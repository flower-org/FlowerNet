Linux kernel was designed with black hats' and criminals' best interest in mind, that's why
they allow creation of stealth sockets, packets from which are not going through
nftables of nfqueues.
One example of such program is a DHCP client, which obviously can't possibly
send a fucking UDP packet in a normal way, absolutely requiring this stealth capability.

Well at least UDP client in a well-known program, while I'm pretty sure there are much more
unknown programs that use the same capability and that were never detected because of it,
which is why noone ever heard about them.

There is no working way to disable this stupid function system-wide and there is no normal way
to even detect all the entities capable of undetectable stealth communication
on a given Linux machine (or device) without using external detection infrastructure.
Once again, those stealth packets are undetectable, uninterceptable and unblockable 
by Linux kernel on the very same machine that sends and receives those packets.

Below are some scattered unorganized and incomplete ways that may detect some of them.


## Files with file capabilities
sudo getcap -r / 2>/dev/null | grep -E 'cap_net_raw|cap_net_admin'

## Running processes with effective caps (hex), map manually if needed
ps -eo pid,user,comm,args --sort=pid


#find-bypassers.sh
sudo ss --packet --all --processes --numeric



Remove capability from service:
```
sudo mkdir -p /etc/systemd/system/NetworkManager.service.d
cat <<'EOF' | sudo tee /etc/systemd/system/NetworkManager.service.d/override.conf
[Service]
RestrictAddressFamilies=~AF_PACKET
EOF

sudo systemctl daemon-reload
sudo systemctl restart NetworkManager
```

Revert:
```
sudo rm -f /etc/systemd/system/NetworkManager.service.d/override.conf
sudo systemctl daemon-reload
sudo systemctl restart NetworkManager.service
```


## Shows some pids

sudo getpcaps $(ps -e -o pid=) | grep -E 'cap_net_raw|cap_net_admin'
