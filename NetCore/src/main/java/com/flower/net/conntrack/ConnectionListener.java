package com.flower.net.conntrack;

public interface ConnectionListener {
    void connecting(ConnectionInfo connectionInfo);
    void disconnecting(ConnectionId connectionId, String reason);

    //TODO:
    //void incomingTraffic(ConnectionId connectionId, int bytesTransferred);
    //void outgoingTraffic(ConnectionId connectionId, int bytesTransferred);
}
