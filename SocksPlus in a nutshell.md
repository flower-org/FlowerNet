→ open_conn | conn_id | host_length | host | port (idempotent)

← open_conn_ack | conn_id (idempotent)

→ ← incoming_data | conn_id | data_length | data (packet#, crc? - prob redundant since it's over TCP, test)

→ ← close_conn | conn_id | close_code | reason_length | reason (idempotent)


Maybe:
single_conn? - virtual connection closes, physical connection closes: useful for nodes in the middle of the chain.  
remove_tls? - for speed-up, but might be impractical.
