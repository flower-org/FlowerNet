package com.flower.net.dns.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flower.net.utils.IpAddressUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsOpCode;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.util.ReferenceCounted;
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

    /**
     * Copies a response into unpooled heap buffers, so the copy stays valid after the original is released,
     * can be shared between threads, and needn't be released.
     */
    public static DefaultDnsResponse unpooledCopy(DnsResponse response) {
        DefaultDnsResponse copy = new DefaultDnsResponse(response.id(), response.opCode(), response.code());
        copy.setAuthoritativeAnswer(response.isAuthoritativeAnswer())
                .setTruncated(response.isTruncated())
                .setRecursionAvailable(response.isRecursionAvailable())
                .setRecursionDesired(response.isRecursionDesired())
                .setZ(response.z());
        for (DnsSection section : DnsSection.values()) {
            for (int i = 0, count = response.count(section); i < count; i++) {
                DnsRecord record = response.recordAt(section, i);
                if (section == DnsSection.QUESTION) {
                    copy.addRecord(section, new DefaultDnsQuestion(record.name(), record.type(), record.dnsClass()));
                } else if (record instanceof DnsRawRecord raw) {
                    copy.addRecord(section, new DefaultDnsRawRecord(raw.name(), raw.type(), raw.dnsClass(),
                            raw.timeToLive(), Unpooled.copiedBuffer(raw.content())));
                } else if (!(record instanceof ReferenceCounted)) {
                    copy.addRecord(section, record);
                }
            }
        }
        return copy;
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

        String hostname = "N/A";
        if (rootNode.has("Question")) {
            for (JsonNode question : rootNode.get("Question")) {
                if (question.has("name")) {
                    hostname = question.get("name").asText();
                    break;
                }
            }
        }

        List<InetAddress> ipAddresses = new ArrayList<>();
        if (rootNode.has("Answer")) {
            for (JsonNode answer : rootNode.get("Answer")) {
                if (answer.has("type")) {
                    int typeCode = answer.get("type").asInt();
                    if (typeCode == 1 || typeCode == 28) {//A or AAAA
                        if (answer.has("data")) {
                            String ipAddress = answer.get("data").asText();
                            InetAddress address = IpAddressUtil.fromString(ipAddress);
                            if (address != null) {
                                ipAddresses.add(address);
                            }
                        }
                    }
                }
            }
        }
        return Pair.of(hostname, ipAddresses);
    }
}
