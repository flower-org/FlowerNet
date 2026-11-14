package com.flower.net.packetui.forms;

import com.flower.fxutils.Refreshable;
import com.flower.net.conntrack.ConnectionFilter;
import com.flower.net.conntrack.ConnectionListener;
import com.flower.net.dns.DnsServer;
import com.flower.net.packetui.MainApp;
import com.flower.net.packetui.server.ConnectionFilterDecider;
import com.flower.net.packetui.server.DeciderServer;
import com.flower.net.packetui.server.Subnets;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;
import java.util.prefs.Preferences;

import static com.google.common.base.Preconditions.checkNotNull;

public class ServerForm extends AnchorPane implements Refreshable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerForm.class);
    static final String INTERNAL_SUBNETS_PREF = "packetUiInternalSubnetsPref";

    final ReentrantLock startLock;
    final MainApp mainApp;
    final ConnectionFilterDecider decider;
    final DeciderServer server;
    @Nullable DnsServer dnsServer;
    boolean isStarted;

    // TODO: make configurable
    static final int DNS_PORT = 5300;

    @Nullable @FXML Button startButton;
    @Nullable @FXML Button stopButton;
    @Nullable @FXML TextField socketPathTextField;
    @Nullable @FXML TextField internalSubnetsTextField;
    @Nullable @FXML Button applyInternalSubnetsButton;
    /** What the decider currently uses; Apply is enabled only when the field says something else. */
    Subnets appliedSubnets = Subnets.EMPTY;

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
        loadInternalSubnets();

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
                try {
                    DnsServer dns = DnsServer.start(DNS_PORT, true);
                    dnsServer = dns;
                    decider.setResolvedHosts(dns.resolvedHosts());
                } catch (Exception e) {
                    server.stop();
                    mainApp.setStatusText("Server failed to start");
                    new Alert(Alert.AlertType.ERROR, "Failed to start DNS server on port " + DNS_PORT + ":\n" + e,
                            ButtonType.OK).showAndWait();
                    return;
                }
                mainApp.setStatusText("Server started on " + socketPath + ", DNS on 127.0.0.1 and ::1 port " + DNS_PORT);
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
                decider.setResolvedHosts(null);
                DnsServer dns = dnsServer;
                dnsServer = null;
                if (dns != null) {
                    dns.close();
                }
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

    void loadInternalSubnets() {
        TextField textField = checkNotNull(internalSubnetsTextField);
        textField.textProperty().addListener((observable, oldText, newText) -> updateApplyInternalSubnetsButton());
        String text = Preferences.userRoot().get(INTERNAL_SUBNETS_PREF, "");
        try {
            appliedSubnets = Subnets.parse(text);
            decider.setInternalSubnets(appliedSubnets);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Ignoring invalid saved internal subnets '{}': {}", text, e.getMessage());
        }
        textField.setText(text);
        updateApplyInternalSubnetsButton();
    }

    /** Enabled when the field differs from the applied subnets, or is invalid (so Apply shows the error). */
    void updateApplyInternalSubnetsButton() {
        boolean nothingToApply;
        try {
            nothingToApply = Subnets.parse(checkNotNull(internalSubnetsTextField).getText()).toString()
                    .equals(appliedSubnets.toString());
        } catch (IllegalArgumentException e) {
            nothingToApply = false;
        }
        checkNotNull(applyInternalSubnetsButton).setDisable(nothingToApply);
    }

    public void applyInternalSubnets() {
        if (checkNotNull(applyInternalSubnetsButton).isDisable()) {
            return; // Enter in the field with nothing to apply
        }
        TextField textField = checkNotNull(internalSubnetsTextField);
        Subnets subnets;
        try {
            subnets = Subnets.parse(textField.getText());
        } catch (IllegalArgumentException e) {
            new Alert(Alert.AlertType.ERROR, "Invalid internal subnets:\n" + e.getMessage(), ButtonType.OK).showAndWait();
            return;
        }
        decider.setInternalSubnets(subnets);
        appliedSubnets = subnets;
        String normalized = subnets.toString();
        textField.setText(normalized);
        Preferences.userRoot().put(INTERNAL_SUBNETS_PREF, normalized);
        mainApp.setStatusText(subnets.isEmpty() ? "Internal subnets cleared" : "Internal subnets: " + normalized);
    }

    @Override
    public void refreshContent() {
    }

    public void notImplemented() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, "Function not implemented yet", ButtonType.OK);
        alert.showAndWait();
    }
}