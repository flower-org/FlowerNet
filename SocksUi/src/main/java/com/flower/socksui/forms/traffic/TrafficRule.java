package com.flower.socksui.forms.traffic;

import com.flower.conntrack.whiteblacklist.AddressFilterList;
import com.flower.conntrack.whiteblacklist.FilterType;

import javax.annotation.Nullable;
import java.util.Date;

public class TrafficRule {
    @Nullable
    final AddressFilterList.AddressRecord addressRecord;
    @Nullable final AddressFilterList.HostRecord hostRecord;
    @Nullable final AddressFilterList.PortRecord portRecord;

    final long creationTimestamp;

    public TrafficRule(AddressFilterList.AddressRecord addressRecord) {
        this.addressRecord = addressRecord;
        this.hostRecord = null;
        this.portRecord = null;
        this.creationTimestamp = addressRecord.creationTimestamp();
    }

    public TrafficRule(AddressFilterList.HostRecord hostRecord) {
        this.addressRecord = null;
        this.hostRecord = hostRecord;
        this.portRecord = null;
        this.creationTimestamp = hostRecord.creationTimestamp();
    }

    public TrafficRule(AddressFilterList.PortRecord portRecord) {
        this.addressRecord = null;
        this.hostRecord = null;
        this.portRecord = portRecord;
        this.creationTimestamp = portRecord.creationTimestamp();
    }

    public FilterType getFilterType() {
        if (addressRecord != null) {

            return addressRecord.filterType();
        } else if (hostRecord != null) {
            return hostRecord.filterType();
        } else if (portRecord != null) {
            return portRecord.filterType();
        } else {
            throw new IllegalStateException("Either addressRecord or hostRecord or portRecord should be not null");
        }
    }

    public String getHost() {
        if (addressRecord != null) {
            return addressRecord.dstHost();
        } else if (hostRecord != null) {
            return hostRecord.dstHost();
        } else if (portRecord != null) {
            return "";
        } else {
            throw new IllegalStateException("Either addressRecord or hostRecord or portRecord should be not null");
        }
    }

    public String getPort() {
        if (addressRecord != null) {
            return Integer.toString(addressRecord.dstPort());
        } else if (hostRecord != null) {
            return "";
        } else if (portRecord != null) {
            return Integer.toString(portRecord.dstPort());
        } else {
            throw new IllegalStateException("Either addressRecord or hostRecord or portRecord should be not null");
        }
    }

    public String getDate() {
        Date date = new Date(creationTimestamp);
        return String.format("%s", date);
    }
}
