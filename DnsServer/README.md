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
TODO: empty responses?