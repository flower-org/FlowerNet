package com.flower.net.ruler.forms.traffic;

import com.flower.net.conntrack.ConnectionFilter;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.AnchorPane;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.prefs.Preferences;

import static com.google.common.base.Preconditions.checkNotNull;

/** Like RawCaptureForm, but shows captured requests aggregated by destination host:port */
public class CaptureStatsForm extends AnchorPane {
    final static String CAPTURE_FLAG_PREF = "captureStatsFlagPref";
    final static int DEFAULT_MAX_REQUESTS = 50;

    record EntryKey(String host, int port) {}

    /** Max number of host:port entries */
    @Nullable @FXML TextField maxRequests;
    @Nullable @FXML CheckBox captureRequestsCheckBox;
    @Nullable @FXML TableView<CaptureStatsEntry> capturedRequestsTable;
    @Nullable @FXML TableColumn<CaptureStatsEntry, Number> lastAccessColumn;
    /** Unsorted entries; the extractor makes a sorted table re-sort when counts or access times change */
    final ObservableList<CaptureStatsEntry> capturedRequests =
            FXCollections.observableArrayList(entry -> new Observable[] { entry.countProperty(), entry.lastAccessProperty() });
    final Map<EntryKey, CaptureStatsEntry> entriesByKey = new HashMap<>();
    @Nullable @FXML MenuButton captureFilterMenuButton;
    @Nullable @FXML CheckMenuItem matchedAllowedMenuItem;
    @Nullable @FXML CheckMenuItem matchedProhibitedMenuItem;
    @Nullable @FXML CheckMenuItem unmatchedAllowedMenuItem;
    @Nullable @FXML CheckMenuItem unmatchedProhibitedMenuItem;
    @Nullable ConnectionFilter connectionFilter;
    /** Mirrors the capture checkbox so connection threads can skip work without touching the UI */
    volatile boolean captureEnabled;

    public CaptureStatsForm() {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("CaptureStatsForm.fxml"));
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }

        TableView<CaptureStatsEntry> table = checkNotNull(capturedRequestsTable);
        SortedList<CaptureStatsEntry> sortedEntries = new SortedList<>(capturedRequests);
        sortedEntries.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sortedEntries);

        checkNotNull(lastAccessColumn).setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(@Nullable Number timestamp, boolean empty) {
                super.updateItem(timestamp, empty);
                setText(empty || timestamp == null ? null : CapturedRequest.formatTimestamp(timestamp.longValue()));
            }
        });

        checkNotNull(maxRequests).setTextFormatter(new TextFormatter<String>(
                change -> change.getControlNewText().matches("\\d*") ? change : null));

        CheckBox captureCheckBox = checkNotNull(captureRequestsCheckBox);
        captureCheckBox.setSelected(Preferences.userRoot().getBoolean(CAPTURE_FLAG_PREF, false));
        captureEnabled = captureCheckBox.isSelected();
        captureCheckBox.selectedProperty().addListener((obs, oldValue, newValue) -> {
            captureEnabled = newValue;
            Preferences.userRoot().putBoolean(CAPTURE_FLAG_PREF, newValue);
        });

        captureFilterChange();
    }

    public void setConnectionFilter(ConnectionFilter connectionFilter) {
        this.connectionFilter = connectionFilter;
    }

    void withSelectedCapture(BiConsumer<ConnectionFilter, CaptureStatsEntry> action) {
        CaptureStatsEntry capturedRequest = checkNotNull(capturedRequestsTable).getSelectionModel().getSelectedItem();
        if (capturedRequest != null) {
            action.accept(checkNotNull(connectionFilter, "CaptureStatsForm: setConnectionFilter() was not called"),
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
        entriesByKey.clear();
    }

    /** Thread-safe. Callers may check this before building a CapturedRequest. */
    public boolean isCaptureEnabled() {
        return captureEnabled;
    }

    /**
     * Thread-safe: may be called from connection threads. Counts the request towards its host:port entry
     * if capture is on and the filter matches.
     * The caller decides what is worth capturing (e.g. skips re-checks of existing connections, where from == null).
     */
    public void addCapture(CapturedRequest capturedRequest) {
        if (!captureEnabled) {
            return;
        }
        Platform.runLater(() -> {
            // Re-checked: capture may have been turned off while this was queued
            if (captureEnabled
                    && getCaptureFilter().matchCapturedRecord(capturedRequest.getAccess(), capturedRequest.isRuleMatched())) {
                EntryKey key = new EntryKey(capturedRequest.getHost(), capturedRequest.getPort());
                CaptureStatsEntry entry = entriesByKey.get(key);
                if (entry == null) {
                    entry = new CaptureStatsEntry(key.host(), key.port());
                    entry.record(capturedRequest);
                    entriesByKey.put(key, entry);
                    capturedRequests.add(entry);
                    trimToMaxRequests();
                } else {
                    entry.record(capturedRequest);
                }
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

    /** Drops the least recently accessed entries above the limit */
    void trimToMaxRequests() {
        int max = getMaxRequests();
        while (capturedRequests.size() > max) {
            CaptureStatsEntry oldest = capturedRequests.stream()
                    .min(Comparator.comparingLong(CaptureStatsEntry::getLastAccess))
                    .orElseThrow();
            capturedRequests.remove(oldest);
            entriesByKey.remove(new EntryKey(oldest.getHost(), oldest.getPort()));
        }
    }

    public void captureFilterChange() {
        checkNotNull(captureFilterMenuButton).textProperty().set(getCaptureFilter().toString());
    }
}
