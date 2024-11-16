package com.flower.conntrack.whiteblacklist;

import com.flower.conntrack.AddressCheck;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.flower.conntrack.whiteblacklist.AddressFilterList.AddressRecord;
import static com.flower.conntrack.whiteblacklist.AddressFilterList.HostRecord;
import static com.flower.conntrack.whiteblacklist.AddressFilterList.PortRecord;

// TODO: extensive test coverage
public class WhitelistBlacklistConnectionFilter {
    final List<Pair<AddressFilterList, Boolean>> addressLists;

    //Priority 0
    final Map<String, Map<Integer, AddressRecord>> addressRecords;
    //Priority 1
    final Map<String, HostRecord> hostRecords;
    //Priority 2
    final Map<String, Map<Integer, AddressRecord>> wildcardAddressRecords;
    //Priority 3
    final Map<String, HostRecord> wildcardHostRecords;
    //Priority 4
    final Map<Integer, PortRecord> portRecords;

    public WhitelistBlacklistConnectionFilter(List<Pair<AddressFilterList, Boolean>> addressLists) {
        this();
        for (Pair<AddressFilterList, Boolean> listPair : addressLists) {
            addList(listPair.getKey(), listPair.getValue());
        }
    }

    public Map<String, Map<Integer, AddressRecord>> getAddressRecords() {
        return addressRecords;
    }

    public Map<String, HostRecord> getHostRecords() {
        return hostRecords;
    }

    public Map<String, Map<Integer, AddressRecord>> getWildcardAddressRecords() {
        return wildcardAddressRecords;
    }

    public Map<String, HostRecord> getWildcardHostRecords() {
        return wildcardHostRecords;
    }

    public Map<Integer, PortRecord> getPortRecords() { return portRecords; }


    public WhitelistBlacklistConnectionFilter() {
        addressLists = new ArrayList<>();
        addressRecords = new ConcurrentHashMap<>();
        hostRecords = new ConcurrentHashMap<>();
        portRecords = new ConcurrentHashMap<>();
        wildcardAddressRecords = new ConcurrentHashMap<>();
        wildcardHostRecords = new ConcurrentHashMap<>();
    }

    public void addList(AddressFilterList list, boolean overrideExisting) {
        for (AddressRecord addressRecord : list.addressRecords()) {
            addAddressRecord(addressRecord, overrideExisting);
        }
        for (HostRecord hostRecord : list.hostRecords()) {
            addHostRecord(hostRecord, overrideExisting);
        }
        for (PortRecord portRecord : list.portRecords()) {
            addPortRecord(portRecord, overrideExisting);
        }
        for (AddressRecord addressRecord : list.wildcardAddressRecords()) {
            addAddressRecord(addressRecord, overrideExisting);
        }
        for (HostRecord hostRecord : list.wildcardHostRecords()) {
            addHostRecord(hostRecord, overrideExisting);
        }
    }

    @Nullable public AddressRecord addAddressRecord(AddressRecord addressRecord, boolean overrideExisting) {
        Map<Integer, AddressRecord> portMap =
                addressRecord.isWildcard() ?
                        wildcardAddressRecords.computeIfAbsent(addressRecord.dstHost(), k -> new ConcurrentHashMap<>())
                        : addressRecords.computeIfAbsent(addressRecord.dstHost(), k -> new ConcurrentHashMap<>());
        if (overrideExisting) {
            return portMap.put(addressRecord.dstPort(), addressRecord);
        } else {
            return portMap.putIfAbsent(addressRecord.dstPort(), addressRecord);
        }
    }

    @Nullable public HostRecord addHostRecord(HostRecord hostRecord, boolean overrideExisting) {
        final Map<String, HostRecord> hostRecords = hostRecord.isWildcard() ? this.wildcardHostRecords : this.hostRecords;
        if (overrideExisting) {
            return hostRecords.put(hostRecord.dstHost(), hostRecord);
        } else {
            return hostRecords.putIfAbsent(hostRecord.dstHost(), hostRecord);
        }
    }

    @Nullable public PortRecord addPortRecord(PortRecord portRecord, boolean overrideExisting) {
        if (overrideExisting) {
            return portRecords.put(portRecord.dstPort(), portRecord);
        } else {
            return portRecords.putIfAbsent(portRecord.dstPort(), portRecord);
        }
    }

    public @Nullable AddressRecord removeAddressRecord(AddressRecord addressRecord) {
        return removeAddressRecord(addressRecord.dstHost(), addressRecord.dstPort(), addressRecord.isWildcard());
    }

    public @Nullable AddressRecord removeAddressRecord(String host, Integer port, boolean isWildcard) {
        Map<Integer, AddressRecord> portMap = isWildcard ? wildcardAddressRecords.get(host) : addressRecords.get(host);
        if (portMap == null) {
            return null;
        } else {
            // I don't want to remove empty portMaps from addressRecords here to avoid race conditions
            return portMap.remove(port);
        }
    }

    public @Nullable HostRecord removeHostRecord(HostRecord hostRecord) {
        final Map<String, HostRecord> hostRecords = hostRecord.isWildcard() ? this.wildcardHostRecords : this.hostRecords;
        return hostRecords.remove(hostRecord.dstHost());
    }

    public @Nullable PortRecord removePortRecord(PortRecord portRecord) {
        return portRecords.remove(portRecord.dstPort());
    }

    public void clear() {
        addressLists.clear();
        addressRecords.clear();
        hostRecords.clear();
        portRecords.clear();
        wildcardAddressRecords.clear();
        wildcardHostRecords.clear();
    }

    public AddressFilterList getFullList() {
        List<AddressRecord> addressRecords = new ArrayList<>();
        List<HostRecord> hostRecords = new ArrayList<>();
        List<PortRecord> portRecords = new ArrayList<>();

        for (Map.Entry<String, Map<Integer, AddressRecord>> addressEntry : this.addressRecords.entrySet()) {
            for (Map.Entry<Integer, AddressRecord> addressPortEntry : addressEntry.getValue().entrySet()) {
                addressRecords.add(addressPortEntry.getValue());
            }
        }
        for (Map.Entry<String, HostRecord> hostEntry : this.hostRecords.entrySet()) {
            hostRecords.add(hostEntry.getValue());
        }
        for (Map.Entry<Integer, PortRecord> portEntry : this.portRecords.entrySet()) {
            portRecords.add(portEntry.getValue());
        }

        List<AddressRecord> wildcardAddressRecords = new ArrayList<>();
        List<HostRecord> wildcardHostRecords = new ArrayList<>();

        for (Map.Entry<String, Map<Integer, AddressRecord>> addressEntry : this.wildcardAddressRecords.entrySet()) {
            for (Map.Entry<Integer, AddressRecord> addressPortEntry : addressEntry.getValue().entrySet()) {
                wildcardAddressRecords.add(addressPortEntry.getValue());
            }
        }
        for (Map.Entry<String, HostRecord> hostEntry : this.wildcardHostRecords.entrySet()) {
            wildcardHostRecords.add(hostEntry.getValue());
        }

        return ImmutableAddressFilterList.builder()
                .addressRecords(addressRecords)
                .hostRecords(hostRecords)
                .portRecords(portRecords)
                .wildcardAddressRecords(wildcardAddressRecords)
                .wildcardHostRecords(wildcardHostRecords)
                .build();
    }

    public AddressFilterList getLocalUpdatesDiff(List<AddressFilterList> baseLists) {
        WhitelistBlacklistConnectionFilter otherFilter = new WhitelistBlacklistConnectionFilter();
        for (Pair<AddressFilterList, Boolean> filterListEntry : addressLists) {
            otherFilter.addList(filterListEntry.getKey(), filterListEntry.getValue());
        }

        return getNewOrUpdatedRecords(this, otherFilter, true);
    }

    public static AddressFilterList getNewOrUpdatedRecords(WhitelistBlacklistConnectionFilter fromThisFilter,
                                                    WhitelistBlacklistConnectionFilter diffFromThatFilter,
                                                    boolean includeUpdated) {
        List<AddressRecord> addressRecords = new ArrayList<>();
        List<HostRecord> hostRecords = new ArrayList<>();
        List<PortRecord> portRecords = new ArrayList<>();

        for (Map.Entry<String, Map<Integer, AddressRecord>> addressEntry : fromThisFilter.addressRecords.entrySet()) {
            Map<Integer, AddressRecord> addressRecordByPortMap = addressEntry.getValue();
            Map<Integer, AddressRecord> otherMap = diffFromThatFilter.addressRecords.get(addressEntry.getKey());

            for (Map.Entry<Integer, AddressRecord> addressPortEntry : addressRecordByPortMap.entrySet()) {
                if (otherMap == null) {
                    addressRecords.add(addressPortEntry.getValue());
                } else {
                    AddressRecord otherRecord = otherMap.get(addressPortEntry.getKey());
                    if (otherRecord == null) {
                        addressRecords.add(addressPortEntry.getValue());
                    } else if (includeUpdated) {
                        if (!addressPortEntry.getValue().equals(otherRecord)) {
                            addressRecords.add(addressPortEntry.getValue());
                        }
                    }
                }
            }
        }
        for (Map.Entry<String, HostRecord> hostEntry : fromThisFilter.hostRecords.entrySet()) {
            HostRecord otherRecord = diffFromThatFilter.hostRecords.get(hostEntry.getKey());
            if (otherRecord == null) {
                hostRecords.add(hostEntry.getValue());
            } else if (includeUpdated) {
                if (!hostEntry.getValue().equals(otherRecord)) {
                    hostRecords.add(hostEntry.getValue());
                }
            }
        }
        for (Map.Entry<Integer, PortRecord> portEntry : fromThisFilter.portRecords.entrySet()) {
            PortRecord otherRecord = diffFromThatFilter.portRecords.get(portEntry.getKey());
            if (otherRecord == null) {
                portRecords.add(portEntry.getValue());
            } else if (includeUpdated) {
                if (!portEntry.getValue().equals(otherRecord)) {
                    portRecords.add(portEntry.getValue());
                }
            }
        }

        List<AddressRecord> wildcardAddressRecords = new ArrayList<>();
        List<HostRecord> wildcardHostRecords = new ArrayList<>();

        for (Map.Entry<String, Map<Integer, AddressRecord>> wildcardAddressEntry : fromThisFilter.wildcardAddressRecords.entrySet()) {
            Map<Integer, AddressRecord> addressRecordByPortMap = wildcardAddressEntry.getValue();
            Map<Integer, AddressRecord> otherMap = diffFromThatFilter.wildcardAddressRecords.get(wildcardAddressEntry.getKey());

            for (Map.Entry<Integer, AddressRecord> addressPortEntry : addressRecordByPortMap.entrySet()) {
                if (otherMap == null) {
                    wildcardAddressRecords.add(addressPortEntry.getValue());
                } else {
                    AddressRecord otherRecord = otherMap.get(addressPortEntry.getKey());
                    if (otherRecord == null) {
                        wildcardAddressRecords.add(addressPortEntry.getValue());
                    } else if (includeUpdated) {
                        if (!addressPortEntry.getValue().equals(otherRecord)) {
                            wildcardAddressRecords.add(addressPortEntry.getValue());
                        }
                    }
                }
            }
        }
        for (Map.Entry<String, HostRecord> hostEntry : fromThisFilter.wildcardHostRecords.entrySet()) {
            HostRecord otherRecord = diffFromThatFilter.wildcardHostRecords.get(hostEntry.getKey());
            if (otherRecord == null) {
                wildcardHostRecords.add(hostEntry.getValue());
            } else if (includeUpdated) {
                if (!hostEntry.getValue().equals(otherRecord)) {
                    wildcardHostRecords.add(hostEntry.getValue());
                }
            }
        }

        return ImmutableAddressFilterList.builder()
                .addressRecords(addressRecords)
                .hostRecords(hostRecords)
                .portRecords(portRecords)
                .wildcardAddressRecords(wildcardAddressRecords)
                .wildcardHostRecords(wildcardHostRecords)
                .build();
    }

    @Nullable public AddressCheck getRecordRule(String dstHost, int dstPort) {
        // Priority 0 - exact hostname/port match
        Map<Integer, AddressRecord> portMap = addressRecords.get(dstHost);
        if (portMap != null) {
            AddressRecord record = portMap.get(dstPort);
            if (record != null) {
                if (record.filterType() == FilterType.WHITELIST) {
                    return AddressCheck.CONNECTION_ALLOWED;
                } else if (record.filterType() == FilterType.BLACKLIST) {
                    return AddressCheck.CONNECTION_PROHIBITED;
                } else {
                    throw new IllegalArgumentException("Unknown filter type: " + record.filterType());
                }
            }
        }

        // Priority 1 - hostname match (without port)
        HostRecord hostRecord = hostRecords.get(dstHost);
        if (hostRecord != null) {
            if (hostRecord.filterType() == FilterType.WHITELIST) {
                return AddressCheck.CONNECTION_ALLOWED;
            } else if (hostRecord.filterType() == FilterType.BLACKLIST) {
                return AddressCheck.CONNECTION_PROHIBITED;
            } else {
                throw new IllegalArgumentException("Unknown filter type: " + hostRecord.filterType());
            }
        }

        // Priority 2 - wildcard hostname/port match
        Map<Integer, AddressRecord> wildcardPortMap = getWildcardResource(dstHost, wildcardAddressRecords);
        if (wildcardPortMap != null) {
            AddressRecord record = wildcardPortMap.get(dstPort);

            if (record != null) {
                if (record.filterType() == FilterType.WHITELIST) {
                    return AddressCheck.CONNECTION_ALLOWED;
                } else if (record.filterType() == FilterType.BLACKLIST) {
                    return AddressCheck.CONNECTION_PROHIBITED;
                } else {
                    throw new IllegalArgumentException("Unknown filter type: " + record.filterType());
                }
            }
        }

        // Priority 3 - wildcard hostname match (without port)
        HostRecord wildcardHostRecord = getWildcardResource(dstHost, wildcardHostRecords);
        if (wildcardHostRecord != null) {
            if (wildcardHostRecord.filterType() == FilterType.WHITELIST) {
                return AddressCheck.CONNECTION_ALLOWED;
            } else if (wildcardHostRecord.filterType() == FilterType.BLACKLIST) {
                return AddressCheck.CONNECTION_PROHIBITED;
            } else {
                throw new IllegalArgumentException("Unknown filter type: " + wildcardHostRecord.filterType());
            }
        }

        // Priority 4 - port match (without hostname)
        PortRecord portRecord = portRecords.get(dstPort);
        if (portRecord != null) {
            if (portRecord.filterType() == FilterType.WHITELIST) {
                return AddressCheck.CONNECTION_ALLOWED;
            } else if (portRecord.filterType() == FilterType.BLACKLIST) {
                return AddressCheck.CONNECTION_PROHIBITED;
            } else {
                throw new IllegalArgumentException("Unknown filter type: " + portRecord.filterType());
            }
        }

        return null;
    }

    private @Nullable <T> T getWildcardResource(String text, Map<String, T> wildcardAddressRecords) {
        for (Map.Entry<String, T> entry : wildcardAddressRecords.entrySet()) {
            String pattern = entry.getKey();
            if (WildcardMatcher.isMatch(text, pattern)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
