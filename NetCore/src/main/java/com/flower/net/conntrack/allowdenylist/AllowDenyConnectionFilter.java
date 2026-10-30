package com.flower.net.conntrack.allowdenylist;

import com.flower.net.config.access.Access;
import com.flower.net.utils.WildcardTrie;
import com.flower.net.utils.WildcardTrie.Match;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.flower.net.conntrack.allowdenylist.AddressFilterList.AddressRecord;
import static com.flower.net.conntrack.allowdenylist.AddressFilterList.HostRecord;
import static com.flower.net.conntrack.allowdenylist.AddressFilterList.PortRecord;

/**
 * Lookup priority follows "most specific rule wins" (see {@link #getRecordRule}):
 * host+port (exact, then wildcard) > host (exact, then wildcard) > port.
 * <p>
 * Wildcard rules (priorities 1 and 3) are indexed in two {@link WildcardTrie}s, one per record type.
 * When several wildcard patterns match,
 * the most specific one wins (see {@link #compareSpecificity}); on a specificity tie DENY wins.
 * <p>
 * A read/write lock keeps the record maps and the tries consistent: mutations take the write lock,
 * lookups take the read lock and so see a consistent state while running concurrently with each other.
 * Do not mutate the maps returned by getters directly.
 * {@link #getNewOrUpdatedRecords} takes no lock (it spans two filters), so its result is best-effort.
 */
public class AllowDenyConnectionFilter {
    //Priority 0
    final ConcurrentHashMap<String, ConcurrentHashMap<Integer, AddressRecord>> addressRecords;
    //Priority 1
    final ConcurrentHashMap<String, ConcurrentHashMap<Integer, AddressRecord>> wildcardAddressRecords;
    //Priority 2
    final ConcurrentHashMap<String, HostRecord> hostRecords;
    //Priority 3
    final ConcurrentHashMap<String, HostRecord> wildcardHostRecords;
    //Priority 4
    final ConcurrentHashMap<Integer, PortRecord> portRecords;
    // Index of wildcardAddressRecords and wildcardHostRecords
    final WildcardTrie<AddressRecord> wildcardAddressTrie;
    final WildcardTrie<HostRecord> wildcardHostTrie;
    final ReadWriteLock lock = new ReentrantReadWriteLock();

    public AllowDenyConnectionFilter(List<Pair<AddressFilterList, Boolean>> addressLists) {
        this();
        for (Pair<AddressFilterList, Boolean> listPair : addressLists) {
            addList(listPair.getKey(), listPair.getValue());
        }
    }

    public ConcurrentHashMap<String, ConcurrentHashMap<Integer, AddressRecord>> getAddressRecords() {
        return addressRecords;
    }

    public ConcurrentHashMap<String, HostRecord> getHostRecords() {
        return hostRecords;
    }

    public ConcurrentHashMap<String, ConcurrentHashMap<Integer, AddressRecord>> getWildcardAddressRecords() {
        return wildcardAddressRecords;
    }

    public ConcurrentHashMap<String, HostRecord> getWildcardHostRecords() {
        return wildcardHostRecords;
    }

    public ConcurrentHashMap<Integer, PortRecord> getPortRecords() { return portRecords; }


    public AllowDenyConnectionFilter() {
        addressRecords = new ConcurrentHashMap<>();
        hostRecords = new ConcurrentHashMap<>();
        portRecords = new ConcurrentHashMap<>();
        wildcardAddressRecords = new ConcurrentHashMap<>();
        wildcardHostRecords = new ConcurrentHashMap<>();
        wildcardAddressTrie = new WildcardTrie<>();
        wildcardHostTrie = new WildcardTrie<>();
    }

    public boolean isListOverrideRequired(AddressFilterList list) {
        return countListOverrides(list) > 0;
    }

    /** Number of records in the list that conflict with existing records (same target, different access). */
    public int countListOverrides(AddressFilterList list) {
        lock.readLock().lock();
        try {
            int count = 0;
            for (AddressRecord addressRecord : list.addressRecords()) {
                if (isAddressRecordOverrideRequired(addressRecord)) { count++; }
            }
            for (HostRecord hostRecord : list.hostRecords()) {
                if (isHostRecordOverrideRequired(hostRecord)) { count++; }
            }
            for (PortRecord portRecord : list.portRecords()) {
                if (isPortRecordOverrideRequired(portRecord)) { count++; }
            }
            for (AddressRecord addressRecord : list.wildcardAddressRecords()) {
                if (isAddressRecordOverrideRequired(addressRecord)) { count++; }
            }
            for (HostRecord hostRecord : list.wildcardHostRecords()) {
                if (isHostRecordOverrideRequired(hostRecord)) { count++; }
            }
            return count;
        } finally {
            lock.readLock().unlock();
        }
    }

    boolean isAddressRecordOverrideRequired(AddressRecord addressRecord) {
        Map<Integer, AddressRecord> portMap =
                addressRecord.isWildcard() ?
                        wildcardAddressRecords.get(addressRecord.dstHost())
                        : addressRecords.get(addressRecord.dstHost());
        if (portMap == null) { return false; }
        AddressRecord oldRecord = portMap.get(addressRecord.dstPort());
        return oldRecord != null && !AddressRecord.recordsEqual(addressRecord, oldRecord);
    }

    boolean isHostRecordOverrideRequired(HostRecord hostRecord) {
        final Map<String, HostRecord> hostRecords = hostRecord.isWildcard() ? this.wildcardHostRecords : this.hostRecords;
        HostRecord oldRecord = hostRecords.get(hostRecord.dstHost());
        return oldRecord != null && !HostRecord.recordsEqual(hostRecord, oldRecord);
    }

    boolean isPortRecordOverrideRequired(PortRecord portRecord) {
        PortRecord oldRecord = portRecords.get(portRecord.dstPort());
        return oldRecord != null && !PortRecord.recordsEqual(portRecord, oldRecord);
    }

    public void addList(AddressFilterList list, boolean overrideExisting) {
        lock.writeLock().lock();
        try {
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
    
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Nullable public AddressRecord addAddressRecord(AddressRecord addressRecord, boolean overrideExisting) {
        lock.writeLock().lock();
        try {
            Map<Integer, AddressRecord> portMap =
                    addressRecord.isWildcard() ?
                            wildcardAddressRecords.computeIfAbsent(addressRecord.dstHost(), k -> new ConcurrentHashMap<>())
                            : addressRecords.computeIfAbsent(addressRecord.dstHost(), k -> new ConcurrentHashMap<>());
            if (overrideExisting) {
                AddressRecord oldRecord = portMap.put(addressRecord.dstPort(), addressRecord);
                if (addressRecord.isWildcard()) {
                    if (oldRecord != null) {
                        wildcardAddressTrie.removePatternValue(oldRecord.dstHost(), oldRecord);
                    }
                    wildcardAddressTrie.addPatternValue(addressRecord.dstHost(), addressRecord);
                }
                return oldRecord;
            } else {
                AddressRecord oldRecord = portMap.putIfAbsent(addressRecord.dstPort(), addressRecord);
                if (oldRecord == null && addressRecord.isWildcard()) {
                    wildcardAddressTrie.addPatternValue(addressRecord.dstHost(), addressRecord);
                }
                return oldRecord;
            }
    
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Nullable public HostRecord addHostRecord(HostRecord hostRecord, boolean overrideExisting) {
        lock.writeLock().lock();
        try {
            final Map<String, HostRecord> hostRecords = hostRecord.isWildcard() ? this.wildcardHostRecords : this.hostRecords;
            if (overrideExisting) {
                HostRecord oldRecord = hostRecords.put(hostRecord.dstHost(), hostRecord);
                if (hostRecord.isWildcard()) {
                    if (oldRecord != null) {
                        wildcardHostTrie.removePatternValue(oldRecord.dstHost(), oldRecord);
                    }
                    wildcardHostTrie.addPatternValue(hostRecord.dstHost(), hostRecord);
                }
                return oldRecord;
            } else {
                HostRecord oldRecord = hostRecords.putIfAbsent(hostRecord.dstHost(), hostRecord);
                if (oldRecord == null && hostRecord.isWildcard()) {
                    wildcardHostTrie.addPatternValue(hostRecord.dstHost(), hostRecord);
                }
                return oldRecord;
            }
    
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Nullable public PortRecord addPortRecord(PortRecord portRecord, boolean overrideExisting) {
        lock.writeLock().lock();
        try {
            if (overrideExisting) {
                return portRecords.put(portRecord.dstPort(), portRecord);
            } else {
                return portRecords.putIfAbsent(portRecord.dstPort(), portRecord);
            }
    
        } finally {
            lock.writeLock().unlock();
        }
    }

    public @Nullable AddressRecord removeAddressRecord(AddressRecord addressRecord) {
        return removeAddressRecord(addressRecord.dstHost(), addressRecord.dstPort(), addressRecord.isWildcard());
    }

    public @Nullable AddressRecord removeAddressRecord(String host, Integer port, boolean isWildcard) {
        lock.writeLock().lock();
        try {
            Map<Integer, AddressRecord> portMap = isWildcard ? wildcardAddressRecords.get(host) : addressRecords.get(host);
            if (portMap == null) {
                return null;
            } else {
                // I don't want to remove empty portMaps from addressRecords here to avoid race conditions
                AddressRecord oldRecord = portMap.remove(port);
                if (oldRecord != null && isWildcard) {
                    wildcardAddressTrie.removePatternValue(oldRecord.dstHost(), oldRecord);
                }
                return oldRecord;
            }
    
        } finally {
            lock.writeLock().unlock();
        }
    }

    public @Nullable HostRecord removeHostRecord(HostRecord hostRecord) {
        lock.writeLock().lock();
        try {
            final Map<String, HostRecord> hostRecords = hostRecord.isWildcard() ? this.wildcardHostRecords : this.hostRecords;
            HostRecord oldRecord = hostRecords.remove(hostRecord.dstHost());
            if (oldRecord != null && hostRecord.isWildcard()) {
                wildcardHostTrie.removePatternValue(oldRecord.dstHost(), oldRecord);
            }
            return oldRecord;
    
        } finally {
            lock.writeLock().unlock();
        }
    }

    public @Nullable PortRecord removePortRecord(PortRecord portRecord) {
        lock.writeLock().lock();
        try {
            return portRecords.remove(portRecord.dstPort());
    
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            addressRecords.clear();
            hostRecords.clear();
            portRecords.clear();
            wildcardAddressRecords.clear();
            wildcardHostRecords.clear();
            wildcardAddressTrie.clear();
            wildcardHostTrie.clear();
    
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void clearFilterType(Access access) {
        lock.writeLock().lock();
        try {
            for (Map.Entry<String, ConcurrentHashMap<Integer, AddressRecord>> addressRecord : addressRecords.entrySet()) {
                ConcurrentHashMap<Integer, AddressRecord> portMap = addressRecord.getValue();
                for (Map.Entry<Integer, AddressRecord> portRecord : portMap.entrySet()) {
                    if (portRecord.getValue().access() == access) {
                        portMap.remove(portRecord.getKey());
                    }
                }
            }
            for (Map.Entry<String, HostRecord> hostRecord : hostRecords.entrySet()) {
                if (hostRecord.getValue().access() == access) {
                    hostRecords.remove(hostRecord.getKey());
                }
            }
            for (Map.Entry<Integer, PortRecord> portRecord : portRecords.entrySet()) {
                if (portRecord.getValue().access() == access) {
                    portRecords.remove(portRecord.getKey());
                }
            }
            for (Map.Entry<String, ConcurrentHashMap<Integer, AddressRecord>> wildcardAddressRecord : wildcardAddressRecords.entrySet()) {
                ConcurrentHashMap<Integer, AddressRecord> portMap = wildcardAddressRecord.getValue();
                for (Map.Entry<Integer, AddressRecord> portRecord : portMap.entrySet()) {
                    AddressRecord record = portRecord.getValue();
                    if (record.access() == access && portMap.remove(portRecord.getKey(), record)) {
                        wildcardAddressTrie.removePatternValue(record.dstHost(), record);
                    }
                }
            }
            for (Map.Entry<String, HostRecord> wildcardHostRecord : wildcardHostRecords.entrySet()) {
                HostRecord record = wildcardHostRecord.getValue();
                if (record.access() == access && wildcardHostRecords.remove(wildcardHostRecord.getKey(), record)) {
                    wildcardHostTrie.removePatternValue(record.dstHost(), record);
                }
            }
    
        } finally {
            lock.writeLock().unlock();
        }
    }


    public AddressFilterList getFullList() {
        lock.readLock().lock();
        try {
            List<AddressRecord> addressRecords = new ArrayList<>();
            List<HostRecord> hostRecords = new ArrayList<>();
            List<PortRecord> portRecords = new ArrayList<>();

            for (Map.Entry<String, ConcurrentHashMap<Integer, AddressRecord>> addressEntry : this.addressRecords.entrySet()) {
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

            for (Map.Entry<String, ConcurrentHashMap<Integer, AddressRecord>> addressEntry : this.wildcardAddressRecords.entrySet()) {
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
    
        } finally {
            lock.readLock().unlock();
        }
    }

    /*public AddressFilterList getLocalUpdatesDiff() {
        AllowDenyConnectionFilter otherFilter = new AllowDenyConnectionFilter();
        for (Pair<AddressFilterList, Boolean> filterListEntry : addressLists) {
            otherFilter.addList(filterListEntry.getKey(), filterListEntry.getValue());
        }

        return getNewOrUpdatedRecords(this, otherFilter, true);
    }*/

/*    public AddressFilterList getUpdatesDiff(List<AddressFilterList> baseLists) {
        AllowDenyConnectionFilter otherFilter = new AllowDenyConnectionFilter();
        for (AddressFilterList filterList : baseLists) {
            otherFilter.addList(filterList, false);
        }

        return getNewOrUpdatedRecords(this, otherFilter, true);
    }*/

    public static AddressFilterList getNewOrUpdatedRecords(AllowDenyConnectionFilter fromThisFilter,
                                                    AllowDenyConnectionFilter diffFromThatFilter,
                                                    boolean includeUpdated) {
        List<AddressRecord> addressRecords = new ArrayList<>();
        List<HostRecord> hostRecords = new ArrayList<>();
        List<PortRecord> portRecords = new ArrayList<>();

        for (Map.Entry<String, ConcurrentHashMap<Integer, AddressRecord>> addressEntry : fromThisFilter.addressRecords.entrySet()) {
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

        for (Map.Entry<String, ConcurrentHashMap<Integer, AddressRecord>> wildcardAddressEntry : fromThisFilter.wildcardAddressRecords.entrySet()) {
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

    @Nullable public Access getRecordRule(String dstHost, int dstPort) {
        lock.readLock().lock();
        try {
            // Priority 0 - exact hostname/port match
            // Most specific rule wins: any host+port rule beats any host-only rule, which beats a port-only rule
            Map<Integer, AddressRecord> portMap = addressRecords.get(dstHost);
            if (portMap != null) {
                AddressRecord record = portMap.get(dstPort);
                if (record != null) {
                    if (record.access() == Access.ALLOW) {
                        return Access.ALLOW;
                    } else if (record.access() == Access.DENY) {
                        return Access.DENY;
                    } else {
                        throw new IllegalArgumentException("Unknown filter type: " + record.access());
                    }
                }
            }

            // Priority 1 - wildcard hostname/port match (most specific pattern wins)
            Access wildcardAddressAccess = resolveMostSpecific(wildcardAddressTrie.matchText(dstHost),
                    record -> record.dstPort() == dstPort, AddressRecord::access);
            if (wildcardAddressAccess != null) {
                return wildcardAddressAccess;
            }

            // Priority 2 - exact hostname match (without port)
            HostRecord hostRecord = hostRecords.get(dstHost);
            if (hostRecord != null) {
                if (hostRecord.access() == Access.ALLOW) {
                    return Access.ALLOW;
                } else if (hostRecord.access() == Access.DENY) {
                    return Access.DENY;
                } else {
                    throw new IllegalArgumentException("Unknown filter type: " + hostRecord.access());
                }
            }

            // Priority 3 - wildcard hostname match (without port, most specific pattern wins)
            Access wildcardHostAccess = resolveMostSpecific(wildcardHostTrie.matchText(dstHost),
                    record -> true, HostRecord::access);
            if (wildcardHostAccess != null) {
                return wildcardHostAccess;
            }

            // Priority 4 - port match (without hostname)
            PortRecord portRecord = portRecords.get(dstPort);
            if (portRecord != null) {
                if (portRecord.access() == Access.ALLOW) {
                    return Access.ALLOW;
                } else if (portRecord.access() == Access.DENY) {
                    return Access.DENY;
                } else {
                    throw new IllegalArgumentException("Unknown filter type: " + portRecord.access());
                }
            }

            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /** True if a host+port rule (exact or wildcard host) matches the destination, i.e. it's a known server. */
    public boolean hasAddressRecord(String dstHost, int dstPort) {
        lock.readLock().lock();
        try {
            Map<Integer, AddressRecord> portMap = addressRecords.get(dstHost);
            if (portMap != null && portMap.containsKey(dstPort)) {
                return true;
            }
            for (Match<AddressRecord> match : wildcardAddressTrie.matchText(dstHost)) {
                for (AddressRecord record : match.values()) {
                    if (record.dstPort() == dstPort) {
                        return true;
                    }
                }
            }
            return false;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * @return access of the applicable records under the most specific matching pattern; DENY wins on a tie.
     *         Null if no matching pattern has applicable records.
     */
    static <T> @Nullable Access resolveMostSpecific(List<Match<T>> matches, Predicate<T> isApplicable,
                                                   Function<T, Access> accessOf) {
        @Nullable Match<T> best = null;
        boolean hasAllow = false;
        boolean hasDeny = false;
        for (Match<T> match : matches) {
            boolean matchAllow = false;
            boolean matchDeny = false;
            for (T record : match.values()) {
                if (!isApplicable.test(record)) { continue; }
                if (accessOf.apply(record) == Access.DENY) {
                    matchDeny = true;
                } else {
                    matchAllow = true;
                }
            }
            if (!matchAllow && !matchDeny) { continue; }
            int cmp = best == null ? 1 : compareSpecificity(match, best);
            if (cmp > 0) {
                best = match;
                hasAllow = matchAllow;
                hasDeny = matchDeny;
            } else if (cmp == 0) {
                hasAllow |= matchAllow;
                hasDeny |= matchDeny;
            }
        }
        if (hasDeny) { return Access.DENY; }
        if (hasAllow) { return Access.ALLOW; }
        return null;
    }

    /**
     * Specificity, in order of precedence:
     * more literal characters wins; then more '?' wins (a '?' is narrower than a '*');
     * then fewer '*' runs wins (an extra '*' anywhere can only widen a pattern).
     * @return positive if match1's pattern is more specific than match2's, negative if less, 0 if equally specific.
     */
    static int compareSpecificity(Match<?> match1, Match<?> match2) {
        int cmp = Integer.compare(match1.literalCount(), match2.literalCount());
        if (cmp != 0) { return cmp; }
        cmp = Integer.compare(match1.anyCharCount(), match2.anyCharCount());
        if (cmp != 0) { return cmp; }
        return Integer.compare(match2.starRunCount(), match1.starRunCount());
    }
}
