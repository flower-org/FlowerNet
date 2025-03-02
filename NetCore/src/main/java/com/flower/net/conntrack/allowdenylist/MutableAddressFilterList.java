package com.flower.net.conntrack.allowdenylist;

import java.util.List;

/** TODO: unused - remove */
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
    @Override
    public List<AddressRecord> wildcardAddressRecords() {
        return List.of();
    }

    @Override
    public List<HostRecord> wildcardHostRecords() {
        return List.of();
    }
}
