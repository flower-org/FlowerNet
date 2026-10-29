package com.flower.net.dns.dotclient;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.collect.ImmutableList;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsOpCode;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Caches responses as immutable snapshots that hold no reference-counted buffers, so entries can be evicted at any
 * time without affecting responses handed out earlier. Every hit returns a new response owned by the caller.
 */
public class AnswerCache {
    public static final Long MAXIMUM_CACHE_SIZE = 10_000L;
    public static final Long CACHE_ENTRY_TIMEOUT_AFTER_ACCESS_MS = 1000L*60L*10L; // 10 minutes

    protected final Cache<DnsQuestionWrapper, CachedResponse> cache;

    /** A record without its buffer: raw records keep a private copy of their content. */
    protected static final class CachedRecord {
        final DnsRecord record;
        @Nullable final byte[] content;

        CachedRecord(DnsRecord record) {
            if (record instanceof DnsRawRecord) {
                DnsRawRecord raw = (DnsRawRecord) record;
                this.record = new DefaultDnsRawRecord(raw.name(), raw.type(), raw.dnsClass(), raw.timeToLive(),
                        Unpooled.EMPTY_BUFFER);
                this.content = ByteBufUtil.getBytes(raw.content());
            } else {
                this.record = record;
                this.content = null;
            }
        }

        DnsRecord toRecord() {
            if (content == null) {
                return record;
            }
            // Wrapped heap buffers aren't pooled, so each hit owns an independent reference count
            return new DefaultDnsRawRecord(record.name(), record.type(), record.dnsClass(), record.timeToLive(),
                    Unpooled.wrappedBuffer(content));
        }
    }

    protected static final class CachedResponse {
        final DnsOpCode opCode;
        final DnsResponseCode code;
        final boolean authoritativeAnswer;
        final boolean truncated;
        final boolean recursionDesired;
        final boolean recursionAvailable;
        final int z;
        final ImmutableList<ImmutableList<CachedRecord>> sections;

        CachedResponse(DnsResponse response) {
            this.opCode = response.opCode();
            this.code = response.code();
            this.authoritativeAnswer = response.isAuthoritativeAnswer();
            this.truncated = response.isTruncated();
            this.recursionDesired = response.isRecursionDesired();
            this.recursionAvailable = response.isRecursionAvailable();
            this.z = response.z();
            ImmutableList.Builder<ImmutableList<CachedRecord>> sectionsBuilder = ImmutableList.builder();
            for (DnsSection section : DnsSection.values()) {
                ImmutableList.Builder<CachedRecord> records = ImmutableList.builder();
                for (int i = 0, count = response.count(section); i < count; i++) {
                    records.add(new CachedRecord(response.recordAt(section, i)));
                }
                sectionsBuilder.add(records.build());
            }
            this.sections = sectionsBuilder.build();
        }

        DefaultDnsResponse toResponse(int id) {
            DefaultDnsResponse response = new DefaultDnsResponse(id, opCode, code);
            response.setAuthoritativeAnswer(authoritativeAnswer)
                    .setTruncated(truncated)
                    .setRecursionDesired(recursionDesired)
                    .setRecursionAvailable(recursionAvailable)
                    .setZ(z);
            for (DnsSection section : DnsSection.values()) {
                for (CachedRecord record : sections.get(section.ordinal())) {
                    response.addRecord(section, record.toRecord());
                }
            }
            return response;
        }
    }

    protected static class DnsQuestionWrapper {
        final DnsRecord question;

        DnsQuestionWrapper(DnsRecord question) {
            this.question = question;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DnsQuestionWrapper that = (DnsQuestionWrapper) o;
            return question.dnsClass() == that.question.dnsClass()
                    && Objects.equals(question.name(), that.question.name())
                    && Objects.equals(question.type(), that.question.type());
        }

        @Override
        public int hashCode() {
            return Objects.hash(question.name(), question.type(), question.dnsClass());
        }
    }

    public AnswerCache() {
        cache = Caffeine.newBuilder()
                .maximumSize(MAXIMUM_CACHE_SIZE)
                .expireAfterAccess(CACHE_ENTRY_TIMEOUT_AFTER_ACCESS_MS, TimeUnit.MILLISECONDS)
                .build();
    }

    /** Stores a snapshot of the response if it has answers. Doesn't retain or release {@code response}. */
    public void putResponse(DnsQuestion question, DnsResponse response) {
        if (response.count(DnsSection.ANSWER) > 0) {
            cache.put(new DnsQuestionWrapper(question), new CachedResponse(response));
        }
    }

    /** Returns a new response with the given ID, owned by the caller (who must release it), or null on a miss. */
    @Nullable
    public DefaultDnsResponse getResponseIfPresent(DnsQuestion question, int id) {
        CachedResponse cached = cache.getIfPresent(new DnsQuestionWrapper(question));
        return cached == null ? null : cached.toResponse(id);
    }
}
