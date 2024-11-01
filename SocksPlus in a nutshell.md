→ open_conn | conn_id | host_length | host | port (idempotent)

→ ← incoming_data | conn_id | data_length | data (packet#, crc? - prob redundant since it's over TCP, test)

→ ← close_conn | conn_id | close_code | reason_length | reason (idempotent)
