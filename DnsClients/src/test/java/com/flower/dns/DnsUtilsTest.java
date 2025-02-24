package com.flower.dns;

import com.flower.dns.utils.DnsUtils;
import com.flower.utils.IpAddressUtil;
import com.google.common.io.Resources;
import io.netty.handler.codec.dns.DnsResponse;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DnsUtilsTest {
    @Test
    public void test() throws UnknownHostException {
        InetAddress ipv4 = InetAddress.getByName("192.168.1.1");
        InetAddress ipv6 = InetAddress.getByName("2001:db8::1");

        DnsResponse dnsRecord = DnsUtils.dnsResponseFromAddresses("host", ipv4, ipv6);
        TestUtils.printDnsRecord(dnsRecord);
    }

    public InetAddress ad(String s) {
        return IpAddressUtil.fromString(s);
    }

    @Test
    public void test2() throws Exception {
        String dnsOverHttpsJsonResponse =
                Resources.toString(Resources.getResource("DnsOverHttpsResponse.json"), StandardCharsets.UTF_8);
        Pair<String, List<InetAddress>> addresses = DnsUtils.extractIpAddresses(dnsOverHttpsJsonResponse);
        assertEquals(List.of(ad("96.7.128.175"), ad("23.215.0.138"), ad("96.7.128.198"), ad("23.192.228.80"),
                ad("23.192.228.84"), ad("2606:2800:220:1:248:1893:25c8:1946")), addresses.getValue());
    }
}
