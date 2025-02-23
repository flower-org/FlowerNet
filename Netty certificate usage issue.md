https://github.com/netty/netty/issues/14419

> TLDR - the issue is that client can pin server certificate, but server can't pin client certificate.  

> So on the client side we can do exact matching for server certificate; while on the server side we can't, and all we can do is to match all clients by some parent/root CA cert.  
> I'm not saying that it's not useful, but pinning of a single trusted client certificate is not possible on the server - we have to accept all of them from the same root CA.

> I still believe this is an inconsistency in the way Netty handles certificates, even though they insist that it works as intended (no reason why or logic attached, or nothing conceptually solid that I could follow anyway).  
> In any case, this peculiarity is something to keep in mind when working with TLS in Netty.  
> I copy the full thread here for historical reasons.

---

## Inconsistencies in certificate behavior in Netty.

**codekrolik2 on Oct 27, 2024**

Hello guys, I'd like to understand whether the behavior shown below is expected and there is some logic behind it, or it's inconsistent (that's the way it looks to me).

To reproduce, we're using 2 certificates, one with private key
- ca.crt
- test.crt, test.key  
  , where "test.crt" is signed by "ca.crt".

Let's review 4 scenarios, each presenting a unique behavior:

1.1) Mutual TLS
- Client is using  
  KeyManagerFactory with { "test.crt", "test.key" }  
  InsecureTrustManagerFactory.INSTANCE
- Server is using  
  new SelfSignedCertificate();  
  TrustManagerFactory with { "test.crt" }

`Behavior - TLS handshake error on server "Empty client certificate chain"`

1.2) Mutual TLS
- Client is using  
  KeyManagerFactory with { "test.crt", "test.key" }  
  InsecureTrustManagerFactory.INSTANCE
- Server is using  
  new SelfSignedCertificate();  
  TrustManagerFactory with { "ca.crt" }

`Behavior - works normally`

2.1) One-way TLS
- Client is using  
  TrustManagerFactory with { "test.crt" }
- Server is using  
  KeyManagerFactory with { "test.crt", "test.key" }

`Behavior - works normally`

2.2) One-way TLS
- Client is using  
  TrustManagerFactory with { "ca.crt" }
- Server is using  
  KeyManagerFactory with { "test.crt", "test.key" }

`Behavior - TLS handshake error on client "Extended key usage does not permit use for TLS server authentication"`

---
**chrisvest on Nov 11, 2024**

> 1.1) `Behavior - TLS handshake error on server "Empty client certificate chain"`

Correct. Server requests a cert path from the client, and that request includes a list of CAs that will be accepted. In this case server thinks test.crt is a CA. The client will then be looking for a cert that is issued by test.crt and finds none, hence returns an empty cert path that the server rejects.

> 1.2) `Behavior - works normally`

Correct. You configured stuff correctly.

> 2.1) `Behavior - works normally`

Correct. The client considers test.crt to be a Trust Anchor, and can thus immediately trust it without doing any validation. This is effectively certificate pinning.

> 2.2) `Behavior - TLS handshake error on client "Extended key usage does not permit use for TLS server authentication"`

Correct. The test.crt only include TLS Web Client Authentication in its extended key usage, so the server is not allowed to use this cert and the client refuses to validate it.

---
**codekrolik2 on Nov 12, 2024**

@chrisvest Just to get this straight, according to you,

1.1 In this case server thinks test.crt is a CA. The client will then be looking for a cert that is issued by test.crt and finds none, hence returns an empty cert path that the server rejects.

and

2.1 The client considers test.crt to be a Trust Anchor, and can thus immediately trust it without doing any validation. This is effectively certificate pinning.

is not inconsistent?

---
**chrisvest on Nov 12, 2024**

@codekrolik2 the protocol is not symmetric between client and server. The server always sends the cert you configure, so the client can check it. The other way, clients only send certs when requested, and the request includes what CAs are accepted, most clients will not include those CAs in the returned list of certs because the CA should already be known to both peers. Then you end up with an empty path, which is an error. Clients and servers are different in important ways, and it shows like this in your tests.

---
**codekrolik2 on Nov 12, 2024**

Ok, I was just making sure that it's expected behavior when client does certificate pinning, and server doesn't.
What I read above is that there is no consistency that's expected between the behaviour of similar concepts on server and client.
Therefore, I shouldn't expect server to pin client certificates - it's expected that server won't pin, even though I can pin server certificates on the client.

@chrisvest Apparently to achieve similar pinning logic on server I need to implement a custom TLS handshake handler?

Thanks!

---
**chrisvest on Nov 12, 2024**

@codekrolik2 If you want to pin client certs on the server side, you can implement a custom TrustManager with the following behavior:

1. Wrap and delegate the check* calls to an ordinarily configured standard JDK TrustManager implementation.
2. In the checkClientTrusted methods, bypass the delegate call if the SHA-256 of the first certificate (aka. the leaf fingerprint) in the chain matches a pinned fingerprint.
3. You can optionally add expiration checks and other validation for the pinned certificates, depending on your use case.
