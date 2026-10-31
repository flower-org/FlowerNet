package com.flower.net.conntrack;

/** Netfilter chain a packet was seen on. Connection-level callers (e.g. SOCKS) have none and pass null. */
public enum TrafficDirection {
    IN,
    OUT,
    FORWARD
}
