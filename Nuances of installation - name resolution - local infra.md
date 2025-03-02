## 1. Interactions with local infrastructure

It's possible to imagine 2 typical ways of Socks+ proxy usage:
1. As a regular internet traffic proxy;
2. As a gateway to internal infrastructure (say, Kubernetes) on which the proxy is installed, or to its subset.

Those installations are technically the same, the difference is in how they are allowed to interact with local infrastructure.  
For the option 1 we want the user to not know any details about how and where the proxy is hosted, and we don't want any 
addresses or hostnames related to underlying cluster to be accessible or resolvable by the user.
For the option 2, on the contrary, we want all the underlying infrastructure to be visible, addresses accessible, and 
services discoverable by internal names.
We should be able to support both installation options and provide practical ways to achieve those requirements.

Our idea is that the best and most convenient way would be to implement just enough configs on the side 
of the proxy server itself to allow achieving that, and in this manner avoid any network-related configuration
on the Node/VM/Pod that's running the server to be a requirement.

Those configs boil down to the following: `IP access management` and `Name resolution settings`.
Before that comes the idea that any connection to any hostname is performed in 2 steps:
1. Name resolution (the actual mechanism may vary) and storing address mappings in the local cache
2. Using the obtained address to establish a connection.

I.e. we don't connect using something akin to `Socket socket = new Socket("example.com", 443);`,  
due to the fact that it will trigger local OS-level name resolution, which is not always desirable.  
We always resolve first, and we use our preferred configured way of resolution, and then connect directly to the IP address.
In this way we can always control an intermediate step of IP address validation. We allow connections via both IP and name, and this 
approach allows us to filter both types of requests by the final IP.

Thus, if we know the IP ranges on which our local services reside, we can deny those ranges to prevent 
local service interactions.
The standard ranges are:
 - 10.0.0.0/8
 - 192.168.0.0/16
 - 172.16.0.0/12

If we're using GKE, we will need to add more ranges, GKE-specific.
In order to determine those ranges, the following can be run in Cloud Console
```
gcloud container clusters describe <CLUSTER-NAME> --region <REGION> | grep Cidr
```
For additional security we can also employ a policy with which we prohibit direct access by IP, but that's really niche.

If we, on the other hand, want to run our proxy as a gateway to our infrastructure, we may either ignore those ranges, allowing 
connections to be made to any address, or actively allow those ranges, prohibiting our clients to connect to anything but the local 
services.
For gateway use case it's also important to be able to resolve local names properly, more on that in `Chapter 2`.

---

## 2. Name resolution

Hostname resolution is a very important part of network interaction and traditionally also a very popular attack 
vector, so it's paramount to get it right. A next-gen proxy solution should be able to address name resolution issues out-of-the box, to remove the burden of 
cumbersome and complex network setup from a user.  
Since name resolution thus becomes a part of Socks+ concern, we not only allow a variety of configuration options on 
the server side, but also provide clients with the ability to send name-resolution requests explicitly, proxying local 
name resolution as well as 3rd party services.  
In other words, Socks+ can proxy not only traffic, but also name resolution, making it a one-stop-shop for your network security.

[SocksPlus in a nutshell.md](SocksPlus%20in%20a%20nutshell.md) shows the exact ways in which name resolution requests can be formed by the user.
It's possible to proxy those requests to 3rd parties running different protocols and even perform 3rd party server TLS certificate validation.  
Let's review two basic resolution types which are related to internal infrastructure on which the proxy server itself 
is deployed, LOCAL_NAMESERVER and LOCAL_OS, the point being that those may end up resolving local names specific to the infrastructure on which our server is deployed.

- LOCAL_NAMESERVER sends name resolution requests to locally configured nameserver.
  - in GKE that won't resolve local service names, as our tests show:
    - e.g. in GKE `dig vault.vault` won't resolve the IP (**LOCAL_NAMESERVER**)
    - while `getent vault.vault` or `nslookup vault.vault` will (LOCAL_OS)


- LOCAL_OS resolution is _blocking_ in Java. (`InetAddress.getByName("...")`) 
  - LOCAL_OS will resolve all names, including local service names, including in GKE environments 
        (unlike LOCAL_NAMESERVER), but it's a blocking operation in Java.
  - Useful if we want to use our proxy as a gateway to our K8s environment.
  - ~~To optimize the blocking part, we should run LOCAL_NAMESERVER first
    (non-blocking) and fall back to the blocking method IFF LOCAL_NAMESERVER fails.~~
    - On the second thought, even blocking local lookup should be sufficiently fast, so why bother.
    - That's a cleaner approach, too.
  - Still, it would be great to figure a non-blocking version eventually, but low prio, since LOCAL_OS is for restrictive installations primarily - like infrastructure gateways, authorized personnel-only.


- LOCAL_OS and LOCAL_NAMESERVER should be disabled in direct name resolution requests from users by default.
  - We disable it by default in order to avoid any direct user interaction with components of local cluster.
  - With that said, using LOCAL_NAMESERVER implicit discovery by default is sensible, to achieve 
    the best latency, and it's "safe" at least in GKE, where it doesn't allow local service discovery.
  - However, disabling name resolution by itself won't prevent direct access to local services by IP. In order to prevent such access completely, IP range denylists should be used. E.g., denying a range will prevent both name resolution and data connections on a given range.

There is a plethora of ways to determine a local nameserver address in Java, but nothing that looks like a reliable standard.

More complete list of IP ranges - private, link-local, loopback, reserved, special use:
```
0.0.0.0/8
10.0.0.0/8
100.64.0.0/10
127.0.0.0/8
169.254.0.0/16
172.16.0.0/12
192.0.2.0/24
192.168.0.0/16
198.51.100.0/24
203.0.113.0/24
224.0.0.0/4
240.0.0.0/4

::/128
::1/128
::ffff:0:0/96
64:ff9b::/96
100::/64
2001::/32
2001:db8::/32
2002::/16
fc00::/7
fe80::/10
ff00::/8
```