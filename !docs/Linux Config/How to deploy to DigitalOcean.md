# How to deploy on DigitalOcean

1. Create -> droplet

2. Choose an image -> Solutions -> Docker

3. Choose a Droplet Plan -> Regular with 25 GB SSD

4. SSH key:
e.g.
```
ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQC34DSWeROdlDjFIPBF7UML4HdxRTak5oVt3KWJvykgAHCpLLeDupRO4TAzpp35jX6CGprAgjGNzNrKXbiS5y95givuqNYagwy0E1ee4/CxVhq7SRVlDuPDKBYM1EANFxkidoYtE1xppibHUuOSc08SiLeDoFxvNf3IddRMLJoFPEOt82SyWWTF1abA8s7298HMJdDWF6+2iu8e6MeLmGx1dFzyEDVxQDrI6kCO0Z5bBrN64gBTRRrY0hIVXZkA8uuFmM2nku5kBzrvYe3x+5+QEORY0CNCqatUzcVzZeCnmG7JDnz5hw9oqS9IdA4zaN/x4DFAVTaXjnb/7+gPRloX
```

5. Startup script:
```
#!/bin/bash
docker run -d \
    --restart unless-stopped \
    -p 8443:8443 \
    docker.io/johnamirov83/socks5s:cae91e3cb01065506d4c18560729b599a6e014d3
```

5. docker ps / docker logs



P.S. to see startup script:
cat /var/lib/cloud/data/user-data.txt
# or
cat /var/lib/cloud/instance/user-data.txt

