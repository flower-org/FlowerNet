# Making PacketUi's DNS server the system DNS (Linux Mint)

PacketUi runs a DNS server on `127.0.0.1:5300`. Hosts it resolves are remembered, so packet rules can match host names
instead of IPs. That only works if every program on the machine uses this server. This guide makes it the only DNS
server, with no fallback, and keeps it that way across reboots.

How it fits together: programs ask systemd-resolved (`127.0.0.53`, from `/etc/resolv.conf`), which forwards everything
to `127.0.0.1:5300`. NetworkManager is told to stop handing over DNS servers it gets from DHCP (your router), so
nothing else can answer.

> While PacketUi is stopped, **nothing resolves** (that's the point: no bypass). Start PacketUi first, and see
> [Undo](#undo) if you're stuck.

Tested on Linux Mint 22 (systemd 255, NetworkManager 1.46). Requires systemd 246 or later for the `IP:port` syntax.

## 1. Check the prerequisites

```bash
ls -l /etc/resolv.conf      # must point to ../run/systemd/resolve/stub-resolv.conf
resolvectl --version        # systemd 246 or later
```

If `/etc/resolv.conf` isn't that symlink, systemd-resolved isn't in use and this guide doesn't apply as-is.

## 2. Start PacketUi and check its DNS server

Click **Start** in PacketUi, then:

```bash
dig @127.0.0.1 -p 5300 example.com +short    # prints IPs
```

## 3. Send all DNS to PacketUi (systemd-resolved)

```bash
sudo mkdir -p /etc/systemd/resolved.conf.d
sudo tee /etc/systemd/resolved.conf.d/flowernet.conf <<'EOF'
[Resolve]
DNS=127.0.0.1:5300
Domains=~.
FallbackDNS=
DNSSEC=no
DNSOverTLS=no
Cache=no
EOF
```

- `Domains=~.`: every domain goes to this server.
- `FallbackDNS=`: no built-in fallback servers.
- `Cache=no`: every lookup reaches PacketUi, so its IP-to-host data stays current. PacketUi caches answers itself.

## 4. Stop NetworkManager handing out other DNS servers

```bash
sudo tee /etc/NetworkManager/conf.d/flowernet-dns.conf <<'EOF'
[main]
dns=none
EOF
```

This applies to every connection, including Wi-Fi networks you join later. Without it, each DHCP lease puts the
router's DNS server back next to PacketUi's, and it answers whenever PacketUi doesn't.

## 5. Apply

```bash
sudo systemctl restart NetworkManager systemd-resolved
```

## 6. Verify

```bash
resolvectl status
```

Expected:

- **Global:** `DNS Servers: 127.0.0.1:5300` and `DNS Domain: ~.`.
- **Each link** (`enp0s31f6`, `wlp...`): **no** `DNS Servers` line, and `Current Scopes: none`.

If a link still lists a DNS server (left over from before step 4), clear it, using your interface name:

```bash
sudo resolvectl revert enp0s31f6
```

Then check that there's no bypass:

```bash
dig example.com                # with PacketUi running: NOERROR with IPs
# Click Stop in PacketUi, then:
ss -tulpn | grep 5300          # prints nothing
dig example.com                # SERVFAIL: nothing else answers
```

## 7. Browsers

Firefox and Chrome can do their own DNS over HTTPS, which bypasses all of this. Turn it off:

- **Firefox:** Settings → Privacy & Security → DNS over HTTPS → **Off**.
- **Chrome/Chromium:** Settings → Privacy and security → Security → **Use secure DNS** → off.

## Persistence across restarts

Everything above is stored in files, so it survives reboots and reconnects:

| What | File |
|------|------|
| All DNS to `127.0.0.1:5300`, no fallback | `/etc/systemd/resolved.conf.d/flowernet.conf` |
| No DNS servers from DHCP/router | `/etc/NetworkManager/conf.d/flowernet-dns.conf` |

After a reboot, run the checks in [step 6](#6-verify) once to confirm.

Two things don't persist on their own:

- **PacketUi itself.** Until it's started and you click **Start**, DNS fails, including right after boot.
- **`resolvectl` commands** (`revert`, `default-route`, `dns`) only last until the next restart. Use them for one-off
  fixes, not as configuration.

## Troubleshooting

| Symptom | Cause / fix |
|---------|-------------|
| `dig example.com` still answers with PacketUi stopped | Something else answers. Check `resolvectl status`: a link with `DNS Servers` → `sudo resolvectl revert <link>`, and make sure step 4 is in place. Check `ss -tulpn \| grep 5300` for a leftover process. |
| `dig` shows `SERVER: 127.0.0.53` | Expected: that's systemd-resolved, which forwards to `127.0.0.1:5300`. |
| Everything fails with PacketUi running | `dig @127.0.0.1 -p 5300 example.com`. If that fails too, PacketUi can't reach its upstream: its packet rules must allow `1.1.1.1` port `853`. |
| Rules match IPs, not host names | The program resolved the name some other way (browser DNS over HTTPS, hard-coded IPs, or a lookup made before this setup). |

## Undo

```bash
sudo rm /etc/systemd/resolved.conf.d/flowernet.conf /etc/NetworkManager/conf.d/flowernet-dns.conf
sudo systemctl restart NetworkManager systemd-resolved
```

If you also ran `nmcli con mod ... ipv4.ignore-auto-dns yes` earlier, turn it back off for each connection:

```bash
nmcli -g NAME con show | while read -r c; do
  sudo nmcli con mod "$c" ipv4.ignore-auto-dns no ipv6.ignore-auto-dns no
done
sudo nmcli con up "Wired connection 1"     # or reconnect your Wi-Fi
```
