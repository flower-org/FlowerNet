package com.flower.conntrack.whiteblacklist;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableAddressFilterList.class)
@JsonDeserialize(as = ImmutableAddressFilterList.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface AddressFilterList {
    @Value.Immutable
    @JsonSerialize(as = ImmutableAddressRecord.class)
    @JsonDeserialize(as = ImmutableAddressRecord.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    interface AddressRecord {
        FilterType filterType();
        String dstHost();
        Integer dstPort();
    }

    @Value.Immutable
    @JsonSerialize(as = ImmutableHostRecord.class)
    @JsonDeserialize(as = ImmutableHostRecord.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    interface HostRecord {
        FilterType filterType();
        String dstHost();
    }

    @Value.Immutable
    @JsonSerialize(as = ImmutablePortRecord.class)
    @JsonDeserialize(as = ImmutablePortRecord.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    interface PortRecord {
        FilterType filterType();
        Integer dstPort();
    }

    List<AddressRecord> addressRecords();
    List<HostRecord> hostRecords();
    List<PortRecord> portRecords();
}
