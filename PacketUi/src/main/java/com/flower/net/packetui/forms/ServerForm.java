package com.flower.net.packetui.forms;

import com.flower.fxutils.Refreshable;
import com.flower.net.conntrack.ConnectionFilter;
import com.flower.net.conntrack.ConnectionListener;
import com.flower.net.packetui.MainApp;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkNotNull;

public class ServerForm extends AnchorPane implements Refreshable {
    final ReentrantLock startLock;
    final MainApp mainApp;
    boolean isStarted;

    @Nullable @FXML Button startButton;
    @Nullable @FXML Button stopButton;
    @Nullable @FXML TextField portTextField;

    @Nullable Stage stage;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public ServerForm(MainApp mainApp) {
        this.mainApp = mainApp;
        startLock = new ReentrantLock();
        isStarted = false;

        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("ServerForm.fxml"));
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }

        // TODO: implement
        // We allow direct IPs on server side since we control that in TrafficControlForm, which implements filter
        /*server = new SocksServer(() -> true, () -> new SocksChainServerConnectHandler(
                this, this::getBindClientToIp, getKeyManagerSupplier()));*/

        refreshContent();
    }

    public void addConnectionFilter(ConnectionFilter connectionFilter) {
        // TODO: implement
        // server.addConnectionFilter(connectionFilter);
    }

    public void addConnectionListener(ConnectionListener connectionListener) {
        // TODO: implement
        // server.addConnectionListener(connectionListener);
    }

    public void startServer() throws InterruptedException {
        try {
            startLock.lock();
            if (!isStarted) {
                //checkNotNull(server).startServer(port, bindToIp, null).sync();
                mainApp.setStatusText("Server started on port " /*+ port*/);
                isStarted = true;
            }
            checkNotNull(portTextField).setDisable(true);
            checkNotNull(startButton).setDisable(true);
            checkNotNull(stopButton).setDisable(false);
        } finally {
            startLock.unlock();
        }
    }

    public void stopServer() {
        try {
            startLock.lock();
            if (isStarted) {
                // TODO: implement
                // checkNotNull(server).shutdownServer();
                mainApp.setStatusText("Server not running");
                isStarted = false;
            }
            checkNotNull(portTextField).setDisable(false);
            checkNotNull(startButton).setDisable(false);
            checkNotNull(stopButton).setDisable(true);
        } finally {
            startLock.unlock();
        }
    }

    @Override
    public void refreshContent() {
    }

    public void notImplemented() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, "Function not implemented yet", ButtonType.OK);
        alert.showAndWait();
    }
}