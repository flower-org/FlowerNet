# Replacing NetworkManager with systemd-networkd (Debian)

This guide removes NetworkManager from a PacketUi router box and does the same job with systemd-networkd, which
ships with systemd on Debian (nothing to install). Less software running, and nothing reconfiguring the network
behind PacketUi's back.

The setup it replaces:

- **WAN:** a USB modem that shows up as a normal network card (`enx…`), getting its address by DHCP.
- **LAN:** an ethernet port in NetworkManager's "share internet" mode: `10.42.0.1/24`, DHCP server for devices on
  it, forwarding to the WAN.

What NetworkManager was doing, and what does it now:

| Job | With NetworkManager | Without |
|-----|---------------------|---------|
| DHCP client on the modem | NetworkManager | systemd-networkd ([step 2](#2-wan-the-usb-modem)) |
| `10.42.0.1/24` on ethernet | NetworkManager | systemd-networkd ([step 3](#3-lan-the-shared-ethernet-port)) |
| DHCP server on the LAN | dnsmasq started by NetworkManager | systemd-networkd's built-in DHCP server ([step 3](#3-lan-the-shared-ethernet-port)) |
| IPv4 forwarding | NetworkManager | sysctl file ([step 4](#4-ip-forwarding)) |
| NAT (masquerade) | [`nf-iptables.sh`](nf-iptables.sh) | unchanged |
| LAN DNS to PacketUi | [`nf-iptables.sh`](nf-iptables.sh) | unchanged |

> Do this from a local keyboard and screen, not over SSH: the network goes away when NetworkManager is purged and
> only comes back after the reboot. Write all the files **before** purging NetworkManager.

## 1. Find the interface names

```bash
ip -br link
```

- The modem is `enx…` (or `usb0`). If it's `wwan0` instead, it's a QMI/MBIM modem that needs ModemManager to
  connect, and this guide doesn't cover it.
- The ethernet port is `enp…` or `eno…`.

To double-check the modem is a plain network card:

```bash
for i in /sys/class/net/*; do echo "$(basename $i) $(basename $(readlink -f $i/device/driver) 2>/dev/null)"; done
```

The modem's driver must be `cdc_ether`, `rndis_host` or `cdc_ncm`.

## 2. WAN: the USB modem

`/etc/systemd/network/10-wan.network` (replace the name with your modem's; `enx…` names come from the MAC
address, so they don't change):

```bash
sudo tee /etc/systemd/network/10-wan.network <<'EOF'
[Match]
Name=enx* <- put actual interface name here

[Network]
DHCP=yes

[DHCPv4]
UseDNS=no

[IPv6AcceptRA]
UseDNS=no
EOF
```

`UseDNS=no` keeps the modem's DNS servers out: DNS stays with PacketUi (see [DNS-SETUP.md](DNS-SETUP.md)).

## 3. LAN: the shared ethernet port

`/etc/systemd/network/20-lan.network` (replace `enp1s0` with your ethernet name):

```bash
sudo tee /etc/systemd/network/20-lan.network <<'EOF'
[Match]
Name=enp1s0 <- put actual interface name here

[Network]
Address=10.42.0.1/24
DHCPServer=yes
ConfigureWithoutCarrier=yes

[DHCPServer]
PoolOffset=10
PoolSize=200
EmitDNS=yes
DNS=10.42.0.1
EOF
```

- `ConfigureWithoutCarrier=yes`: `10.42.0.1` is set even when no cable is plugged in.
- Devices get `10.42.0.11`–`10.42.0.210`, with `10.42.0.1` as gateway and DNS. Their DNS goes to PacketUi
  whatever server they use, through the `Router` section of [`nf-iptables.sh`](nf-iptables.sh).
- No `IPMasquerade=` on purpose: NAT is in [`nf-iptables.sh`](nf-iptables.sh), and its `nft flush ruleset` would
  remove networkd's NAT rules anyway.

## 4. IP forwarding

```bash
echo 'net.ipv4.ip_forward=1' | sudo tee /etc/sysctl.d/90-flowernet-router.conf
```

## 5. Switch over

First see what else would be removed, and make sure it's nothing you need:

```bash
apt -s purge network-manager modemmanager | grep '^Purg'    # -s = dry run, removes nothing
```

Then actually remove them:

```bash
sudo systemctl enable systemd-networkd
sudo apt purge network-manager modemmanager
```

ModemManager isn't needed for an `enx…` modem (it's only for `wwan0` ones), and it probes every new serial device
(Arduino, Pico, ...) with AT commands, which can disturb them. Then:

```bash
sudo rm -f /etc/NetworkManager/conf.d/flowernet-dns.conf   # from DNS-SETUP.md step 3, not needed anymore
sudo reboot
```

## 6. Verify

```bash
networkctl                            # both interfaces "routable" or "configured"
ip -br addr                           # modem has an address from DHCP, ethernet has 10.42.0.1/24
sysctl net.ipv4.ip_forward            # = 1
grep nameserver /etc/resolv.conf      # still nothing (networkd doesn't write it without systemd-resolved)
```

Then plug a device into the ethernet port: it gets a `10.42.0.x` address and internet (with PacketUi running).

PacketUi's packet rules must allow DHCP: on the LAN, devices to the router on UDP port `67` (in) and the answers
(out); on the WAN, the router's DHCP renewals to the modem (out, port `67`) and the answers (in).

## Troubleshooting

| Symptom | Cause / fix |
|---------|-------------|
| An interface is `unmanaged` in `networkctl` | Its name doesn't match the `Name=` in its `.network` file. Fix the name, then `sudo networkctl reload`. |
| Modem has no address | `journalctl -b -u systemd-networkd`. If DHCP times out, PacketUi is blocking it (see [step 6](#6-verify)). |
| LAN devices get no address | `ss -ulpn \| grep ':67 '` must show `systemd-networkd`. If it does, PacketUi is blocking DHCP on the LAN. |
| LAN devices have an address but no internet | `sysctl net.ipv4.ip_forward` must be `1`, and `sudo nft list table ip nfbridge_nat` must show the masquerade rule. |
| Boot hangs ~2 minutes at "Wait for Network" | `sudo systemctl disable systemd-networkd-wait-online`. |

## Undo

```bash
sudo apt install network-manager modemmanager
sudo systemctl disable systemd-networkd
sudo rm /etc/systemd/network/10-wan.network /etc/systemd/network/20-lan.network
sudo rm /etc/sysctl.d/90-flowernet-router.conf
sudo reboot
```

Then redo [DNS-SETUP.md](DNS-SETUP.md) step 3, and set the ethernet connection to "Shared to other computers" again.
