Debian no longer supports /etc/rc.local, so we need to create a systemd service for that



Create a new systemd service file for rc-local:
```
sudo nano /etc/systemd/system/rc-local.service
```

Add the following content to the service file:
```
[Unit]
Description=/etc/rc.local Compatibility
ConditionPathExists=/etc/nf-iptables.sh
After=network.target
[Service]
Type=forking
ExecStart=/etc/nf-iptables.sh
TimeoutSec=0
StandardOutput=tty
RemainAfterExit=yes
SysVStartPriority=99
[Install]
WantedBy=multi-user.target
```

Save and close the file.

Step 3: Enable the Service
```
sudo systemctl daemon-reload
```

Enable the rc-local service so it runs at startup:

```
sudo systemctl enable rc-local
```

Start the service:
```
sudo systemctl start rc-local
```

Verify the status of the service to ensure it’s running without errors:
```
sudo systemctl status rc-local
```

If everything is set up correctly, /etc/nf-iptables.sh will now run at startup as it did in older versions of Debian.

Reboot your system to test if /etc/nf-iptables.sh runs on startup:

```
sudo reboot
```

