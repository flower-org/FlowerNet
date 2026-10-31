package com.flower.net.packetui.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SubnetsTest {
    @Test
    public void blankIsEmpty() {
        assertSame(Subnets.EMPTY, Subnets.parse(""));
        assertSame(Subnets.EMPTY, Subnets.parse(" ,  , "));
        assertFalse(Subnets.EMPTY.contains("10.0.0.1"));
        assertEquals("", Subnets.EMPTY.toString());
    }

    @Test
    public void ipv4SubnetsAndHosts() {
        Subnets subnets = Subnets.parse("10.42.0.0/24, 203.0.113.10  172.16.0.0/12");
        assertTrue(subnets.contains("10.42.0.1"));
        assertTrue(subnets.contains("10.42.0.255"));
        assertFalse(subnets.contains("10.42.1.0"));
        assertTrue(subnets.contains("203.0.113.10"));
        assertFalse(subnets.contains("203.0.113.11"));
        assertTrue(subnets.contains("172.31.255.255"));
        assertFalse(subnets.contains("172.32.0.0"));
        assertFalse(subnets.contains("::1"));
    }

    @Test
    public void ipv6() {
        Subnets subnets = Subnets.parse("fd00::/8,::1");
        assertTrue(subnets.contains("fd12:3456::1"));
        assertFalse(subnets.contains("fe80::1"));
        assertTrue(subnets.contains("::1"));
        assertFalse(subnets.contains("127.0.0.1"));
    }

    @Test
    public void zeroPrefixMatchesWholeFamily() {
        Subnets subnets = Subnets.parse("0.0.0.0/0");
        assertTrue(subnets.contains("1.2.3.4"));
        assertFalse(subnets.contains("2001:db8::1"));
    }

    @Test
    public void hostBitsAreClearedAndToStringRoundTrips() {
        Subnets subnets = Subnets.parse("10.42.0.1/24 192.168.1.130/25 2001:DB8::1/32 8.8.8.8");
        assertEquals("10.42.0.0/24, 192.168.1.128/25, 2001:db8::/32, 8.8.8.8", subnets.toString());
        assertEquals(subnets.toString(), Subnets.parse(subnets.toString()).toString());
        assertTrue(subnets.contains("192.168.1.255"));
        assertFalse(subnets.contains("192.168.1.127"));
    }

    @Test
    public void nonLiteralsDontMatch() {
        Subnets subnets = Subnets.parse("0.0.0.0/0");
        assertFalse(subnets.contains("example.com"));
        assertFalse(subnets.contains(""));
    }

    @Test
    public void invalidEntriesThrow() {
        assertEquals("Not an IP address or subnet: example.com",
                assertThrows(IllegalArgumentException.class, () -> Subnets.parse("10.0.0.0/8, example.com")).getMessage());
        assertThrows(IllegalArgumentException.class, () -> Subnets.parse("10.0.0.0/33"));
        assertThrows(IllegalArgumentException.class, () -> Subnets.parse("10.0.0.0/-1"));
        assertThrows(IllegalArgumentException.class, () -> Subnets.parse("10.0.0.0/"));
        assertThrows(IllegalArgumentException.class, () -> Subnets.parse("10.0.0.0/x"));
        assertThrows(IllegalArgumentException.class, () -> Subnets.parse("::/129"));
        assertThrows(IllegalArgumentException.class, () -> Subnets.parse("10.0.0"));
        assertThrows(IllegalArgumentException.class, () -> Subnets.parse("10.0.0.*"));
        assertThrows(IllegalArgumentException.class, () -> Subnets.parse("10.0.0.1-5"));
        assertThrows(IllegalArgumentException.class, () -> Subnets.parse("10.0.0.0/255.255.255.0"));
        assertThrows(IllegalArgumentException.class, () -> Subnets.parse("/24"));
    }
}
