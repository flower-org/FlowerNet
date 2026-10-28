package com.flower.net.packetui.forms;

import com.flower.fxutils.Refreshable;
import com.flower.net.conntrack.ConnectionFilter;
import com.flower.net.conntrack.ConnectionListener;
import com.flower.net.packetui.MainApp;
import com.flower.net.packetui.server.ConnectionFilterDecider;
import com.flower.net.packetui.server.DeciderServer;
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
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkNotNull;

public class ServerForm extends AnchorPane implements Refreshable {
    final ReentrantLock startLock;
    final MainApp mainApp;
    final ConnectionFilterDecider decider;
    final DeciderServer server;
    boolean isStarted;

    @Nullable @FXML Button startButton;
    @Nullable @FXML Button stopButton;
    @Nullable @FXML TextField socketPathTextField;

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

        decider = new ConnectionFilterDecider();
        server = new DeciderServer(decider);

        refreshContent();
    }

    public void addConnectionFilter(ConnectionFilter connectionFilter) {
        decider.addConnectionFilter(connectionFilter);
    }

    public void addConnectionListener(ConnectionListener connectionListener) {
        // TODO: implement
        // server.addConnectionListener(connectionListener);
    }

    public void startServer() {
        try {
            startLock.lock();
            if (!isStarted) {
                String socketPathStr = checkNotNull(socketPathTextField).getText().trim();
                Path socketPath;
                try {
                    if (socketPathStr.isEmpty()) {
                        throw new InvalidPathException(socketPathStr, "Socket path is empty");
                    }
                    socketPath = Path.of(socketPathStr);
                    server.start(socketPath);
                } catch (IOException | InvalidPathException | UnsupportedOperationException e) {
                    mainApp.setStatusText("Server failed to start");
                    new Alert(Alert.AlertType.ERROR, "Failed to start server on " + socketPathStr + ":\n" + e,
                            ButtonType.OK).showAndWait();
                    return;
                }
                mainApp.setStatusText("Server started on " + socketPath);
                isStarted = true;
            }
            checkNotNull(socketPathTextField).setDisable(true);
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
                server.stop();
                mainApp.setStatusText("Server not running");
                isStarted = false;
            }
            checkNotNull(socketPathTextField).setDisable(false);
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