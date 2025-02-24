package com.flower.dns.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flower.utils.IpAddressUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsOpCode;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsSection;
import org.apache.commons.lang3.tuple.Pair;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

public class DnsUtils {
    public static DnsResponse dnsResponseFromAddresses(String hostname, Collection<InetAddress> addresses) {
        return dnsResponseFromAddresses(hostname, addresses.toArray(new InetAddress[] {}));
    }

    public static DnsResponse dnsResponseFromAddresses(String hostname, InetAddress... addresses) {
        int randomID = new Random().nextInt(60000 - 1000) + 1000;
        return dnsResponseFromAddresses(randomID, hostname, addresses);
    }

    public static DnsResponse dnsResponseFromAddresses(int id, String hostname, InetAddress... addresses) {
        int timeToLive = 3600;//1 hour
        DefaultDnsResponse response = new DefaultDnsResponse(id, DnsOpCode.QUERY);

        for (InetAddress address: addresses) {
            if (address instanceof Inet4Address) {
                response.addRecord(DnsSection.ANSWER, new DefaultDnsRawRecord(hostname, DnsRecordType.A,
                        timeToLive, Unpooled.wrappedBuffer(address.getAddress())));
            } else if (address instanceof Inet6Address) {
                response.addRecord(DnsSection.ANSWER, new DefaultDnsRawRecord(hostname, DnsRecordType.AAAA,
                        timeToLive, Unpooled.wrappedBuffer(address.getAddress())));
            } else {
                throw new RuntimeException("Unknown InetAddress type " + address.getClass());
            }
        }
        return response;
    }

    public static boolean dnsResponseHasIp(DnsResponse dnsResponse) {
        for (int i = 0, count = dnsResponse.count(DnsSection.ANSWER); i < count; i++) {
            DnsRecord record = dnsResponse.recordAt(DnsSection.ANSWER, i);
            if (DnsRecordType.A.equals(record.type())  || DnsRecordType.AAAA.equals(record.type())) {
                return true;
            }
        }
        return false;
    }

    public static Pair<String, List<InetAddress>> extractIpAddresses(String dnsOverHttpsJsonResponse) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(dnsOverHttpsJsonResponse);
        List<InetAddress> hostnames = new ArrayList<>();

        if (rootNode.has("Answer")) {
            for (JsonNode answer : rootNode.get("Answer")) {
                if (answer.has("type")) {
                    int typeCode = answer.get("type").asInt();
                    if (typeCode == 1 || typeCode == 28) {//A or AAAA
                        if (answer.has("data")) {
                            String ipAddress = answer.get("data").asText();
                            InetAddress address = IpAddressUtil.fromString(ipAddress);
                            if (address != null) {
                                hostnames.add(address);
                            }
                        }
                    }
                }
            }
        }
        return Pair.of("", hostnames);
    }
}
