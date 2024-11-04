package com.flower.conntrack.whiteblacklist;

import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
public interface AddressFilterList {
    @Value.Immutable
    interface AddressRecord {
        FilterType filterType();
        String dstHost();
        Integer dstPort();
    }

    @Value.Immutable
    interface HostRecord {
        FilterType filterType();
        String dstHost();
    }

    @Value.Immutable
    interface PortRecord {
        FilterType filterType();
        Integer dstPort();
    }

    List<AddressRecord> addressRecords();
    List<HostRecord> hostRecords();
    List<PortRecord> portRecords();
}
