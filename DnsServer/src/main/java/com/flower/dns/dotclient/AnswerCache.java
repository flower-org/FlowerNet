package com.flower.dns.dotclient;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRecord;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class AnswerCache {
    public static final Long MAXIMUM_CACHE_SIZE = 10_000L;
    public static final Long CACHE_ENTRY_TIMEOUT_AFTER_ACCESS_MS = 1000L*60L*10L; // 10 minutes

    protected final Cache<DnsQuestionWrapper, DefaultDnsResponse> cache;

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
        cache = CacheBuilder.newBuilder()
                .maximumSize(MAXIMUM_CACHE_SIZE)
                .expireAfterAccess(CACHE_ENTRY_TIMEOUT_AFTER_ACCESS_MS, TimeUnit.MILLISECONDS)
                .removalListener(
                        (RemovalListener<DnsQuestionWrapper, DefaultDnsResponse>) notification ->
                                notification.getValue().release())
                .build();
    }

    public void putResponse(DnsQuestion question, DefaultDnsResponse response) {
        response.retain();
        cache.put(new DnsQuestionWrapper(question), response);
    }

    @Nullable
    public DefaultDnsResponse getResponseIfPresent(DnsQuestion question) {
        return cache.getIfPresent(new DnsQuestionWrapper(question));
    }
}
