package com.flower.dns;

import com.flower.dns.dotclient.DnsOverTlsClient;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.dns.DatagramDnsQuery;
import io.netty.handler.codec.dns.DatagramDnsResponse;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.util.NetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DnsMessageHandler extends SimpleChannelInboundHandler<DatagramDnsQuery> {
    final static Logger LOGGER = LoggerFactory.getLogger(DnsMessageHandler.class);

    public final DnsOverTlsClient client;

    public DnsMessageHandler(DnsOverTlsClient client) {
        this.client = client;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramDnsQuery query) {
        DnsQuestion qQuestion = query.recordAt(DnsSection.QUESTION, 0);
        LOGGER.info("{} | request: {} / question count {}", query.id(), qQuestion.name(), query.count(DnsSection.QUESTION));
        query.retain();

        client.query(query,
            tcpResponse -> {
                logResponse(tcpResponse);

                DatagramDnsResponse udpResponse = convertToUdpResponse(query, tcpResponse);
                ctx.writeAndFlush(udpResponse);
            }
        );
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("DnsMessageHandler.exceptionCaught", cause);
    }

    protected DatagramDnsResponse convertToUdpResponse(DatagramDnsQuery query, DefaultDnsResponse tcpResponse) {
        DatagramDnsResponse udpResponse = new DatagramDnsResponse(query.recipient(), query.sender(), query.id());
        udpResponse.retain();
        udpResponse.setCode(tcpResponse.code());
        for (DnsSection section : DnsSection.values()) {
            int count = tcpResponse.count(section);
            for (int i = 0; i < count; i++) {
                DnsRecord record = tcpResponse.recordAt(section, i);
                udpResponse.addRecord(section, record);
            }
        }

        return udpResponse;
    }

    public static void logResponse(DefaultDnsResponse tcpResponse) {
        int questionCount = tcpResponse.count(DnsSection.QUESTION);
        int answerCount = tcpResponse.count(DnsSection.ANSWER);
        int authorityCount = tcpResponse.count(DnsSection.AUTHORITY);
        int additionalCount = tcpResponse.count(DnsSection.ADDITIONAL);
        if (questionCount > 0) {
            DnsQuestion question = tcpResponse.recordAt(DnsSection.QUESTION, 0);
            LOGGER.info("{} | response to: {} | q: {} ans: {} auth: {} add: {}",
                    tcpResponse.id(), question.name(),
                    questionCount, answerCount, authorityCount, additionalCount);
        }

        for (int i = 0, count = tcpResponse.count(DnsSection.ANSWER); i < count; i++) {
            DnsRecord record = tcpResponse.recordAt(DnsSection.ANSWER, i);
            if (record.type() == DnsRecordType.A) {
                //just print the IP after query
                DnsRawRecord raw = (DnsRawRecord) record;
                LOGGER.info("{} | {}", tcpResponse.id(), NetUtil.bytesToIpAddress(ByteBufUtil.getBytes(raw.content())));
            }
        }
    }
}