Flower Network stack.
Probably not what you're looking for.

To build jars, run:
```
gradle shadowJar
```

Абстрактно: 
- если SOCKSs это SOCKS5 over TLS,

- то SOCKS+ это SOCKS5 over QUIC,
    - который может инкапсулировать TCP в streams
    - UDP в Datagram, без потери семантики и без субоптимального UDP Associatte
    - полный мультиплекс, будет так же хорош по производительности, как и любой VPN-протокол.
    - и семантически, как и SOCKS5s, он будет совместим со сверх-распространенным SOCKS5 протоколом, что позволит поддерживать огромное количество существующих приложений через локальный SOCKS5-преоразователь (как мы уже и делаем с SOCKS5s)
