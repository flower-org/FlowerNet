package com.flower.net.dns.dotclient;

import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.security.SecureRandom;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Tracks queries in flight to the upstream server.
 * Every upstream query gets its own randomly allocated ID, unique among in-flight queries, so that clients
 * that happen to use the same query ID can't receive each other's answers.
 * A response is only matched if its question also matches the question of the original query.
 * Every registered query ends exactly once: completed by a response, cancelled, or expired after the deadline,
 * in which case the expiry handler is called so the client can still be answered.
 */
public class UpstreamQueryRegistry {
    static final Logger LOGGER = LoggerFactory.getLogger(UpstreamQueryRegistry.class);

    public static final int ID_SPACE = 1 << 16;

    public static final class PendingQuery {
        private final int upstreamId;
        private final int originalId;
        private final DnsQuestion question;
        private final Consumer<DefaultDnsResponse> callback;
        @Nullable private volatile ScheduledFuture<?> timeoutFuture;
        @Nullable private volatile ScheduledFuture<?> retryFuture;
        /** Number of the current send attempt, starting at 0. */
        private final AtomicInteger attempt = new AtomicInteger();
        /** The query as sent upstream; owned by this entry and released when it finishes. */
        @Nullable private DnsQuery upstreamQuery;
        private boolean finished;

        PendingQuery(int upstreamId, int originalId, DnsQuestion question, Consumer<DefaultDnsResponse> callback) {
            this.upstreamId = upstreamId;
            this.originalId = originalId;
            this.question = question;
            this.callback = callback;
        }

        public int upstreamId() { return upstreamId; }
        public int originalId() { return originalId; }
        public DnsQuestion question() { return question; }
        public Consumer<DefaultDnsResponse> callback() { return callback; }

        public int attempt() { return attempt.get(); }

        /** Moves to the next attempt if {@code fromAttempt} is still the current one; false if someone else did. */
        public boolean advanceAttempt(int fromAttempt) {
            return attempt.compareAndSet(fromAttempt, fromAttempt + 1);
        }

        /** Hands the upstream query to this entry, which releases it when finished. */
        public synchronized void attachUpstreamQuery(DnsQuery query) {
            if (finished) {
                query.release();
            } else {
                upstreamQuery = query;
            }
        }

        /** Retains the upstream query for sending, or returns null if the query already finished. */
        @Nullable
        public synchronized DnsQuery retainUpstreamQuery() {
            DnsQuery query = upstreamQuery;
            return finished || query == null ? null : (DnsQuery) query.retain();
        }

        synchronized void finish() {
            finished = true;
            cancel(timeoutFuture);
            cancel(retryFuture);
            DnsQuery query = upstreamQuery;
            upstreamQuery = null;
            if (query != null) {
                query.release();
            }
        }

        private static void cancel(@Nullable ScheduledFuture<?> future) {
            if (future != null) {
                future.cancel(false);
            }
        }
    }

    private final ConcurrentHashMap<Integer, PendingQuery> pending = new ConcurrentHashMap<>();
    private final Random random = new SecureRandom();
    private final EventExecutorGroup scheduler;
    private final long timeoutMillis;
    private final Consumer<PendingQuery> onExpired;

    public UpstreamQueryRegistry(EventExecutorGroup scheduler, long timeoutMillis) {
        this(scheduler, timeoutMillis, entry -> { });
    }

    /** @param onExpired called with queries that got no response within {@code timeoutMillis} */
    public UpstreamQueryRegistry(EventExecutorGroup scheduler, long timeoutMillis, Consumer<PendingQuery> onExpired) {
        this.scheduler = scheduler;
        this.timeoutMillis = timeoutMillis;
        this.onExpired = onExpired;
    }

    /**
     * Registers a query and allocates a unique upstream ID for it.
     * The registration expires if no matching response arrives within the timeout.
     * @throws IllegalStateException if all 65536 IDs are in use
     */
    public PendingQuery register(int originalId, DnsQuestion question, Consumer<DefaultDnsResponse> callback) {
        while (true) {
            if (pending.size() >= ID_SPACE) {
                throw new IllegalStateException("No free upstream query IDs: " + pending.size() + " queries in flight");
            }
            int upstreamId = random.nextInt(ID_SPACE);
            PendingQuery entry = new PendingQuery(upstreamId, originalId, question, callback);
            if (pending.putIfAbsent(upstreamId, entry) == null) {
                entry.timeoutFuture = scheduler.schedule(() -> {
                    if (pending.remove(upstreamId, entry)) {
                        LOGGER.info("{} | upstream query {} for {} timed out", originalId, upstreamId, question.name());
                        entry.finish();
                        onExpired.accept(entry);
                    }
                }, timeoutMillis, TimeUnit.MILLISECONDS);
                return entry;
            }
        }
    }

    /**
     * Finds and removes the query that the response answers.
     * @return the matching query, or null if the ID is unknown or the question doesn't match
     */
    @Nullable
    public PendingQuery complete(DnsResponse response) {
        PendingQuery entry = pending.get(response.id());
        if (entry == null) {
            LOGGER.info("Upstream response with unknown or expired ID {}", response.id());
            return null;
        }

        DnsQuestion responseQuestion = response.count(DnsSection.QUESTION) == 1
                ? response.recordAt(DnsSection.QUESTION, 0) : null;
        if (responseQuestion == null || !sameQuestion(entry.question, responseQuestion)) {
            LOGGER.warn("{} | upstream response {} question mismatch: expected {} {} {}, got {}",
                    entry.originalId, entry.upstreamId,
                    entry.question.name(), entry.question.type(), entry.question.dnsClass(),
                    responseQuestion == null
                        ? response.count(DnsSection.QUESTION) + " questions"
                        : responseQuestion.name() + " " + responseQuestion.type() + " " + responseQuestion.dnsClass());
            return null;
        }

        if (!pending.remove(response.id(), entry)) {
            // Completed or timed out concurrently
            return null;
        }
        entry.finish();
        return entry;
    }

    /**
     * Drops a query that won't be answered, e.g. because it ran out of retries.
     * @return true if the query was still pending, i.e. the caller is responsible for answering the client
     */
    public boolean cancel(PendingQuery entry) {
        if (pending.remove(entry.upstreamId, entry)) {
            entry.finish();
            return true;
        }
        return false;
    }

    /** Runs {@code action} after the delay if the query is still pending then; replaces any earlier scheduled retry. */
    public void scheduleRetry(PendingQuery entry, long delayMillis, Runnable action) {
        PendingQuery.cancel(entry.retryFuture);
        entry.retryFuture = scheduler.schedule(() -> {
            if (pending.get(entry.upstreamId) == entry) {
                action.run();
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    public int inFlightCount() {
        return pending.size();
    }

    /** DNS names are case-insensitive (RFC 4343). */
    public static boolean sameQuestion(DnsQuestion a, DnsQuestion b) {
        return a.name().equalsIgnoreCase(b.name())
                && a.type().equals(b.type())
                && a.dnsClass() == b.dnsClass();
    }
}
