package com.flower.dns;

import com.flower.net.dns.dotclient.DnsOverTlsClient;
import com.flower.crypt.PkiUtil;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.dns.DefaultDnsQuery;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DnsOpCode;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.util.NetUtil;

import javax.net.ssl.TrustManagerFactory;
import java.util.Random;
import java.util.concurrent.locks.LockSupport;

public final class DnsOverTlsViaSocksClientTest {
    private static final String SOCKS_SERVER_ADDRESS = "127.0.0.1";
    private static final int SOCKS_SERVER_PORT = 1081;
    private static final String DNS_SERVER_ADDRESS = "1.1.1.1";
    private static final int DNS_SERVER_PORT = 853;

    private static final TrustManagerFactory TRUST_MANAGER = PkiUtil.getTrustManagerForCertificateResource("oneone_cert.pem");

    private static final String QUERY_DOMAIN = "www.example.com";

    public static void main(String[] args) throws Exception {
        DnsOverTlsClient client = new DnsOverTlsClient(SOCKS_SERVER_ADDRESS, SOCKS_SERVER_PORT,
                DNS_SERVER_ADDRESS, DNS_SERVER_PORT, TRUST_MANAGER, false, null);

        int randomID = new Random().nextInt(60000 - 1000) + 1000;
        DnsQuery query = new DefaultDnsQuery(randomID, DnsOpCode.QUERY)
                .setRecord(DnsSection.QUESTION, new DefaultDnsQuestion(QUERY_DOMAIN, DnsRecordType.A));

        client.query(query,
            msg -> {
                System.out.println("=========================");
                System.out.println("RESPONSE RECEIVED:");
                if (msg.count(DnsSection.QUESTION) > 0) {
                    DnsQuestion question = msg.recordAt(DnsSection.QUESTION, 0);
                    System.out.printf("name: %s%n", question.name());
                }
                for (int i = 0, count = msg.count(DnsSection.ANSWER); i < count; i++) {
                    DnsRecord record = msg.recordAt(DnsSection.ANSWER, i);
                    if (record.type() == DnsRecordType.A) {
                        //just print the IP after query
                        DnsRawRecord raw = (DnsRawRecord) record;
                        System.out.println(NetUtil.bytesToIpAddress(ByteBufUtil.getBytes(raw.content())));
                    } else {
                        System.out.println(record.type());
                    }
                }
                System.out.println("=========================");
            }
        );

        LockSupport.parkNanos(10_000_000_000L);
        client.shutdown();
    }
}
