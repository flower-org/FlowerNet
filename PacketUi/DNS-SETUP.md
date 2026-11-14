# Making PacketUi's DNS server the system DNS (Linux Mint / Debian)

PacketUi runs a DNS server on `127.0.0.1:5300` and `[::1]:5300`. Hosts it resolves are remembered, so packet rules can match host names
instead of IPs. That only works if every program on the machine uses this server. This guide makes it the only DNS
server, with no fallback, and keeps it that way across reboots.

How it fits together: nftables rules in [`nf-iptables.sh`](nf-iptables.sh) redirect `127.0.0.1:53` and `[::1]:53`
(UDP and TCP) to port `5300` on the same address. Replies are translated back by conntrack, so programs see the
answer coming from port 53. `/etc/resolv.conf` needs no `nameserver` line: without one, programs ask `127.0.0.1`
(and `dig` also `::1`), which is where the redirect is. systemd-resolved isn't needed and is purged to reduce the
attack surface. NetworkManager is told to stop managing DNS.

> While PacketUi is stopped, **nothing resolves** (that's the point: no bypass). Start PacketUi first, and see
> [Undo](#undo) if you're stuck.

## 1. Load the nftables rules

The redirect is the `DNS` section of [`nf-iptables.sh`](nf-iptables.sh). Set the script up as described in
[run on startup.md](run%20on%20startup.md), then check:

```bash
sudo nft list table ip nfbridge_dns     # the 127.0.0.1 rules from the DNS section of the script
sudo nft list table ip6 nfbridge_dns    # the ::1 rules
```

The redirect happens before filtering, so PacketUi sees programs' DNS as going to `127.0.0.1:5300` /
`[::1]:5300`. PacketUi's packet rules must allow that (on `lo`, both directions).

## 2. Start PacketUi and check its DNS server

Click **Start** in PacketUi, then:

```bash
dig @127.0.0.1 -p 5300 example.com +short    # prints IPs
dig @::1 -p 5300 example.com +short          # prints IPs
```

## 3. Stop NetworkManager managing DNS

```bash
sudo tee /etc/NetworkManager/conf.d/flowernet-dns.conf <<'EOF'
[main]
dns=none
EOF
```

With `dns=none`, NetworkManager doesn't write DNS servers from DHCP (your router) into `/etc/resolv.conf`.
This applies to every connection, including Wi-Fi networks you join later.

## 4. Purge systemd-resolved

First see what else would be removed, and make sure it's nothing you need:

```bash
apt -s purge systemd-resolved libnss-resolve | grep '^Purg'
```

Then:

```bash
sudo systemctl disable --now systemd-resolved
sudo apt purge systemd-resolved libnss-resolve
grep '^hosts:' /etc/nsswitch.conf     # must not contain "resolve"; if it does, remove that word (and its [..] action)
sudo systemctl restart NetworkManager
```

Whatever is left in `/etc/resolv.conf` (nothing, or a copy with only `search .`) is fine, as long as it has no
`nameserver` line pointing somewhere else.

## 5. Verify

```bash
grep nameserver /etc/resolv.conf     # nothing (or only 127.0.0.1 / ::1)
systemctl status systemd-resolved    # "could not be found"
ss -tulpn | grep ':53 '              # nothing on 127.0.0.53 (dnsmasq on 10.42.0.1 is fine when sharing internet)
```

Then check that there's no bypass:

```bash
dig example.com                # with PacketUi running: NOERROR with IPs, no "communications error" lines
# Click Stop in PacketUi, then:
ss -tulpn | grep 5300          # prints nothing
dig example.com                # times out: nothing else answers
```

## 6. Browsers

Firefox and Chrome can do their own DNS over HTTPS, which bypasses all of this. Turn it off:

- **Firefox:** Settings → Privacy & Security → DNS over HTTPS → **Off**.
- **Chrome/Chromium:** Settings → Privacy and security → Security → **Use secure DNS** → off.

## Persistence across restarts

Everything above is stored in files, so it survives reboots and reconnects:

| What | File |
|------|------|
| `127.0.0.1:53` / `[::1]:53` redirected to port `5300` | [`nf-iptables.sh`](nf-iptables.sh), see [run on startup.md](run%20on%20startup.md) |
| NetworkManager leaves DNS alone | `/etc/NetworkManager/conf.d/flowernet-dns.conf` |

After a reboot, run the checks in [step 5](#5-verify) once to confirm.

PacketUi itself doesn't persist: until it's started and you click **Start**, DNS fails, including right after boot.

## Troubleshooting

| Symptom | Cause / fix |
|---------|-------------|
| `dig example.com` still answers with PacketUi stopped | Something else answers on `:5300`: `ss -tulpn \| grep 5300` for a leftover process. Or the redirect isn't loaded: `sudo nft list table ip nfbridge_dns` and `ip6 nfbridge_dns`. |
| `/etc/resolv.conf` gets a `nameserver` line again | Something else manages it: check step 3 is in place, and that no other resolver package (`resolvconf`, `openresolv`) is installed. |
| Everything fails with PacketUi running | `dig @127.0.0.1 -p 5300 example.com`. If that fails, PacketUi can't reach its upstream: its packet rules must allow `1.1.1.1` port `853`. If it works but `dig example.com` doesn't, packet rules must allow `127.0.0.1:5300` on `lo`, or the redirect isn't loaded. |
| Rules match IPs, not host names | The program resolved the name some other way (browser DNS over HTTPS, hard-coded IPs, or a lookup made before this setup). |

## Undo

```bash
sudo rm /etc/NetworkManager/conf.d/flowernet-dns.conf
sudo apt install systemd-resolved     # recreates the /etc/resolv.conf symlink to its stub
sudo ln -sf ../run/systemd/resolve/stub-resolv.conf /etc/resolv.conf   # in case it didn't
sudo systemctl enable --now systemd-resolved
sudo systemctl restart NetworkManager
```

Then remove the `DNS` section from [`nf-iptables.sh`](nf-iptables.sh) and rerun it (or `sudo nft delete table ip nfbridge_dns; sudo nft delete table ip6 nfbridge_dns`
for the current boot only).

## Useful commands for debian

`apt install dnsutils git`

`apt remove gstreamer* avahi* cups* *office* *cron* *wpa* *modem* *speech* *speak* *task*`

`apt autoremove`

`xset dpms 0 0 0 && xset s off`

`sudo nmcli con mod "Wired connection 1" ipv4.ignore-auto-dns yes ipv6.ignore-auto-dns yes`