package com.flower.net.dns;

import com.flower.net.dns.dotclient.DnsOverTlsClient;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsOpCode;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.util.NetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/** Validates queries, resolves them through the upstream client and replies; transports define how replies are sent. */
public abstract class AbstractDnsQueryHandler<Q extends DnsQuery> extends SimpleChannelInboundHandler<Q> {
    final static Logger LOGGER = LoggerFactory.getLogger(AbstractDnsQueryHandler.class);

    public final DnsOverTlsClient client;

    protected AbstractDnsQueryHandler(DnsOverTlsClient client) {
        this.client = client;
    }

    /**
     * Returns what sends a response to this query's client. It's called after {@code query} has been released,
     * so it must capture what it needs now. It doesn't own the response it's given.
     */
    protected abstract Consumer<DefaultDnsResponse> replier(ChannelHandlerContext ctx, Q query);

    /** Returns the error to reply with if the query can't be forwarded, or null if it's valid. */
    @Nullable
    static DnsResponseCode validate(DnsQuery query) {
        if (!DnsOpCode.QUERY.equals(query.opCode())) {
            return DnsResponseCode.NOTIMP;
        }
        // Multiple questions are undefined in practice (RFC 9619), and no upstream supports them
        if (query.count(DnsSection.QUESTION) != 1) {
            return DnsResponseCode.FORMERR;
        }
        return null;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Q query) {
        Consumer<DefaultDnsResponse> replier = replier(ctx, query);
        int id = query.id();
        Consumer<DefaultDnsResponse> owningReplier = response -> {
            // We own the response; the replier encodes it into its own buffer
            try {
                logResponse(response);
                replier.accept(response);
            } catch (RuntimeException e) {
                LOGGER.error("{} | Failed to send the response", id, e);
            } finally {
                response.release();
            }
        };

        DnsResponseCode error = validate(query);
        if (error != null) {
            LOGGER.info("{} | rejected with {}: opcode {}, question count {}", id, error, query.opCode(),
                    query.count(DnsSection.QUESTION));
            DefaultDnsResponse reply = errorReply(id, query.opCode(), query.isRecursionDesired(), error);
            for (int i = 0, count = query.count(DnsSection.QUESTION); i < count; i++) {
                DnsQuestion question = query.recordAt(DnsSection.QUESTION, i);
                reply.addRecord(DnsSection.QUESTION, question);
            }
            owningReplier.accept(reply);
            return;
        }

        DnsQuestion qQuestion = query.recordAt(DnsSection.QUESTION, 0);
        LOGGER.debug("{} | request: {}", id, qQuestion.name());
        client.query(query, owningReplier);
    }

    /** An error reply with no records; the header echoes the query's ID, opcode and RD (RFC 1035 4.1.1). */
    static DefaultDnsResponse errorReply(int id, DnsOpCode opCode, boolean recursionDesired, DnsResponseCode code) {
        DefaultDnsResponse reply = new DefaultDnsResponse(id, opCode, code);
        reply.setRecursionDesired(recursionDesired);
        reply.setRecursionAvailable(true);
        return reply;
    }

    public static void logResponse(DefaultDnsResponse response) {
        int questionCount = response.count(DnsSection.QUESTION);
        if (questionCount > 0) {
            DnsQuestion question = response.recordAt(DnsSection.QUESTION, 0);
            LOGGER.debug("{} | response to: {} | {} | q: {} ans: {} auth: {} add: {}",
                    response.id(), question.name(), response.code(), questionCount,
                    response.count(DnsSection.ANSWER), response.count(DnsSection.AUTHORITY),
                    response.count(DnsSection.ADDITIONAL));
        }

        for (int i = 0, count = response.count(DnsSection.ANSWER); i < count; i++) {
            DnsRecord record = response.recordAt(DnsSection.ANSWER, i);
            if (DnsRecordType.A.equals(record.type()) && record instanceof DnsRawRecord) {
                LOGGER.debug("{} | {}", response.id(),
                        NetUtil.bytesToIpAddress(ByteBufUtil.getBytes(((DnsRawRecord) record).content())));
            }
        }
    }
}
