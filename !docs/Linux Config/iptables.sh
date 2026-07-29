#!/bin/bash

iptables -t nat -F
ip6tables -t nat -F
iptables -t mangle -F
ip6tables -t mangle -F
iptables -F
ip6tables -F
iptables -X
ip6tables -X

iptables -P OUTPUT DROP
ip6tables -P OUTPUT DROP
iptables -P INPUT DROP
ip6tables -P INPUT DROP
iptables -P FORWARD DROP
ip6tables -P FORWARD DROP

# ================== external router port ==================

# Remote SOCKS5S server
iptables -A OUTPUT -p tcp --dport 8443 -d xxx.yyy.zzz.aaa -j ACCEPT
iptables -A INPUT -p tcp --sport 8443 -s xxx.yyy.zzz.aaa -m state --state ESTABLISHED,RELATED -j ACCEPT

# ================ END external router port ================


# ==================== local proxy port ====================
# KVM user network traffic looks like the following 
# {IN= OUT=lo SRC=127.0.0.1 DST=127.0.0.1 LEN=60 TOS=0x00 PREC=0x00 TTL=64 ID=29747 DF PROTO=TCP 
#   SPT=45852 DPT=1081 WINDOW=65495 RES=0x00 SYN URGP=0}

# SocksUI client
iptables -A OUTPUT -o lo -p tcp --dport 1081 -d 127.0.0.1 -s 127.0.0.1 -j ACCEPT
iptables -A INPUT -i lo -p tcp --sport 1081 -s 127.0.0.1 -d 127.0.0.1 -m state --state ESTABLISHED,RELATED -j ACCEPT

# SocksUI server
iptables -A OUTPUT -o lo -p tcp --sport 1081 -s 127.0.0.1 -d 127.0.0.1 -j ACCEPT
iptables -A INPUT -i lo -p tcp --dport 1081 -d 127.0.0.1 -s 127.0.0.1 -j ACCEPT

# ================== END local proxy port ==================

#DoH
#iptables -A OUTPUT -p tcp --dport 443 -d 1.1.1.1 -j ACCEPT
#iptables -A INPUT -p tcp --sport 443 -s 1.1.1.1 -m state --state ESTABLISHED,RELATED -j ACCEPT

#apt
#iptables -A OUTPUT -p tcp --dport 80 -d 208.77.20.19 -j ACCEPT
#iptables -A INPUT -p tcp --sport 80 -s 208.77.20.19 -m state --state ESTABLISHED,RELATED -j ACCEPT

#iptables -A OUTPUT -p tcp --dport 80 -d 172.66.152.176 -j ACCEPT
#iptables -A INPUT -p tcp --sport 80 -s 172.66.152.176 -m state --state ESTABLISHED,RELATED -j ACCEPT

#iptables -A OUTPUT -p tcp --dport 80 -d 172.66.152.176 -j ACCEPT
#iptables -A INPUT -p tcp --sport 80 -s 172.66.152.176 -m state --state ESTABLISHED,RELATED -j ACCEPT

#github
#iptables -A OUTPUT -p tcp --dport 443 -d 140.82.116.4 -j ACCEPT
#iptables -A INPUT -p tcp --sport 443 -s 140.82.116.4 -m state --state ESTABLISHED,RELATED -j ACCEPT
#iptables -A OUTPUT -p tcp --dport 443 -d 185.199.111.215 -j ACCEPT
#iptables -A INPUT -p tcp --sport 443 -s 185.199.111.215 -m state --state ESTABLISHED,RELATED -j ACCEPT
#iptables -A OUTPUT -p tcp --dport 443 -d 185.199.111.133 -j ACCEPT
#iptables -A INPUT -p tcp --sport 443 -s 185.199.111.133 -m state --state ESTABLISHED,RELATED -j ACCEPT




# ================ xfce4-session service ================
# We need to allow traffic to local xfce4-session service, on port 4101.
# Failure to do so will cause login screen to hang for ~2 minutes.
# Local traffic to this service comes malformed, packets come with empty OUT, like the following:
# {IN=lo OUT= MAC=00:00:00:00:00:00:00:00:00:00:00:00:08:00 SRC=127.0.0.1 DST=127.0.0.1 
#   LEN=60 TOS=0x00 PREC=0x00 TTL=64 ID=64015 DF PROTO=TCP SPT=35218 DPT=4101 WINDOW=65495 RES=0x00 SYN URGP=0} 
# I have no idea why, but because of that we have to work around interfaces cautiosly.

# ----- IPV4 -----
# local client, sends TCP traffic to [127.0.0.1:4101] from [127.0.0.1:any, any out]
iptables -A OUTPUT -p tcp --dport 4101 -d 127.0.0.1 -s 127.0.0.1 -j ACCEPT
# local client recieves TCP traffic back from [127.0.0.1:4101] to [127.0.0.1:any, any in]
iptables -A INPUT -p tcp --sport 4101 -s 127.0.0.1 -d 127.0.0.1 -m state --state ESTABLISHED,RELATED -j ACCEPT

# local server, sends TCP traffic from [127.0.0.1:4101, out lo] to [127.0.0.1:any port, any in]
iptables -A OUTPUT -o lo -p tcp --sport 4101 -s 127.0.0.1 -d 127.0.0.1 -j ACCEPT
# local server accepts TCP traffic at [127.0.0.1:4101, in lo] from [127.0.0.1:any port, any out]
iptables -A INPUT -i lo -p tcp --dport 4101 -d 127.0.0.1 -s 127.0.0.1 -j ACCEPT

# ----- IPV6 -----
ip6tables -A OUTPUT -p tcp --dport 4101 -d ::1 -s ::1 -j ACCEPT
ip6tables -A INPUT -p tcp --sport 4101 -s ::1 -d ::1 -m state --state ESTABLISHED,RELATED -j ACCEPT
ip6tables -A OUTPUT -o lo -p tcp --sport 4101 -s ::1 -d ::1 -j ACCEPT
ip6tables -A INPUT -i lo -p tcp --dport 4101 -d ::1 -s ::1 -j ACCEPT

# ============== END xfce4-session service ==============


# Log all dropped packages, debug only.
#iptables -N logging
#iptables -A INPUT -j logging
#iptables -A OUTPUT -j logging
#iptables -A logging -m limit --limit 2/min -j LOG --log-prefix "IPTables general: " --log-level 7
#iptables -A logging -j DROP

#ip6tables -N logging
#ip6tables -A INPUT -j logging
#ip6tables -A OUTPUT -j logging
#ip6tables -A logging -m limit --limit 2/min -j LOG --log-prefix "IPTables general: " --log-level 7
#ip6tables -A logging -j DROP
