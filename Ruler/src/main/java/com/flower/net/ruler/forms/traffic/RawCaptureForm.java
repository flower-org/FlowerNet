package com.flower.net.ruler.forms.traffic;

import com.flower.net.config.access.Access;
import com.flower.net.config.access.Protocol;
import com.flower.net.conntrack.ConnectionFilter;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.prefs.Preferences;

import static com.google.common.base.Preconditions.checkNotNull;

public class RawCaptureForm extends AnchorPane {
    final static Logger LOGGER = LoggerFactory.getLogger(RawCaptureForm.class);

    final static String CAPTURE_FLAG_PREF = "captureFlagPref";

    @Nullable Stage stage;
    @Nullable @FXML TextField maxRequests;
    @Nullable @FXML CheckBox captureRequestsCheckBox;
    @Nullable @FXML TableView<CapturedRequest> capturedRequestsTable;
    final ObservableList<CapturedRequest> capturedRequests;
    @Nullable @FXML MenuButton captureFilterMenuButton;
    @Nullable ConnectionFilter connectionFilter;

    public RawCaptureForm() {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("RawCaptureForm.fxml"));
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }

        capturedRequests = FXCollections.observableArrayList();
        checkNotNull(capturedRequestsTable).itemsProperty().set(capturedRequests);

        Preferences userPreferences = Preferences.userRoot();
        String captureFlagStr = userPreferences.get(CAPTURE_FLAG_PREF, "");
        boolean captureFlag = false;
        if (!StringUtils.isBlank(captureFlagStr)) {
            try {
                captureFlag = Boolean.parseBoolean(captureFlagStr);
            } catch (Exception e) { }
        }
        checkNotNull(captureRequestsCheckBox).selectedProperty().set(captureFlag);

        captureFilterChange();
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setConnectionFilter(ConnectionFilter connectionFilter) {
        this.connectionFilter = connectionFilter;
    }

    public void allowCapture() {
        CapturedRequest capturedRequest = checkNotNull(capturedRequestsTable).getSelectionModel().getSelectedItem();
        if (capturedRequest != null) {
            checkNotNull(connectionFilter).allow(capturedRequest.getHost(), capturedRequest.getPort());
        }
    }

    public void denyCapture() {
        CapturedRequest capturedRequest = checkNotNull(capturedRequestsTable).getSelectionModel().getSelectedItem();
        if (capturedRequest != null) {
            checkNotNull(connectionFilter).deny(capturedRequest.getHost(), capturedRequest.getPort());
        }
    }

    public void allowCaptureHost() {
        CapturedRequest capturedRequest = checkNotNull(capturedRequestsTable).getSelectionModel().getSelectedItem();
        if (capturedRequest != null) {
            checkNotNull(connectionFilter).allowHost(capturedRequest.getHost());
        }
    }

    public void denyCaptureHost() {
        CapturedRequest capturedRequest = checkNotNull(capturedRequestsTable).getSelectionModel().getSelectedItem();
        if (capturedRequest != null) {
            checkNotNull(connectionFilter).denyHost(capturedRequest.getHost());
        }
    }

    public void allowCapturePort() {
        CapturedRequest capturedRequest = checkNotNull(capturedRequestsTable).getSelectionModel().getSelectedItem();
        if (capturedRequest != null) {
            checkNotNull(connectionFilter).allowPort(capturedRequest.getPort());
        }
    }

    public void denyCapturePort() {
        CapturedRequest capturedRequest = checkNotNull(capturedRequestsTable).getSelectionModel().getSelectedItem();
        if (capturedRequest != null) {
            checkNotNull(connectionFilter).denyPort(capturedRequest.getPort());
        }
    }

    public CaptureFilter getCaptureFilter() {
        MenuButton menuButton = checkNotNull(captureFilterMenuButton);
        boolean matchedAllowed = ((CheckMenuItem)(menuButton).getItems().get(0)).selectedProperty().get();
        boolean matchedProhibited = ((CheckMenuItem)menuButton.getItems().get(1)).selectedProperty().get();
        boolean unmatchedAllowed = ((CheckMenuItem)menuButton.getItems().get(2)).selectedProperty().get();
        boolean unmatchedProhibited = ((CheckMenuItem)menuButton.getItems().get(3)).selectedProperty().get();

        return new CaptureFilter(matchedAllowed, matchedProhibited, unmatchedAllowed, unmatchedProhibited);
    }

    public void clearCapturedData() {
        capturedRequests.clear();
        checkNotNull(capturedRequestsTable).refresh();
    }

    public void captureFlagChange() {
        Preferences userPreferences = Preferences.userRoot();
        userPreferences.put(CAPTURE_FLAG_PREF, Boolean.toString(checkNotNull(captureRequestsCheckBox).selectedProperty().get()));
    }

    public void addCapture(String dstHost, int dstPort, @Nullable SocketAddress from,
                           Access access, boolean isRuleMatched, boolean isDirectIpBlock) {
        if (from != null) {
            Platform.runLater(() -> {
                if (checkNotNull(captureRequestsCheckBox).selectedProperty().get()) {
                    CaptureFilter captureFilter = getCaptureFilter();
                    if (captureFilter.matchCapturedRecord(access, isRuleMatched)) {
                        String hostOrIpFrom = null;
                        int port = -1;
                        if (from instanceof InetSocketAddress isa) {
                            hostOrIpFrom = (isa.getAddress() != null)
                                    ? isa.getAddress().getHostAddress() // resolved -> IP
                                    : isa.getHostString();              // unresolved -> original host
                            port = isa.getPort();
                        }

                        CapturedRequest capturedRequest = new CapturedRequest(dstHost, dstPort, access,
                                isRuleMatched, Protocol.TCP, isDirectIpBlock, hostOrIpFrom, port);
                        capturedRequests.add(capturedRequest);
                        try {
                            int max = Integer.parseInt(checkNotNull(maxRequests).textProperty().get());
                            while (capturedRequests.size() > max) {
                                CapturedRequest selectedItem = checkNotNull(capturedRequestsTable).getSelectionModel().getSelectedItem();
                                capturedRequests.remove(0);
                                if (selectedItem != null) {
                                    try {
                                        checkNotNull(capturedRequestsTable).getSelectionModel().select(selectedItem);
                                    } catch (Exception e) {
                                        // selectedItem may be out of bounds
                                    }
                                }
                            }
                        } catch (Exception e) {
                        }

                        checkNotNull(capturedRequestsTable).refresh();
                    }
                }
            });
        }
    }

    public void captureFilterChange() {
        checkNotNull(captureFilterMenuButton).textProperty().set(getCaptureFilter().toString());
    }
}
