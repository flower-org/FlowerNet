Regular traffic:

→ open_conn | request_id | host_len | host | port

← open_conn_ack | request_id | conn_id

→ ← data | conn_id | data_len | data

→ ← close_conn | conn_id | close_code | reason_len | reason

---

Address resolution:

→ resolve | request_id | resolution_details | name_len | name_to_resolve

    resolution_details = type | server_addr_len | server_addr | server_port | cert_match_type | cert_len | cert
        type = DNS_UDP, DNS_TLS, DNS_HTTPS, LOCAL_NAMESERVER, LOCAL_OS
        cert_match_type = NONE, ROOT, EXACT

    resolution_details = DNS_UDP | server_addr_len | server_addr | server_port
    resolution_details = DNS_TLS | server_addr_len | server_addr | server_port | cert_match_type | cert_len | cert
    resolution_details = DNS_HTTPS | server_addr_len | server_addr | server_port | cert_match_type | cert_len | cert
    resolution_details = LOCAL_NAMESERVER
    resolution_details = LOCAL_OS

← resolve_response | request_id | name_len | name_to_resolve | success | ip4_count | ip4[] | ip_6_count | ip6[]

← resolve_response | request_id | name_len | name_to_resolve | error_code | error_len | error_text

---

Possible Error codes (Socks5)
-    0: SUCCESS
-    1: FAILURE
-    2: FORBIDDEN
-    3: NETWORK_UNREACHABLE
-    4: HOST_UNREACHABLE
-    5: CONNECTION_REFUSED
-    6: TTL_EXPIRED
-    7: COMMAND_UNSUPPORTED
-    8: ADDRESS_UNSUPPORTED

---

Ideas (possibly bad):
- single_conn? - virtual connection closes, physical connection closes: useful for nodes in the middle of the chain.
- packet#, crc? - in data messages prob redundant since it's over TCP, test
- run the protocol on QUIC for speedup? - really low prio
