package com.flower.socksui.forms;

import com.flower.conntrack.ConnectionId;
import com.flower.conntrack.ConnectionInfo;
import com.flower.conntrack.ConnectionListener;
import com.flower.conntrack.Destination;
import com.flower.socksui.MainApp;
import com.flower.socksui.forms.traffic.TrafficControlForm;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static com.flower.conntrack.AddressCheck.CONNECTION_PROHIBITED;
import static com.google.common.base.Preconditions.checkNotNull;

public class ConnectionMonitorForm extends AnchorPane implements Refreshable, ConnectionListener {
    final static Logger LOGGER = LoggerFactory.getLogger(ConnectionMonitorForm.class);

    public static class ConnectionInfoWrapper {
        public final ConnectionInfo connectionInfo;

        public ConnectionInfoWrapper(ConnectionInfo connectionInfo) {
            this.connectionInfo = connectionInfo;
        }

        public String getId() {
            return connectionInfo.connectionId == null ? "N/A" : connectionInfo.connectionId.toString();
        }

        public String getFrom() {
            return connectionInfo.source == null ? "N/A" : connectionInfo.source.toString();
        }

        public String getTo() {
            return connectionInfo.destination == null ? "N/A" : connectionInfo.destination.toString();
        }

        public String getCreatedAt() {
            return String.format("%s", new Date(connectionInfo.creationTime));
        }

        public String getBytesIn() {
            return "N/A";
        }

        public String getBytesOut() {
            return "N/A";
        }
    }

    @Nullable Stage stage;
    @Nullable @FXML TableView<ConnectionInfoWrapper> connectionTable;
    @Nullable @FXML CheckBox closeOnBlacklistCheckBox;
    final ObservableList<ConnectionInfoWrapper> connections;
    final ConcurrentHashMap<ConnectionId, ConnectionInfoWrapper> connectionMap;
    TrafficControlForm trafficControlForm;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public ConnectionMonitorForm(MainApp mainForm, TrafficControlForm trafficControlForm) {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("ConnectionMonitorForm.fxml"));
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }

        this.trafficControlForm = trafficControlForm;
        this.trafficControlForm.setConnectionMonitorForm(this);

        connectionMap = new ConcurrentHashMap<>();
        connections = FXCollections.observableArrayList();
        checkNotNull(connectionTable).itemsProperty().set(connections);

        refreshContent();
    }

    @Override
    public void refreshContent() {
        // TODO: implement?
    }

/*
    //Keeping this for debug
    public void connectionStateQuery() {
        ConnectionInfoWrapper connection = checkNotNull(connectionTable).getSelectionModel().getSelectedItem();
        JavaFxUtils.showMessage("", "", "isActive: " + connection.connectionInfo.channel.isActive());
    }
    */

    public void closeConnection() {
        ConnectionInfoWrapper connection = checkNotNull(connectionTable).getSelectionModel().getSelectedItem();
        connection.connectionInfo.channel.close();
    }

    public void closeAllConnections() {
        List<ConnectionInfoWrapper> connectionList = new ArrayList<>();
        connectionList.addAll(connections);

        for (ConnectionInfoWrapper connection : connectionList) {
            connection.connectionInfo.channel.close();
        }
    }

    public void whitelistConnection() {
        ConnectionInfoWrapper connection = checkNotNull(connectionTable).getSelectionModel().getSelectedItem();
        Destination destination = connection.connectionInfo.destination;
        if (destination != null) {
            trafficControlForm.whitelist(destination.host, destination.port);
        }
    }

    public void blacklistConnection() {
        ConnectionInfoWrapper connection = checkNotNull(connectionTable).getSelectionModel().getSelectedItem();
        Destination destination = connection.connectionInfo.destination;
        if (destination != null) {
            trafficControlForm.blacklist(destination.host, destination.port);
        }
    }

    public void whitelistConnectionHost() {
        ConnectionInfoWrapper connection = checkNotNull(connectionTable).getSelectionModel().getSelectedItem();
        Destination destination = connection.connectionInfo.destination;
        if (destination != null) {
            trafficControlForm.whitelistHost(destination.host);
        }
    }

    public void blacklistConnectionHost() {
        ConnectionInfoWrapper connection = checkNotNull(connectionTable).getSelectionModel().getSelectedItem();
        Destination destination = connection.connectionInfo.destination;
        if (destination != null) {
            trafficControlForm.blacklistHost(destination.host);
        }
    }

    public void whitelistConnectionPort() {
        ConnectionInfoWrapper connection = checkNotNull(connectionTable).getSelectionModel().getSelectedItem();
        Destination destination = connection.connectionInfo.destination;
        if (destination != null) {
            trafficControlForm.whitelistPort(destination.port);
        }
    }

    public void blacklistConnectionPort() {
        ConnectionInfoWrapper connection = checkNotNull(connectionTable).getSelectionModel().getSelectedItem();
        Destination destination = connection.connectionInfo.destination;
        if (destination != null) {
            trafficControlForm.blacklistPort(destination.port);
        }
    }

    @Override
    public void connecting(ConnectionInfo connectionInfo) {
        Platform.runLater(() -> {
            if (connectionInfo.connectionId != null) {
                ConnectionInfoWrapper connectionInfoWrapper = new ConnectionInfoWrapper(connectionInfo);
                if (connectionMap.putIfAbsent(connectionInfo.connectionId, connectionInfoWrapper) == null) {
                    connections.add(connectionInfoWrapper);
                    checkNotNull(connectionTable).refresh();
                }
            }
        });
    }

    @Override
    public void disconnecting(ConnectionId connectionId, String reason) {
        Platform.runLater(() -> {
            ConnectionInfoWrapper connectionInfoWrapper = connectionMap.remove(connectionId);
            if (connectionInfoWrapper != null) {
                connections.remove(connectionInfoWrapper);
                checkNotNull(connectionTable).refresh();
            }
        });
    }

    public void checkBlacklistedHosts() {
        if (checkNotNull(closeOnBlacklistCheckBox).selectedProperty().get()) {
            for (ConnectionInfoWrapper info : connections) {
                Destination dest = info.connectionInfo.destination;

                if (dest != null) {
                    if (trafficControlForm.approveConnection(dest.host, dest.port, null) == CONNECTION_PROHIBITED) {
                        info.connectionInfo.channel.close();
                    }
                }
            }
        }
    }
}
