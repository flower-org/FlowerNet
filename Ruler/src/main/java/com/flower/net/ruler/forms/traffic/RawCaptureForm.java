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
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.AnchorPane;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.function.BiConsumer;
import java.util.prefs.Preferences;

import static com.google.common.base.Preconditions.checkNotNull;

public class RawCaptureForm extends AnchorPane {
    final static String CAPTURE_FLAG_PREF = "captureFlagPref";
    final static int DEFAULT_MAX_REQUESTS = 50;

    @Nullable @FXML TextField maxRequests;
    @Nullable @FXML CheckBox captureRequestsCheckBox;
    @Nullable @FXML TableView<CapturedRequest> capturedRequestsTable;
    final ObservableList<CapturedRequest> capturedRequests;
    @Nullable @FXML MenuButton captureFilterMenuButton;
    @Nullable @FXML CheckMenuItem matchedAllowedMenuItem;
    @Nullable @FXML CheckMenuItem matchedProhibitedMenuItem;
    @Nullable @FXML CheckMenuItem unmatchedAllowedMenuItem;
    @Nullable @FXML CheckMenuItem unmatchedProhibitedMenuItem;
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

        checkNotNull(maxRequests).setTextFormatter(new TextFormatter<String>(
                change -> change.getControlNewText().matches("\\d*") ? change : null));

        boolean captureFlag = Preferences.userRoot().getBoolean(CAPTURE_FLAG_PREF, false);
        checkNotNull(captureRequestsCheckBox).selectedProperty().set(captureFlag);

        captureFilterChange();
    }

    public void setConnectionFilter(ConnectionFilter connectionFilter) {
        this.connectionFilter = connectionFilter;
    }

    void withSelectedCapture(BiConsumer<ConnectionFilter, CapturedRequest> action) {
        CapturedRequest capturedRequest = checkNotNull(capturedRequestsTable).getSelectionModel().getSelectedItem();
        if (capturedRequest != null) {
            action.accept(checkNotNull(connectionFilter), capturedRequest);
        }
    }

    public void allowCapture() {
        withSelectedCapture((filter, request) -> filter.allow(request.getHost(), request.getPort()));
    }

    public void denyCapture() {
        withSelectedCapture((filter, request) -> filter.deny(request.getHost(), request.getPort()));
    }

    public void allowCaptureHost() {
        withSelectedCapture((filter, request) -> filter.allowHost(request.getHost()));
    }

    public void denyCaptureHost() {
        withSelectedCapture((filter, request) -> filter.denyHost(request.getHost()));
    }

    public void allowCapturePort() {
        withSelectedCapture((filter, request) -> filter.allowPort(request.getPort()));
    }

    public void denyCapturePort() {
        withSelectedCapture((filter, request) -> filter.denyPort(request.getPort()));
    }

    public CaptureFilter getCaptureFilter() {
        boolean matchedAllowed = checkNotNull(matchedAllowedMenuItem).isSelected();
        boolean matchedProhibited = checkNotNull(matchedProhibitedMenuItem).isSelected();
        boolean unmatchedAllowed = checkNotNull(unmatchedAllowedMenuItem).isSelected();
        boolean unmatchedProhibited = checkNotNull(unmatchedProhibitedMenuItem).isSelected();

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
                        Integer port = null;
                        if (from instanceof InetSocketAddress isa) {
                            hostOrIpFrom = (isa.getAddress() != null)
                                    ? isa.getAddress().getHostAddress() // resolved -> IP
                                    : isa.getHostString();              // unresolved -> original host
                            port = isa.getPort();
                        }

                        CapturedRequest capturedRequest = new CapturedRequest(dstHost, dstPort, access,
                                isRuleMatched, Protocol.TCP, isDirectIpBlock, hostOrIpFrom, port);
                        capturedRequests.add(capturedRequest);
                        trimToMaxRequests();

                        checkNotNull(capturedRequestsTable).refresh();
                    }
                }
            });
        }
    }

    /** Falls back to the default if the field is empty or out of int range (it only accepts digits) */
    int getMaxRequests() {
        try {
            return Integer.parseInt(checkNotNull(maxRequests).getText());
        } catch (NumberFormatException e) {
            return DEFAULT_MAX_REQUESTS;
        }
    }

    /** Drops the oldest captures above the limit; if the selected row was dropped, the selection moves to the next row */
    void trimToMaxRequests() {
        int excess = capturedRequests.size() - getMaxRequests();
        if (excess > 0) {
            TableView<CapturedRequest> table = checkNotNull(capturedRequestsTable);
            CapturedRequest selectedItem = table.getSelectionModel().getSelectedItem();
            capturedRequests.remove(0, excess);
            if (selectedItem != null && capturedRequests.contains(selectedItem)) {
                table.getSelectionModel().select(selectedItem);
            }
        }
    }

    public void captureFilterChange() {
        checkNotNull(captureFilterMenuButton).textProperty().set(getCaptureFilter().toString());
    }
}
