package com.flower.dns;

import com.flower.utils.ServerUtil;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.dns.DefaultDnsQuery;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DnsOpCode;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.util.NetUtil;

import javax.net.ssl.TrustManagerFactory;
import java.net.InetAddress;
import java.util.Random;
import java.util.concurrent.locks.LockSupport;

public final class DnsOverTlsClientTest {
    private static final InetAddress DNS_SERVER_ADDRESS = ServerUtil.getByName("1.1.1.1");
    private static final int DNS_SERVER_PORT = 853;
//    private static final TrustManagerFactory TRUST_MANAGER = ServerUtil.getTrustedCertificates();
//    private static final TrustManagerFactory TRUST_MANAGER = ServerUtil.getUntrustingManager();
    private static final TrustManagerFactory TRUST_MANAGER = ServerUtil.getFromCertificateResource("oneone_cert.pem");

    private static final String QUERY_DOMAIN = "www.example.com";

    public static void main(String[] args) throws Exception {
        DnsOverTlsClient client = new DnsOverTlsClient(DNS_SERVER_ADDRESS, DNS_SERVER_PORT, TRUST_MANAGER);

        int randomID = new Random().nextInt(60000 - 1000) + 1000;
        DnsQuery query = new DefaultDnsQuery(randomID, DnsOpCode.QUERY)
                .setRecord(DnsSection.QUESTION, new DefaultDnsQuestion(QUERY_DOMAIN, DnsRecordType.A));

        client.query(query,
            msg -> {
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
                    }
                }
            }
        );

        LockSupport.parkNanos(1_000_000_000L);
        client.shutdown();
    }
}
