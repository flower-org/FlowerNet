#!/bin/bash
# Sends all INPUT/OUTPUT/FORWARD packets to NFQUEUE 0 (PacketUi) using nftables only.
# Each packet must be queued from one place only: a second queue rule (e.g. iptables) makes PacketUi decide
# and capture every packet twice. The file name is kept for existing startup setups (see "run on startup.md").

# Start from a clean slate: removes all tables and chains of every family (ip/ip6 nat, mangle, filter, ...).
# Replaces the iptables/ip6tables -t nat -F, -t mangle -F, -F and -X calls. Covers iptables rules too,
# since Mint's iptables is iptables-nft.
nft flush ruleset

# Default DROP for INPUT/OUTPUT/FORWARD (replaces iptables/ip6tables -P ... DROP): the base chains' policy.
# When PacketUi isn't running, queued packets are dropped (no bypass).
nft add table inet nfbridge
nft add chain inet nfbridge in  '{ type filter hook input   priority 0; policy drop; }'
nft add chain inet nfbridge out '{ type filter hook output  priority 0; policy drop; }'
nft add chain inet nfbridge forward_chain '{ type filter hook forward priority 0; policy drop; }'

# Override rules MUST come BEFORE the queue rules: queue is terminal, so rules after it are never reached.

# ================ xfce4-session service ================
# We need to allow traffic to local xfce4-session service, on port 4101.
# Failure to do so will cause login screen to hang for ~2 minutes.
# Local traffic to this service comes malformed, packets come with empty OUT, like the following:
# {IN=lo OUT= MAC=00:00:00:00:00:00:00:00:00:00:00:00:08:00 SRC=127.0.0.1 DST=127.0.0.1
#   LEN=60 TOS=0x00 PREC=0x00 TTL=64 ID=64015 DF PROTO=TCP SPT=35218 DPT=4101 WINDOW=65495 RES=0x00 SYN URGP=0}
# I have no idea why, but because of that we have to work around interfaces cautiosly.

# ----- IPV4 -----
# local client, sends TCP traffic to [127.0.0.1:4101] from [127.0.0.1:any, any out]
nft add rule inet nfbridge out ip daddr 127.0.0.1 ip saddr 127.0.0.1 tcp dport 4101 accept
# local client recieves TCP traffic back from [127.0.0.1:4101] to [127.0.0.1:any, any in]
nft add rule inet nfbridge in ip saddr 127.0.0.1 ip daddr 127.0.0.1 tcp sport 4101 ct state established,related accept

# local server, sends TCP traffic from [127.0.0.1:4101, out lo] to [127.0.0.1:any port, any in]
nft add rule inet nfbridge out oifname lo ip saddr 127.0.0.1 ip daddr 127.0.0.1 tcp sport 4101 accept
# local server accepts TCP traffic at [127.0.0.1:4101, in lo] from [127.0.0.1:any port, any out]
nft add rule inet nfbridge in iifname lo ip daddr 127.0.0.1 ip saddr 127.0.0.1 tcp dport 4101 accept

# ----- IPV6 -----
nft add rule inet nfbridge out ip6 daddr ::1 ip6 saddr ::1 tcp dport 4101 accept
nft add rule inet nfbridge in ip6 saddr ::1 ip6 daddr ::1 tcp sport 4101 ct state established,related accept

nft add rule inet nfbridge out oifname lo ip6 saddr ::1 ip6 daddr ::1 tcp sport 4101 accept
nft add rule inet nfbridge in iifname lo ip6 daddr ::1 ip6 saddr ::1 tcp dport 4101 accept

# ============== END xfce4-session service ==============

# Everything else goes to the NFQUEUE 0.
nft add rule inet nfbridge in  queue num 0
nft add rule inet nfbridge out queue num 0
nft add rule inet nfbridge forward_chain queue num 0

# ================ Router (NetworkManager "share internet", LAN 10.42.0.0/24) ================
# Address translation only, no filtering: PacketUi still decides every packet.
# Matches the LAN subnet, not an interface, so on a machine that isn't sharing internet these rules match nothing.
# Own table, not "nat": NetworkManager adds its rules to "ip nat" via iptables-nft, and mixing ours in could break that.
# PacketUi sees LAN addresses: dstnat happens before filtering and srcnat after it.
nft add table ip nfbridge_nat

# NAT for the LAN (replaces NetworkManager's masquerade rule, which "nft flush ruleset" removes)
nft add chain ip nfbridge_nat postrouting '{ type nat hook postrouting priority srcnat; policy accept; }'
nft add rule ip nfbridge_nat postrouting ip saddr 10.42.0.0/24 ip daddr != 10.42.0.0/24 masquerade

# All LAN DNS goes to the router's dnsmasq (-> systemd-resolved -> PacketUi DNS), whichever server a device asked:
# PacketUi only knows host names for IPs its DNS resolved. Shows up in PacketUi as IN to 10.42.0.1:53.
nft add chain ip nfbridge_nat prerouting '{ type nat hook prerouting priority dstnat; policy accept; }'
nft add rule ip nfbridge_nat prerouting ip saddr 10.42.0.0/24 udp dport 53 redirect to :53
nft add rule ip nfbridge_nat prerouting ip saddr 10.42.0.0/24 tcp dport 53 redirect to :53

# ============== END Router ==============

# mkdir /run/nfbridge/
# chown {USERNAME_THAT_RUNS_PACKET_UI} /run/nfbridge/

# nohup /etc/nfbridge --headers-only >/dev/null 2>&1 &
