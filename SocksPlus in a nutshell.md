→ open_conn | conn_id | host_length | host | port (idempotent)

← open_conn_ack | conn_id (idempotent)

→ ← incoming_data | conn_id | data_length | data (packet#, crc? - prob redundant since it's over TCP, test)

→ ← close_conn | conn_id | close_code | reason_length | reason (idempotent)

---

+ DNS resolution - (implementable spoofer DNS (like a special 192.169.*.*))

→ resolve_dns | request_id | dns_details | host_length | host
    dns_details = type | server_host_length | server_host | server_port
        type = SELF, UDP, DoTLS, DoHTTPS

← dns_response | ip4_count | ip4 | ip_6_count | ip6
