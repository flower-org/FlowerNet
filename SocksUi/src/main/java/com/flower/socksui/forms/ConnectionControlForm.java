package com.flower.socksui.forms;

import com.flower.conntrack.ConnectionId;
import com.flower.conntrack.ConnectionInfo;
import com.flower.conntrack.ConnectionListener;
import com.flower.socksui.JavaFxUtils;
import com.flower.socksui.MainApp;
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

import static com.google.common.base.Preconditions.checkNotNull;

public class ConnectionControlForm extends AnchorPane implements Refreshable, ConnectionListener {
    final static Logger LOGGER = LoggerFactory.getLogger(ConnectionControlForm.class);

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
    final ObservableList<ConnectionInfoWrapper> connections;
    final ConcurrentHashMap<ConnectionId, ConnectionInfoWrapper> connectionMap;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public ConnectionControlForm(MainApp mainForm) {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("ConnectionControlForm.fxml"));
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }

        connectionMap = new ConcurrentHashMap<>();
        connections = FXCollections.observableArrayList();
        checkNotNull(connectionTable).itemsProperty().set(connections);

        refreshContent();
    }

    @Override
    public void refreshContent() {
        // TODO: implement
    }

    public void connectionStateQuery() {
        ConnectionInfoWrapper connection = checkNotNull(connectionTable).getSelectionModel().getSelectedItem();
        JavaFxUtils.showMessage("", "", "isActive: " + connection.connectionInfo.channel.isActive());
    }

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
        // TODO: implement
    }

    public void blacklistConnection() {
        // TODO: implement
    }

    public void whitelistConnectionHost() {
        // TODO: implement
    }

    public void blacklistConnectionHost() {
        // TODO: implement
    }

    public void whitelistConnectionPort() {
        // TODO: implement
    }

    public void blacklistConnectionPort() {
        // TODO: implement
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
}
