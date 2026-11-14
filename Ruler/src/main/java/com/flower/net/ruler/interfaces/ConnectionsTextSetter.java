package com.flower.net.ruler.interfaces;

public interface ConnectionsTextSetter {
    void setConnectionsText(String text);

    /** What the counters count, shown as the counters' title */
    default String countersTitle() {
        return "Connections";
    }
}
