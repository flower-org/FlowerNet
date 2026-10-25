package com.flower.net.ruler.forms.traffic;

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
    /** Mirrors the capture checkbox so connection threads can skip work without touching the UI */
    volatile boolean captureEnabled;

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

        CheckBox captureCheckBox = checkNotNull(captureRequestsCheckBox);
        captureCheckBox.selectedProperty().addListener((obs, oldValue, newValue) -> captureEnabled = newValue);
        captureCheckBox.setSelected(Preferences.userRoot().getBoolean(CAPTURE_FLAG_PREF, false));

        captureFilterChange();
    }

    public void setConnectionFilter(ConnectionFilter connectionFilter) {
        this.connectionFilter = connectionFilter;
    }

    void withSelectedCapture(BiConsumer<ConnectionFilter, CapturedRequest> action) {
        CapturedRequest capturedRequest = checkNotNull(capturedRequestsTable).getSelectionModel().getSelectedItem();
        if (capturedRequest != null) {
            action.accept(checkNotNull(connectionFilter, "RawCaptureForm: setConnectionFilter() was not called"),
                    capturedRequest);
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

    /** Thread-safe. Callers may check this before building a CapturedRequest. */
    public boolean isCaptureEnabled() {
        return captureEnabled;
    }

    /** Thread-safe: may be called from connection threads. Shows the request if capture is on and the filter matches. */
    public void addCapture(CapturedRequest capturedRequest) {
        if (!captureEnabled) {
            return;
        }
        Platform.runLater(() -> {
            // Re-checked: capture may have been turned off while this was queued
            if (checkNotNull(captureRequestsCheckBox).isSelected()
                    && getCaptureFilter().matchCapturedRecord(capturedRequest.getAccess(), capturedRequest.isRuleMatched())) {
                capturedRequests.add(capturedRequest);
                trimToMaxRequests();
                checkNotNull(capturedRequestsTable).refresh();
            }
        });
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
