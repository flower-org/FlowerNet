package com.flower.net.socksui.forms.traffic;

import com.flower.net.conntrack.allowdenylist.AddressFilterList;
import com.flower.net.config.access.Access;

import javax.annotation.Nullable;
import java.util.Date;

public class TrafficRule {
    @Nullable final AddressFilterList.AddressRecord addressRecord;
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

    public Access getFilterType() {
        if (addressRecord != null) {
            return addressRecord.access();
        } else if (hostRecord != null) {
            return hostRecord.access();
        } else if (portRecord != null) {
            return portRecord.access();
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

    public @Nullable Integer getIntPort() {
        if (addressRecord != null) {
            return addressRecord.dstPort();
        } else if (hostRecord != null) {
            return null;
        } else if (portRecord != null) {
            return portRecord.dstPort();
        } else {
            throw new IllegalStateException("Either addressRecord or hostRecord or portRecord should be not null");
        }
    }

    public String getPort() {
        Integer intPort = getIntPort();
        return intPort == null ? "" : Integer.toString(intPort);
    }

    public boolean isWildcard() {
        if (addressRecord != null) {
            return addressRecord.isWildcard();
        } else if (hostRecord != null) {
            return hostRecord.isWildcard();
        } else if (portRecord != null) {
            return false;
        } else {
            throw new IllegalStateException("Either addressRecord or hostRecord or portRecord should be not null");
        }
    }

    public String isWildcardStr() {
        return Boolean.toString(isWildcard());
    }

    public String getDate() {
        Date date = new Date(creationTimestamp);
        return String.format("%s", date);
    }
}
