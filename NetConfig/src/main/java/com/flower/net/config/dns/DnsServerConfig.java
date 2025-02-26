package com.flower.net.config.dns;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
@JsonSerialize(as = ImmutableDnsServerConfig.class)
@JsonDeserialize(as = ImmutableDnsServerConfig.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface DnsServerConfig {
    DnsType dnsType();

    @Nullable String host();
    @Nullable Integer port();
    @Nullable String certificate();
    @Nullable String httpPath();

    /** Resolves via local os methods, no special config */
    static DnsServerConfig localOs() {
        return ImmutableDnsServerConfig.builder()
                .dnsType(DnsType.LOCAL_OS)
                .build();
    }

    /** Uses UDP DNS client with server defined in local resolution conf */
    static DnsServerConfig localNameserver() {
        return ImmutableDnsServerConfig.builder()
                .dnsType(DnsType.LOCAL_NAMESERVER)
                .build();
    }

    /** Uses UDP DNS client with specified server */
    static DnsServerConfig dnsUdp(String host, Integer port) {
        return ImmutableDnsServerConfig.builder()
                .dnsType(DnsType.DNS_UDP)
                .host(host)
                .port(port)
                .build();
    }

    /** Uses TLS DNS client with specified server */
    static DnsServerConfig dnsTls(String host, Integer port) {
        return ImmutableDnsServerConfig.builder()
                .dnsType(DnsType.DNS_TLS)
                .host(host)
                .port(port)
                .build();
    }

    /** Uses TLS DNS client with specified server */
    static DnsServerConfig dnsTls(String host, Integer port, String certificate) {
        return ImmutableDnsServerConfig.builder()
                .dnsType(DnsType.DNS_TLS)
                .host(host)
                .port(port)
                .certificate(certificate)
                .build();
    }

    /** Uses HTTPS DNS client with specified server */
    static DnsServerConfig dnsHttps1(String host, Integer port, String httpPath) {
        return ImmutableDnsServerConfig.builder()
                .dnsType(DnsType.DNS_HTTPS_1)
                .host(host)
                .port(port)
                .httpPath(httpPath)
                .build();
    }

    /** Uses HTTPS DNS client with specified server */
    static DnsServerConfig dnsHttps1(String host, Integer port, String httpPath, String certificate) {
        return ImmutableDnsServerConfig.builder()
                .dnsType(DnsType.DNS_HTTPS_1)
                .host(host)
                .port(port)
                .httpPath(httpPath)
                .certificate(certificate)
                .build();
    }

    /** Uses HTTPS DNS client with specified server */
    static DnsServerConfig dnsHttps2(String host, Integer port, String httpPath) {
        return ImmutableDnsServerConfig.builder()
                .dnsType(DnsType.DNS_HTTPS_2)
                .host(host)
                .port(port)
                .httpPath(httpPath)
                .build();
    }

    /** Uses HTTPS DNS client with specified server */
    static DnsServerConfig dnsHttps2(String host, Integer port, String httpPath, String certificate) {
        return ImmutableDnsServerConfig.builder()
                .dnsType(DnsType.DNS_HTTPS_2)
                .host(host)
                .port(port)
                .httpPath(httpPath)
                .certificate(certificate)
                .build();
    }
}
