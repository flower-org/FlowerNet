package com.flower.net.dns.dotclient;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResolvedHostsTest {
    private static InetAddress ip(String address) throws Exception {
        return InetAddress.getByName(address);
    }

    @Test
    void looksUpRecordedHost() throws Exception {
        ResolvedHosts hosts = new ResolvedHosts();
        hosts.record("Example.ORG.", List.of(ip("1.2.3.4"), ip("2001:db8::1")));
        assertEquals(List.of("example.org"), hosts.hostsFor(ip("1.2.3.4")));
        assertEquals(List.of("example.org"), hosts.hostsFor(ip("2001:db8::1")));
        assertEquals(List.of(), hosts.hostsFor(ip("5.6.7.8")));
        assertEquals(2, hosts.size());
    }

    @Test
    void sharedIpReturnsAllHostsMostRecentFirst() throws Exception {
        ResolvedHosts hosts = new ResolvedHosts();
        hosts.record("a.com", List.of(ip("1.1.1.1")));
        hosts.record("b.com", List.of(ip("1.1.1.1")));
        assertEquals(List.of("b.com", "a.com"), hosts.hostsFor(ip("1.1.1.1")));
        hosts.record("a.com", List.of(ip("1.1.1.1")));
        assertEquals(List.of("a.com", "b.com"), hosts.hostsFor(ip("1.1.1.1")));
    }

    @Test
    void evictsLeastRecentlyUsed() throws Exception {
        ResolvedHosts hosts = new ResolvedHosts(2);
        hosts.record("a.com", List.of(ip("1.0.0.1")));
        hosts.record("b.com", List.of(ip("1.0.0.2")));
        hosts.record("c.com", List.of(ip("1.0.0.3")));
        assertEquals(List.of(), hosts.hostsFor(ip("1.0.0.1")));
        assertEquals(2, hosts.size());
    }

    @Test
    void lookupBumpProtectsFromEviction() throws Exception {
        ResolvedHosts hosts = new ResolvedHosts(2);
        hosts.record("a.com", List.of(ip("1.0.0.1")));
        hosts.record("b.com", List.of(ip("1.0.0.2")));
        hosts.hostsFor(ip("1.0.0.1"));
        hosts.record("c.com", List.of(ip("1.0.0.3")));
        assertEquals(List.of("a.com"), hosts.hostsFor(ip("1.0.0.1")));
        assertEquals(List.of(), hosts.hostsFor(ip("1.0.0.2")));
    }

    @Test
    void resolveBumpProtectsFromEviction() throws Exception {
        ResolvedHosts hosts = new ResolvedHosts(2);
        hosts.record("a.com", List.of(ip("1.0.0.1")));
        hosts.record("b.com", List.of(ip("1.0.0.2")));
        hosts.record("a.com", List.of(ip("1.0.0.1")));
        hosts.record("c.com", List.of(ip("1.0.0.3")));
        assertEquals(List.of("a.com"), hosts.hostsFor(ip("1.0.0.1")));
        assertEquals(List.of(), hosts.hostsFor(ip("1.0.0.2")));
    }

    @Test
    void recordsAddressesFromSuccessfulResponsesOnly() throws Exception {
        ResolvedHosts hosts = new ResolvedHosts();
        DefaultDnsResponse ok = new DefaultDnsResponse(1);
        ok.addRecord(DnsSection.ANSWER, new DefaultDnsRawRecord("cdn.example.net.", DnsRecordType.A, 60,
                Unpooled.wrappedBuffer(new byte[] {9, 9, 9, 9})));
        ok.addRecord(DnsSection.ANSWER, new DefaultDnsRawRecord("cdn.example.net.", DnsRecordType.TXT, 60,
                Unpooled.wrappedBuffer(new byte[] {1, 2, 3, 4})));
        hosts.record("www.example.com.", ok);
        ok.release();
        assertEquals(List.of("www.example.com"), hosts.hostsFor(ip("9.9.9.9")));
        assertEquals(1, hosts.size());

        DefaultDnsResponse failed = new DefaultDnsResponse(2);
        failed.setCode(DnsResponseCode.SERVFAIL);
        failed.addRecord(DnsSection.ANSWER, new DefaultDnsRawRecord("x.com.", DnsRecordType.A, 60,
                Unpooled.wrappedBuffer(new byte[] {8, 8, 8, 8})));
        hosts.record("x.com.", failed);
        failed.release();
        assertEquals(List.of(), hosts.hostsFor(ip("8.8.8.8")));
    }
}
