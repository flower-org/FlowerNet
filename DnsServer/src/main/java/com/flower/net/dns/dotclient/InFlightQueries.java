package com.flower.net.dns.dotclient;

import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsSection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/**
 * Coalesces identical queries: while one is being resolved upstream, others asking the same thing wait for its
 * answer instead of being sent too. Each waiter gets its own copy, with its own ID and question (as asked).
 */
public class InFlightQueries {
    static final Logger LOGGER = LoggerFactory.getLogger(InFlightQueries.class);

    /** What makes two queries interchangeable: the cache key, plus RD, which changes how upstream answers. */
    record FlightKey(AnswerCache.Key key, boolean recursionDesired) {
    }

    /** A query waiting for another's answer. */
    record Follower(int id, DnsQuestion question, boolean wantsAuthenticData, Consumer<DefaultDnsResponse> consumer) {
    }

    static final class Flight {
        final List<Follower> followers = new ArrayList<>();
    }

    private final ConcurrentMap<FlightKey, Flight> flights = new ConcurrentHashMap<>();

    /**
     * If an identical query is in flight, registers {@code consumer} to get a copy of its answer and returns null.
     * Otherwise returns a consumer to pass the leader's answer to: it answers all followers, then {@code consumer}.
     * The caller keeps ownership of {@code query}; consumers own the responses they get.
     */
    @Nullable
    public Consumer<DefaultDnsResponse> join(DnsQuery query, Consumer<DefaultDnsResponse> consumer) {
        FlightKey key = new FlightKey(AnswerCache.Key.of(query), query.isRecursionDesired());
        Flight leader = new Flight();
        // Followers are added and the flight removed under the map's lock for this key, so none is missed
        Flight flight = flights.compute(key, (k, existing) -> {
            if (existing == null) {
                return leader;
            }
            DnsQuestion question = query.recordAt(DnsSection.QUESTION, 0);
            existing.followers.add(new Follower(query.id(),
                    new DefaultDnsQuestion(question.name(), question.type(), question.dnsClass()),
                    (query.z() & AnswerCache.Z_AUTHENTIC_DATA) != 0 || AnswerCache.isDnssecOk(query), consumer));
            return existing;
        });
        if (flight != leader) {
            LOGGER.info("{} | Waiting for an identical query in flight", query.id());
            return null;
        }
        return response -> {
            flights.remove(key, leader);
            try {
                for (Follower follower : leader.followers) {
                    try {
                        follower.consumer().accept(copyFor(response, follower));
                    } catch (RuntimeException e) {
                        LOGGER.error("{} | Failed to answer a coalesced query", follower.id(), e);
                    }
                }
            } finally {
                consumer.accept(response);
            }
        };
    }

    /** Number of distinct questions in flight. */
    public int size() {
        return flights.size();
    }

    static DefaultDnsResponse copyFor(DnsResponse response, Follower follower) {
        DefaultDnsResponse copy = new DefaultDnsResponse(follower.id(), response.opCode(), response.code());
        DnsMessages.copyHeaderFlags(response, copy);
        // RFC 6840 5.8: set AD only for clients that asked for it with AD or DO
        if (!follower.wantsAuthenticData()) {
            copy.setZ(copy.z() & ~AnswerCache.Z_AUTHENTIC_DATA);
        }
        // Echo the question as this client asked it: resolvers using 0x20 case randomization check it
        copy.addRecord(DnsSection.QUESTION, follower.question());
        for (DnsSection section : new DnsSection[] {DnsSection.ANSWER, DnsSection.AUTHORITY, DnsSection.ADDITIONAL}) {
            for (int i = 0, count = response.count(section); i < count; i++) {
                DnsRecord record = response.recordAt(section, i);
                copy.addRecord(section, DnsMessages.retainedDuplicate(record));
            }
        }
        return copy;
    }
}
