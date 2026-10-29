package com.flower.net.dns.dotclient;

import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsSection;

/** Copies DNS messages so that each owner holds its own references to the record buffers. */
public final class DnsMessages {
    private DnsMessages() {
    }

    /** Returns a record that can be owned independently: raw records are retained duplicates, others are immutable. */
    public static DnsRecord retainedDuplicate(DnsRecord record) {
        return record instanceof DnsRawRecord ? ((DnsRawRecord) record).retainedDuplicate() : record;
    }

    /**
     * Copies the response's header and records under {@code id}.
     * The copy holds its own references and must be released by its owner, independently of {@code response}.
     */
    public static DefaultDnsResponse retainedCopy(DnsResponse response, int id) {
        DefaultDnsResponse copy = new DefaultDnsResponse(id, response.opCode(), response.code());
        copyHeaderFlags(response, copy);
        for (DnsSection section : DnsSection.values()) {
            for (int i = 0, count = response.count(section); i < count; i++) {
                copy.addRecord(section, retainedDuplicate(response.recordAt(section, i)));
            }
        }
        return copy;
    }

    /** Copies AA, TC, RA, RD and Z; Netty's 3-bit Z field also carries the AD and CD bits (RFC 4035). */
    public static void copyHeaderFlags(DnsResponse from, DnsResponse to) {
        to.setAuthoritativeAnswer(from.isAuthoritativeAnswer())
                .setTruncated(from.isTruncated())
                .setRecursionAvailable(from.isRecursionAvailable())
                .setRecursionDesired(from.isRecursionDesired())
                .setZ(from.z());
    }
}
