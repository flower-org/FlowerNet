package com.flower.dns;

import com.flower.dns.cache.DnsCache;
import com.flower.dns.client.dnsovertls.DnsOverTlsClient;
import com.flower.dns.client.dnsoverudp.DnsOverUdpClient;
import com.flower.utils.DnsClient;
import com.flower.utils.PkiUtil;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.Promise;

import javax.net.ssl.TrustManagerFactory;
import java.util.concurrent.locks.LockSupport;

public final class DnsClientTest {
    private static final String DNS_SERVER_ADDRESS = "1.1.1.1";
    private static final int DNS_OVER_UDP_SERVER_PORT = 53;
    private static final int DNS_OVER_TLS_SERVER_PORT = 853;
//    private static final TrustManagerFactory TRUST_MANAGER = ServerUtil.getTrustedCertificates();
//    private static final TrustManagerFactory TRUST_MANAGER = ServerUtil.getUntrustingManager();
    private static final TrustManagerFactory TRUST_MANAGER = PkiUtil.getTrustManagerForCertificateResource("oneone_cert.pem");

    private static final String QUERY_DOMAIN = "www.google.com";

    public static void main(String[] args) throws Exception {
        DnsClient client;
        int i = 3;
        switch (i) {
            case 1: client = new DnsOverTlsClient(DNS_SERVER_ADDRESS, DNS_OVER_TLS_SERVER_PORT, TRUST_MANAGER); break;
            case 2: client = new DnsOverUdpClient(DNS_SERVER_ADDRESS, DNS_OVER_UDP_SERVER_PORT); break;
            case 3: client = new DnsCache(new DnsOverTlsClient(DNS_SERVER_ADDRESS, DNS_OVER_TLS_SERVER_PORT, TRUST_MANAGER)); break;
            case 4: client = new DnsCache(new DnsOverUdpClient(DNS_SERVER_ADDRESS, DNS_OVER_UDP_SERVER_PORT)); break;
            default: throw new RuntimeException("Client type unknown: " + i);
        }

        testClient(client);

        LockSupport.parkNanos(1_000_000_000L);
        client.shutdown();
    }

    static void testClient(DnsClient client) {
        Promise<DnsResponse> promise = client.query(QUERY_DOMAIN, 1000);
        promise.addListener(future -> {
            if (future.isSuccess()) {
                System.out.println("Success");
                DefaultDnsResponse msg = (DefaultDnsResponse) future.get();
                if (msg.count(DnsSection.QUESTION) > 0) {
                    DnsQuestion question = msg.recordAt(DnsSection.QUESTION, 0);
                    System.out.printf("name: %s%n", question.name());
                }
                for (int i = 0, count = msg.count(DnsSection.ANSWER); i < count; i++) {
                    DnsRecord record = msg.recordAt(DnsSection.ANSWER, i);
                    if (DnsRecordType.A.equals(record.type())) {
                        //just print the IP after query
                        DnsRawRecord raw = (DnsRawRecord) record;
                        System.out.println(NetUtil.bytesToIpAddress(ByteBufUtil.getBytes(raw.content())));
                    }
                }
            } else {
                future.cause().printStackTrace();
            }
        });
    }
}
