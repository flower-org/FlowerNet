How to test local DNS:
    `dig @127.0.0.1 -p 5300 google.com`


DNS Via TLS
    `kdig -d @1.1.1.1 +tls-ca +tls-host=one.one.one.one example.com`


DNS via HTTPS (FYI only, unused)

`curl -i -v --http1.1 --header "accept: application/dns-json" https://1.1.1.1/dns-query?name=google.com`

```
{
    "Status":0,
    "TC":false,
    "RD":true,
    "RA":true,
    "AD":false,
    "CD":false,
    "Question": [
        {
            "name": "google.com",
            "type": 1
        }
    ],
    "Answer": [
        {
            "name": "google.com",
            "type": 1,
            "TTL": 290,
            "data": "142.250.217.110"
        }
    ]
}
```

---

Upstream connections: queries share long-lived DoT connections (up to 3) and are pipelined (RFC 7766):
each gets a unique ID and responses are matched to it in any order. Idle connections close after 7s and are
reopened on demand. After a failed connection, new attempts back off exponentially (250ms doubling up to 30s,
reset on success); meanwhile queries use the remaining connections, or get SERVFAIL if there are none.
Upstream via SOCKS5: `DnsServer <port> <useCache> <socksHost> <socksPort>`. If the proxy needs username/password
authentication (RFC 1929), set the `SOCKS_USERNAME` and `SOCKS_PASSWORD` environment variables (kept off the command
line so the password doesn't show in the process list). The TLS handshake through the proxy times out after 5s.
Embedding in your app:

```java
DnsServer server = DnsServer.start(5300, true);   // port, useCache; returns once listening on UDP + TCP

List<String> hosts = server.resolvedHosts().hostsFor(InetAddress.getByName("104.20.23.154"));
// ["example.com"], most recent first; [] if this server never resolved that IP

server.close();                                    // on app shutdown
```

For a SOCKS proxy, build a `DnsOverTlsClient` yourself and call `DnsServer.start(port, client)`; the server owns
the client and shuts it down on `close()`.

Access: by default the server listens on 127.0.0.1 only, so it isn't an open resolver. To serve a network, pass a bind
address and a `ClientAccess`: `DnsServer.start("0.0.0.0", port, client, new ClientAccess(List.of("192.168.1.0/24"),
50, 100))`. Clients outside the allowed subnets get REFUSED; each client IP is rate limited (queries/second, burst)
and queries over the limit are dropped. `ClientAccess.defaults()` allows loopback, private and link-local networks
at 50 queries/s with bursts of 100.

IP to host lookup (Java API only): `server.resolvedHosts().hostsFor(ip)` returns the host names this server resolved
to that IP for its clients, most recent first. It never resolves anything itself. It keeps up to 100k (IP, host)
pairs, evicted least-recently-used first and regardless of DNS TTL. Resolving a host bumps its IPs; a lookup bumps
the hosts it returns. Hosts are the names clients asked for (lowercase, no trailing dot), even via CNAMEs.
TODO: empty responses?