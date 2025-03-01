package com.flower.net.dns;

import com.flower.net.dns.cache.DnsCache;
import com.flower.net.dns.client.dnsoverhttps1.DnsOverHttps1Client;
import com.flower.net.dns.client.dnsoverhttps2.DnsOverHttps2Client;
import com.flower.net.dns.client.dnsovertls.DnsOverTlsClient;
import com.flower.net.dns.client.dnsoverudp.DnsOverUdpClient;
import com.flower.net.dns.client.os.RawOsResolver;
import com.flower.net.utils.IpAddressUtil;
import com.flower.crypt.PkiUtil;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.util.concurrent.Future;

import javax.net.ssl.TrustManagerFactory;
import java.net.InetAddress;
import java.util.concurrent.locks.LockSupport;

public final class DnsClientTest {
    private static final int DNS_OVER_UDP_SERVER_PORT = 53;
    private static final int DNS_OVER_TLS_SERVER_PORT = 853;
    private static final int DNS_OVER_HTTPS_SERVER_PORT = 443;

//    private static final TrustManagerFactory TRUST_MANAGER = ServerUtil.getTrustedCertificates();
//    private static final TrustManagerFactory TRUST_MANAGER = PkiUtil.getUntrustingManager();
//    private static final TrustManagerFactory TRUST_MANAGER = PkiUtil.getInsecureTrustManagerFactory();

    private static final InetAddress DNS_SERVER_ADDRESS = IpAddressUtil.fromString("1.1.1.1");
    private static final String DNS_SERVER_PATH_PREFIX = "/dns-query?name=";
    private static final TrustManagerFactory TRUST_MANAGER = PkiUtil.getTrustManagerForCertificateResource("oneone_cert.pem");

/*    private static final InetAddress DNS_SERVER_ADDRESS = IpAddressUtil.fromString("8.8.8.8");
    private static final String DNS_SERVER_PATH_PREFIX = "/resolve?name=";
    private static final TrustManagerFactory TRUST_MANAGER = PkiUtil.getTrustManagerForCertificateResource("8888.pem");
*/
    private static final String QUERY_DOMAIN = "www.test.com";

    public static void main(String[] args) throws Exception {
        DnsClient client;
        int i = 9;
        switch (i) {
            case 1: client = new DnsOverTlsClient(DNS_SERVER_ADDRESS, DNS_OVER_TLS_SERVER_PORT, TRUST_MANAGER, null); break;
            case 2: client = new DnsCache(new DnsOverTlsClient(DNS_SERVER_ADDRESS, DNS_OVER_TLS_SERVER_PORT, TRUST_MANAGER, null)); break;
            case 3: client = new DnsOverUdpClient(DNS_SERVER_ADDRESS, DNS_OVER_UDP_SERVER_PORT); break;
            case 4: client = new DnsCache(new DnsOverUdpClient(DNS_SERVER_ADDRESS, DNS_OVER_UDP_SERVER_PORT)); break;
            case 5: client = new RawOsResolver(); break;
            case 6: client = new DnsCache(new RawOsResolver()); break;
            case 7: client = new DnsOverHttps1Client(DNS_SERVER_ADDRESS, DNS_OVER_HTTPS_SERVER_PORT, DNS_SERVER_PATH_PREFIX, TRUST_MANAGER, null); break;
            case 8: client = new DnsCache(new DnsOverHttps1Client(DNS_SERVER_ADDRESS, DNS_OVER_HTTPS_SERVER_PORT, DNS_SERVER_PATH_PREFIX, TRUST_MANAGER, null)); break;
            case 9: client = new DnsOverHttps2Client(DNS_SERVER_ADDRESS, DNS_OVER_HTTPS_SERVER_PORT, DNS_SERVER_PATH_PREFIX, TRUST_MANAGER, null); break;
            case 10: client = new DnsCache(new DnsOverHttps2Client(DNS_SERVER_ADDRESS, DNS_OVER_HTTPS_SERVER_PORT, DNS_SERVER_PATH_PREFIX, TRUST_MANAGER, null)); break;
            default: throw new RuntimeException("Client type unknown: " + i);
        }

        testClient(client);

        LockSupport.parkNanos(1_000_000_000L);
        client.shutdown();
    }

    static void testClient(DnsClient client) {
        Future<DnsResponse> promise = client.query(QUERY_DOMAIN, 1000);
        promise.addListener(future -> {
            if (future.isSuccess()) {
                DefaultDnsResponse msg = (DefaultDnsResponse) future.get();
                TestUtils.printDnsRecord(msg);
            } else {
                future.cause().printStackTrace();
            }
        });
    }
}
