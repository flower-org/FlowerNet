package com.flower.net.dns.dotclient;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.collect.ImmutableList;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsMessage;
import io.netty.handler.codec.dns.DnsOpCode;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Caches responses as immutable snapshots that hold no reference-counted buffers, so entries can be evicted at any
 * time without affecting responses handed out earlier. Every hit returns a new response owned by the caller.
 *
 * <p>Entries live as long as their shortest TTL. Negative answers (NXDOMAIN and NODATA) are cached using the SOA
 * record per RFC 2308. Errors, truncated responses and zero TTLs are never cached. Served records carry the TTL
 * they have left.
 */
public class AnswerCache {
    public static final long MAXIMUM_CACHE_SIZE = 10_000L;
    public static final long MAX_TTL_SECONDS = 86_400L;
    /** RFC 2308 section 5 suggests 1 to 3 hours as the upper bound for negative caching. */
    public static final long MAX_NEGATIVE_TTL_SECONDS = 3_600L;

    static final int Z_AUTHENTIC_DATA = 0b010;
    static final int Z_CHECKING_DISABLED = 0b001;
    static final long OPT_FLAG_DNSSEC_OK = 0x8000;

    protected final Cache<Key, CachedResponse> cache;
    private final Ticker ticker;

    /**
     * Identifies answers that can be shared: the question (case-insensitively, per RFC 4343) and the DO and CD bits,
     * which change the answer's content (RRSIGs, unvalidated data).
     */
    public static final class Key {
        final String name;
        final int type;
        final int dnsClass;
        final boolean dnssecOk;
        final boolean checkingDisabled;

        Key(String name, int type, int dnsClass, boolean dnssecOk, boolean checkingDisabled) {
            this.name = asciiLowerCase(name);
            this.type = type;
            this.dnsClass = dnsClass;
            this.dnssecOk = dnssecOk;
            this.checkingDisabled = checkingDisabled;
        }

        /** The query must have a question. */
        public static Key of(DnsQuery query) {
            DnsQuestion question = query.recordAt(DnsSection.QUESTION, 0);
            return new Key(question.name(), question.type().intValue(), question.dnsClass(), isDnssecOk(query),
                    (query.z() & Z_CHECKING_DISABLED) != 0);
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) return true;
            if (!(o instanceof Key)) return false;
            Key that = (Key) o;
            return type == that.type && dnsClass == that.dnsClass && dnssecOk == that.dnssecOk
                    && checkingDisabled == that.checkingDisabled && name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, type, dnsClass, dnssecOk, checkingDisabled);
        }

        @Override
        public String toString() {
            return name + "/" + type + "/" + dnsClass + (dnssecOk ? "/DO" : "") + (checkingDisabled ? "/CD" : "");
        }
    }

    /** DNS names compare case-insensitively in ASCII only (RFC 4343). */
    static String asciiLowerCase(String name) {
        StringBuilder sb = null;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                if (sb == null) {
                    sb = new StringBuilder(name);
                }
                sb.setCharAt(i, (char) (c + ('a' - 'A')));
            }
        }
        return sb == null ? name : sb.toString();
    }

    /** The DO bit lives in the OPT pseudo-record's TTL field (RFC 6891 6.1.3). */
    static boolean isDnssecOk(DnsMessage message) {
        for (int i = 0, count = message.count(DnsSection.ADDITIONAL); i < count; i++) {
            DnsRecord record = message.recordAt(DnsSection.ADDITIONAL, i);
            if (DnsRecordType.OPT.equals(record.type())) {
                return (record.timeToLive() & OPT_FLAG_DNSSEC_OK) != 0;
            }
        }
        return false;
    }

    /** RFC 2181 8: a TTL with the most significant bit set is treated as zero. */
    static long ttlSeconds(DnsRecord record) {
        long ttl = record.timeToLive();
        return ttl < 0 || ttl > Integer.MAX_VALUE ? 0 : ttl;
    }

    /** SOA RDATA ends with SERIAL, REFRESH, RETRY, EXPIRE, MINIMUM: MINIMUM is the last 32 bits, whatever the names. */
    static long soaMinimum(DnsRecord soa) {
        if (!(soa instanceof DnsRawRecord)) {
            return 0;
        }
        ByteBuf content = ((DnsRawRecord) soa).content();
        if (content.readableBytes() < 22) {
            return 0;
        }
        long minimum = content.getUnsignedInt(content.writerIndex() - 4);
        return minimum > Integer.MAX_VALUE ? 0 : minimum;
    }

    /** How long the response may be cached, in seconds; 0 means it must not be cached. */
    static long cacheTtlSeconds(DnsResponse response) {
        if (response.isTruncated()) {
            return 0;
        }
        boolean negative;
        if (DnsResponseCode.NXDOMAIN.equals(response.code())) {
            negative = true;
        } else if (DnsResponseCode.NOERROR.equals(response.code())) {
            negative = response.count(DnsSection.ANSWER) == 0;
        } else {
            // SERVFAIL, REFUSED, FORMERR...: transient or specific to a query
            return 0;
        }

        long ttl = negative ? MAX_NEGATIVE_TTL_SECONDS : MAX_TTL_SECONDS;
        boolean hasSoa = false;
        for (DnsSection section : CACHED_SECTIONS) {
            for (int i = 0, count = response.count(section); i < count; i++) {
                DnsRecord record = response.recordAt(section, i);
                if (DnsRecordType.OPT.equals(record.type())) {
                    continue;
                }
                ttl = Math.min(ttl, ttlSeconds(record));
                if (section == DnsSection.AUTHORITY && DnsRecordType.SOA.equals(record.type())) {
                    // RFC 2308 5: the negative TTL is the minimum of the SOA's own TTL and its MINIMUM field
                    hasSoa = true;
                    ttl = Math.min(ttl, soaMinimum(record));
                }
            }
        }
        // RFC 2308 5: negative responses without an SOA should not be cached
        return negative && !hasSoa ? 0 : ttl;
    }

    static final DnsSection[] CACHED_SECTIONS = {DnsSection.ANSWER, DnsSection.AUTHORITY, DnsSection.ADDITIONAL};

    /** A record without its buffer: raw records keep a private copy of their content. */
    protected static final class CachedRecord {
        final DnsRecord record;
        @Nullable final byte[] content;

        CachedRecord(DnsRecord record) {
            this.record = record;
            this.content = record instanceof DnsRawRecord ? ByteBufUtil.getBytes(((DnsRawRecord) record).content())
                    : null;
        }

        /** Returns a copy whose TTL is reduced by {@code elapsedSeconds}; OPT's TTL field holds flags and is kept. */
        DnsRecord toRecord(long elapsedSeconds) {
            long ttl = DnsRecordType.OPT.equals(record.type()) ? record.timeToLive()
                    : Math.max(0, ttlSeconds(record) - elapsedSeconds);
            if (content == null) {
                // Only raw records are produced by the decoder, so this is unlikely; keep the record as is
                return record;
            }
            // Wrapped heap buffers aren't pooled, so each hit owns an independent reference count
            return new DefaultDnsRawRecord(record.name(), record.type(), record.dnsClass(), ttl,
                    Unpooled.wrappedBuffer(content));
        }
    }

    protected static final class CachedResponse {
        final DnsOpCode opCode;
        final DnsResponseCode code;
        final boolean authoritativeAnswer;
        final boolean recursionDesired;
        final boolean recursionAvailable;
        final int z;
        /** ANSWER, AUTHORITY and ADDITIONAL; the question comes from each query. */
        final ImmutableList<ImmutableList<CachedRecord>> sections;
        final long ttlSeconds;
        final long createdNanos;

        CachedResponse(DnsResponse response, long ttlSeconds, long createdNanos) {
            this.opCode = response.opCode();
            this.code = response.code();
            this.authoritativeAnswer = response.isAuthoritativeAnswer();
            this.recursionDesired = response.isRecursionDesired();
            this.recursionAvailable = response.isRecursionAvailable();
            this.z = response.z();
            this.ttlSeconds = ttlSeconds;
            this.createdNanos = createdNanos;
            ImmutableList.Builder<ImmutableList<CachedRecord>> sectionsBuilder = ImmutableList.builder();
            for (DnsSection section : CACHED_SECTIONS) {
                ImmutableList.Builder<CachedRecord> records = ImmutableList.builder();
                for (int i = 0, count = response.count(section); i < count; i++) {
                    records.add(new CachedRecord(response.recordAt(section, i)));
                }
                sectionsBuilder.add(records.build());
            }
            this.sections = sectionsBuilder.build();
        }

        DefaultDnsResponse toResponse(DnsQuery query, long nowNanos) {
            long elapsedSeconds = Math.max(0, TimeUnit.NANOSECONDS.toSeconds(nowNanos - createdNanos));
            DnsQuestion question = query.recordAt(DnsSection.QUESTION, 0);
            int responseZ = z;
            // RFC 6840 5.8: set AD only for clients that asked for it with AD or DO
            if ((query.z() & Z_AUTHENTIC_DATA) == 0 && !isDnssecOk(query)) {
                responseZ &= ~Z_AUTHENTIC_DATA;
            }
            DefaultDnsResponse response = new DefaultDnsResponse(query.id(), opCode, code);
            response.setAuthoritativeAnswer(authoritativeAnswer)
                    .setRecursionDesired(recursionDesired)
                    .setRecursionAvailable(recursionAvailable)
                    .setZ(responseZ);
            // Echo the client's question as asked: resolvers using 0x20 case randomization check it
            response.addRecord(DnsSection.QUESTION,
                    new DefaultDnsQuestion(question.name(), question.type(), question.dnsClass()));
            for (int s = 0; s < CACHED_SECTIONS.length; s++) {
                for (CachedRecord record : sections.get(s)) {
                    response.addRecord(CACHED_SECTIONS[s], record.toRecord(elapsedSeconds));
                }
            }
            return response;
        }
    }

    public AnswerCache() {
        this(Ticker.systemTicker());
    }

    public AnswerCache(Ticker ticker) {
        this.ticker = ticker;
        cache = Caffeine.newBuilder()
                .ticker(ticker)
                .maximumSize(MAXIMUM_CACHE_SIZE)
                .expireAfter(new Expiry<Key, CachedResponse>() {
                    @Override
                    public long expireAfterCreate(Key key, CachedResponse value, long currentTime) {
                        return TimeUnit.SECONDS.toNanos(value.ttlSeconds);
                    }

                    @Override
                    public long expireAfterUpdate(Key key, CachedResponse value, long currentTime,
                                                  long currentDuration) {
                        return TimeUnit.SECONDS.toNanos(value.ttlSeconds);
                    }

                    @Override
                    public long expireAfterRead(Key key, CachedResponse value, long currentTime,
                                                long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
    }

    /** Stores a snapshot of the response if it's cacheable. Doesn't retain or release {@code response}. */
    public void putResponse(Key key, DnsResponse response) {
        long ttlSeconds = cacheTtlSeconds(response);
        if (ttlSeconds > 0) {
            cache.put(key, new CachedResponse(response, ttlSeconds, ticker.read()));
        }
    }

    /**
     * Returns a response to {@code query} (its ID and question) with the remaining TTLs, or null on a miss.
     * The response is owned by the caller, who must release it.
     */
    @Nullable
    public DefaultDnsResponse getResponseIfPresent(DnsQuery query) {
        CachedResponse cached = cache.getIfPresent(Key.of(query));
        return cached == null ? null : cached.toResponse(query, ticker.read());
    }
}
