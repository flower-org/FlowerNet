package com.flower.net.dns.dotclient.viasocks;

import java.nio.charset.StandardCharsets;

/** Username/password for SOCKS5 authentication (RFC 1929). */
public record Socks5Credentials(String username, String password) {
    public Socks5Credentials {
        // RFC 1929: each is 1 to 255 bytes; Netty encodes them as US-ASCII
        if (!isValid(username) || !isValid(password)) {
            throw new IllegalArgumentException("SOCKS5 username and password must be 1-255 ASCII characters");
        }
    }

    private static boolean isValid(String value) {
        return !value.isEmpty() && value.length() <= 255 && StandardCharsets.US_ASCII.newEncoder().canEncode(value);
    }

    @Override
    public String toString() {
        return "Socks5Credentials[username=" + username + ", password=****]";
    }
}
