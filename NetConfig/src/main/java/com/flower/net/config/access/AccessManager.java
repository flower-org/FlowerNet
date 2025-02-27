package com.flower.net.config.access;

import java.net.InetAddress;

/**
 * Recommended access control rule priorities.
 * Access checks by addresses are orthogonal to access checks by name (2 different methods).
 * <p>
 * P0: Address + port; Name + port
 * P1: Address + port range; Name + port range
 * P2: Address; Name
 * P3: Address range + port; Wildcard name + port
 * P4: Address range + port range; Wildcard name + port range
 * P5: Address range; Wildcard name
 * P6: Port
 * P7: Port range
 * <p>
 * Access priorities - conflicting rules of equal priority are resolved as follows.
 * P0: DENY
 * P1: ALLOW
 */
public interface AccessManager {
    Access accessCheck(InetAddress address, int port);
    Access accessCheck(String name, int port);
}
