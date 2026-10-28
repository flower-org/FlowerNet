package com.flower.net.packetui.server;

import com.flower.net.utils.IpAddressUtil;

import javax.annotation.Nullable;

/** Addressing info parsed from a raw IPv4/IPv6 packet. Ports are 0 if not TCP/UDP or not available (non-first fragment). */
public record PacketInfo(int ipProtocol, String srcHost, int srcPort, String dstHost, int dstPort) {
    public static final int PROTO_ICMP = 1;
    public static final int PROTO_TCP = 6;
    public static final int PROTO_UDP = 17;
    public static final int PROTO_ICMPV6 = 58;

    private static final int IPV6_HOP_BY_HOP = 0;
    private static final int IPV6_ROUTING = 43;
    private static final int IPV6_FRAGMENT = 44;
    private static final int IPV6_AH = 51;
    private static final int IPV6_DEST_OPTIONS = 60;

    /** @return null if the packet is not a well-formed IPv4/IPv6 packet */
    public static @Nullable PacketInfo parse(byte[] packet) {
        if (packet.length < 1) {
            return null;
        }
        int version = (packet[0] & 0xF0) >>> 4;
        if (version == 4) {
            return parseIpv4(packet);
        } else if (version == 6) {
            return parseIpv6(packet);
        }
        return null;
    }

    static @Nullable PacketInfo parseIpv4(byte[] packet) {
        if (packet.length < 20) {
            return null;
        }
        int headerLength = (packet[0] & 0x0F) * 4;
        if (headerLength < 20 || packet.length < headerLength) {
            return null;
        }
        int protocol = u8(packet, 9);
        int fragmentOffset = u16(packet, 6) & 0x1FFF;
        String src = IpAddressUtil.toCanonicalString(packet, 12, 4);
        String dst = IpAddressUtil.toCanonicalString(packet, 16, 4);
        return withPorts(packet, protocol, src, dst, fragmentOffset == 0 ? headerLength : -1);
    }

    static @Nullable PacketInfo parseIpv6(byte[] packet) {
        if (packet.length < 40) {
            return null;
        }
        String src = IpAddressUtil.toCanonicalString(packet, 8, 16);
        String dst = IpAddressUtil.toCanonicalString(packet, 24, 16);

        int nextHeader = u8(packet, 6);
        int offset = 40;
        while (true) {
            if (nextHeader == IPV6_HOP_BY_HOP || nextHeader == IPV6_ROUTING || nextHeader == IPV6_DEST_OPTIONS) {
                if (packet.length < offset + 2) {
                    return null;
                }
                int length = (u8(packet, offset + 1) + 1) * 8;
                nextHeader = u8(packet, offset);
                offset += length;
            } else if (nextHeader == IPV6_AH) {
                if (packet.length < offset + 2) {
                    return null;
                }
                int length = (u8(packet, offset + 1) + 2) * 4;
                nextHeader = u8(packet, offset);
                offset += length;
            } else if (nextHeader == IPV6_FRAGMENT) {
                if (packet.length < offset + 8) {
                    return null;
                }
                int fragmentOffset = u16(packet, offset + 2) >>> 3;
                nextHeader = u8(packet, offset);
                offset += 8;
                if (fragmentOffset != 0) {
                    return withPorts(packet, nextHeader, src, dst, -1);
                }
            } else {
                return withPorts(packet, nextHeader, src, dst, offset);
            }
        }
    }

    /** @param transportOffset offset of the transport header, or -1 if ports aren't available */
    static PacketInfo withPorts(byte[] packet, int protocol, String src, String dst, int transportOffset) {
        int srcPort = 0;
        int dstPort = 0;
        if ((protocol == PROTO_TCP || protocol == PROTO_UDP)
                && transportOffset >= 0 && packet.length >= transportOffset + 4) {
            srcPort = u16(packet, transportOffset);
            dstPort = u16(packet, transportOffset + 2);
        }
        return new PacketInfo(protocol, src, srcPort, dst, dstPort);
    }

    private static int u8(byte[] buffer, int offset) {
        return buffer[offset] & 0xFF;
    }

    private static int u16(byte[] buffer, int offset) {
        return ((buffer[offset] & 0xFF) << 8) | (buffer[offset + 1] & 0xFF);
    }
}
