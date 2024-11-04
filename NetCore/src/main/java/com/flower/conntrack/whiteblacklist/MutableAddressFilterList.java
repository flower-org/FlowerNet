package com.flower.conntrack.whiteblacklist;

import java.util.List;

public class MutableAddressFilterList implements AddressFilterList {
    @Override
    public List<AddressRecord> addressRecords() {
        return List.of();
    }

    @Override
    public List<HostRecord> hostRecords() {
        return List.of();
    }

    @Override
    public List<PortRecord> portRecords() {
        return List.of();
    }
}
