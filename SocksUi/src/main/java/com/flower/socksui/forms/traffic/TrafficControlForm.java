package com.flower.socksui.forms.traffic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.flower.conntrack.ConnectionListenerAndFilter;
import com.flower.conntrack.whiteblacklist.AddressFilterList;
import com.flower.conntrack.whiteblacklist.FilterType;
import com.flower.conntrack.whiteblacklist.ImmutableAddressRecord;
import com.flower.conntrack.whiteblacklist.ImmutableHostRecord;
import com.flower.conntrack.whiteblacklist.ImmutablePortRecord;
import com.flower.conntrack.whiteblacklist.WhitelistBlacklistConnectionFilter;
import com.flower.socksui.JavaFxUtils;
import com.flower.socksui.MainApp;
import com.flower.socksui.ModalWindow;
import com.flower.socksui.forms.Refreshable;
import com.flower.utils.NonDnsHostnameChecker;
import com.google.common.collect.Streams;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.prefs.Preferences;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.flower.conntrack.whiteblacklist.AddressFilterList.AddressRecord;
import static com.flower.conntrack.whiteblacklist.AddressFilterList.HostRecord;
import static com.flower.conntrack.whiteblacklist.AddressFilterList.PortRecord;

public class TrafficControlForm extends AnchorPane implements Refreshable, ConnectionListenerAndFilter {
    final static Logger LOGGER = LoggerFactory.getLogger(TrafficControlForm.class);
    final static String TRAFFIC_RULES_PREF = "trafficRulesPref";

    final static String OFF = "Off";
    final static String WHITELIST = "Whitelist";
    final static String BLACKLIST = "Blacklist";

    @Nullable Stage stage;
    @Nullable @FXML ComboBox<String> filteringModeComboBox;
    @Nullable @FXML CheckBox captureRequestsCheckBox;
    @Nullable @FXML TableView<CapturedRequest> capturedRequestsTable;
    final ObservableList<CapturedRequest> capturedRequests;
    @Nullable @FXML TableView<TrafficRule> trafficRulesTable;
    final ObservableList<TrafficRule> trafficRules;
    @Nullable @FXML TextField maxRequests;
    @Nullable @FXML CheckBox allowDirectIpAccessCheckBox;
    @Nullable @FXML MenuButton captureFilterMenuButton;

    final WhitelistBlacklistConnectionFilter innerFilter;

    final AtomicLong totalConnections;
    final AtomicLong allowedConnections;
    final AtomicLong prohibitedConnections;
    final MainApp mainForm;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public TrafficControlForm(MainApp mainForm) {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("TrafficControlForm.fxml"));
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }

        totalConnections = new AtomicLong(0);
        allowedConnections = new AtomicLong(0);
        prohibitedConnections = new AtomicLong(0);
        this.mainForm = mainForm;

        innerFilter = new WhitelistBlacklistConnectionFilter();

        capturedRequests = FXCollections.observableArrayList();
        checkNotNull(capturedRequestsTable).itemsProperty().set(capturedRequests);

        trafficRules = FXCollections.observableArrayList();
        checkNotNull(trafficRulesTable).itemsProperty().set(trafficRules);

        try {
            Preferences userPreferences = Preferences.userRoot();
            String trafficRules = userPreferences.get(TRAFFIC_RULES_PREF, "");
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
                    .registerModule(new GuavaModule());
            AddressFilterList addressFilterList = mapper.readValue(trafficRules, AddressFilterList.class);

            innerFilter.clear();
            innerFilter.addList(addressFilterList, true);
        } catch (Exception e) {}

        refreshContent();

        captureFilterChange();
    }

    public enum TrafficControlType {
        WHITELIST,
        BLACKLIST,
        OFF
    }

    public TrafficControlType getTrafficControlType() {
        String modeStr = checkNotNull(filteringModeComboBox).getSelectionModel().getSelectedItem();
        switch (modeStr) {
            case OFF: return TrafficControlType.OFF;
            case BLACKLIST: return TrafficControlType.BLACKLIST;
            case WHITELIST:
            default: return TrafficControlType.WHITELIST;
        }
    }

    public void captureFilterChange() {
        checkNotNull(captureFilterMenuButton).textProperty().set(getCaptureFilter().toString());
    }

    public CaptureFilter getCaptureFilter() {
        MenuButton menuButton = checkNotNull(captureFilterMenuButton);
        boolean matchedAllowed = ((CheckMenuItem)(menuButton).getItems().get(0)).selectedProperty().get();
        boolean matchedProhibited = ((CheckMenuItem)menuButton.getItems().get(1)).selectedProperty().get();
        boolean unmatchedAllowed = ((CheckMenuItem)menuButton.getItems().get(2)).selectedProperty().get();
        boolean unmatchedProhibited = ((CheckMenuItem)menuButton.getItems().get(3)).selectedProperty().get();

        return new CaptureFilter(matchedAllowed, matchedProhibited, unmatchedAllowed, unmatchedProhibited);
    }

    @Override
    public AddressCheck approveConnection(String dstHost, int dstPort, SocketAddress from) {
        AddressCheck checkResult;
        boolean isDirectIpBlock = false;
        boolean isRuleMatched;

        TrafficControlType trafficControlType = getTrafficControlType();
        if (trafficControlType == TrafficControlType.OFF) {
            // If filtering is off, we allow all connections
            isRuleMatched = false;
            checkResult = AddressCheck.CONNECTION_ALLOWED;
        } else {
            boolean isDirectIpAccessAllowed = checkNotNull(allowDirectIpAccessCheckBox).selectedProperty().get();
            if (!isDirectIpAccessAllowed && NonDnsHostnameChecker.isIPAddress(dstHost)) {
                isDirectIpBlock = true;
                isRuleMatched = false;
                checkResult = AddressCheck.CONNECTION_PROHIBITED;
            } else if (trafficControlType == TrafficControlType.WHITELIST) {
                isRuleMatched = true;
                checkResult = innerFilter.getRecordRule(dstHost, dstPort);

                // If matching record not found, we prohibit anything that's not whitelisted
                if (checkResult == null) {
                    isRuleMatched = false;
                    checkResult = AddressCheck.CONNECTION_PROHIBITED;
                }
            } else if (trafficControlType == TrafficControlType.BLACKLIST) {
                isRuleMatched = true;
                checkResult = innerFilter.getRecordRule(dstHost, dstPort);

                // If matching records not found, we allow everything that's not blacklisted
                if (checkResult == null) {
                    isRuleMatched = false;
                    checkResult = AddressCheck.CONNECTION_ALLOWED;
                }
            } else {
                throw new IllegalArgumentException("Unknown traffic control type: " + trafficControlType);
            }
        }

        final AddressCheck finalCheckResult = checkResult;
        final boolean finalIsRuleMatched = isRuleMatched;
        final boolean finalIsDirectIpBlock = isDirectIpBlock;
        Platform.runLater(() -> {
            if (checkNotNull(captureRequestsCheckBox).selectedProperty().get()) {
                CaptureFilter captureFilter = getCaptureFilter();
                if (captureFilter.matchCapturedRecord(finalCheckResult, finalIsRuleMatched)) {
                    CapturedRequest capturedRequest = new CapturedRequest(dstHost, dstPort, finalCheckResult,
                            finalIsRuleMatched, finalIsDirectIpBlock, from);
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
                    } catch (Exception e) {}

                    checkNotNull(capturedRequestsTable).refresh();
                }
            }
        });

        totalConnections.incrementAndGet();
        if (checkResult == AddressCheck.CONNECTION_ALLOWED) {
            allowedConnections.incrementAndGet();
        } else {
            prohibitedConnections.incrementAndGet();
        }

        Platform.runLater(() -> {
            updateConnectionStats();
        });

        return checkResult;
    }

    public void updateConnectionStats() {
        mainForm.setConnectionsText(
                String.format("Connections: total: %d allowed: %d prohibited: %d",
                        totalConnections.get(), allowedConnections.get(), prohibitedConnections.get())
        );
    }

    public void clearCapturedData() {
        capturedRequests.clear();
        checkNotNull(trafficRulesTable).refresh();
    }

    public void whitelistCapture() {
        CapturedRequest capturedRequest = checkNotNull(capturedRequestsTable).getSelectionModel().getSelectedItem();
        if (capturedRequest != null) {
            AddressRecord addressRecord = ImmutableAddressRecord.builder()
                    .dstHost(capturedRequest.getHost())
                    .dstPort(capturedRequest.getPort())
                    .filterType(FilterType.WHITELIST)
                    .creationTimestamp(System.currentTimeMillis())
                    .build();
            AddressRecord existingRule = innerFilter.addAddressRecord(addressRecord, false);
            if (existingRule != null && !AddressRecord.recordsEqual(existingRule, addressRecord)) {
                //Ask to reload
                if (JavaFxUtils.showYesNoDialog("Overwrite existing rule?") == JavaFxUtils.YesNo.YES) {
                    innerFilter.addAddressRecord(addressRecord, true);
                }
            }
            refreshContent();
        }
    }

    public void blacklistCapture() {
        CapturedRequest capturedRequest = checkNotNull(capturedRequestsTable).getSelectionModel().getSelectedItem();
        if (capturedRequest != null) {
            AddressRecord addressRecord = ImmutableAddressRecord.builder()
                    .dstHost(capturedRequest.getHost())
                    .dstPort(capturedRequest.getPort())
                    .filterType(FilterType.BLACKLIST)
                    .creationTimestamp(System.currentTimeMillis())
                    .build();
            AddressRecord existingRule = innerFilter.addAddressRecord(addressRecord, false);
            if (existingRule != null && !AddressRecord.recordsEqual(existingRule, addressRecord)) {
                //Ask to reload
                if (JavaFxUtils.showYesNoDialog("Overwrite existing rule?") == JavaFxUtils.YesNo.YES) {
                    innerFilter.addAddressRecord(addressRecord, true);
                }
            }
            refreshContent();
        }
    }

    public void whitelistCaptureHost() {
        CapturedRequest capturedRequest = checkNotNull(capturedRequestsTable).getSelectionModel().getSelectedItem();
        if (capturedRequest != null) {
            HostRecord hostRecord = ImmutableHostRecord.builder()
                    .dstHost(capturedRequest.getHost())
                    .filterType(FilterType.WHITELIST)
                    .creationTimestamp(System.currentTimeMillis())
                    .build();
            HostRecord existingRule = innerFilter.addHostRecord(hostRecord, false);
            if (existingRule != null && !HostRecord.recordsEqual(existingRule, hostRecord)) {
                //Ask to reload
                if (JavaFxUtils.showYesNoDialog("Overwrite existing rule?") == JavaFxUtils.YesNo.YES) {
                    innerFilter.addHostRecord(hostRecord, true);
                }
            }
            refreshContent();
        }
    }

    public void blacklistCaptureHost() {
        CapturedRequest capturedRequest = checkNotNull(capturedRequestsTable).getSelectionModel().getSelectedItem();
        if (capturedRequest != null) {
            HostRecord hostRecord = ImmutableHostRecord.builder()
                    .dstHost(capturedRequest.getHost())
                    .filterType(FilterType.BLACKLIST)
                    .creationTimestamp(System.currentTimeMillis())
                    .build();
            HostRecord existingRule = innerFilter.addHostRecord(hostRecord, false);
            if (existingRule != null && !HostRecord.recordsEqual(existingRule, hostRecord)) {
                //Ask to reload
                if (JavaFxUtils.showYesNoDialog("Overwrite existing rule?") == JavaFxUtils.YesNo.YES) {
                    innerFilter.addHostRecord(hostRecord, true);
                }
            }
            refreshContent();
        }
    }

    public void whitelistCapturePort() {
        CapturedRequest capturedRequest = checkNotNull(capturedRequestsTable).getSelectionModel().getSelectedItem();
        if (capturedRequest != null) {
            PortRecord portRecord = ImmutablePortRecord.builder()
                    .dstPort(capturedRequest.getPort())
                    .filterType(FilterType.WHITELIST)
                    .creationTimestamp(System.currentTimeMillis())
                    .build();
            PortRecord existingRule = innerFilter.addPortRecord(portRecord, false);
            if (existingRule != null && !PortRecord.recordsEqual(existingRule, portRecord)) {
                //Ask to reload
                if (JavaFxUtils.showYesNoDialog("Overwrite existing rule?") == JavaFxUtils.YesNo.YES) {
                    innerFilter.addPortRecord(portRecord, true);
                }
            }
            refreshContent();
        }
    }

    public void blacklistCapturePort() {
        CapturedRequest capturedRequest = checkNotNull(capturedRequestsTable).getSelectionModel().getSelectedItem();
        if (capturedRequest != null) {
            PortRecord portRecord = ImmutablePortRecord.builder()
                    .dstPort(capturedRequest.getPort())
                    .filterType(FilterType.BLACKLIST)
                    .creationTimestamp(System.currentTimeMillis())
                    .build();
            PortRecord existingRule = innerFilter.addPortRecord(portRecord, false);
            if (existingRule != null && !PortRecord.recordsEqual(existingRule, portRecord)) {
                //Ask to reload
                if (JavaFxUtils.showYesNoDialog("Overwrite existing rule?") == JavaFxUtils.YesNo.YES) {
                    innerFilter.addPortRecord(portRecord, true);
                }
            }
            refreshContent();
        }
    }

    @Override
    public void refreshContent() {
        trafficRules.clear();

        Streams.concat(
            innerFilter.getPortRecords()
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new TrafficRule(e.getValue())),
            innerFilter.getHostRecords()
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new TrafficRule(e.getValue())),
            innerFilter.getAddressRecords()
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .flatMap(m -> m.entrySet().stream())
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new TrafficRule(e.getValue()))
        )
        .forEach(trafficRules::add);

        checkNotNull(trafficRulesTable).refresh();

        try {
            AddressFilterList addressFilterList = innerFilter.getFullList();

            StringWriter writer = new StringWriter();
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
                    .registerModule(new GuavaModule());
            mapper.writeValue(writer, addressFilterList);

            String filterList = writer.getBuffer().toString();

            Preferences userPreferences = Preferences.userRoot();
            userPreferences.put(TRAFFIC_RULES_PREF, filterList);
        } catch (Exception e) {}
    }

    public void newRule() {
        try {
            TrafficRuleAddDialog trafficRuleAddDialog = new TrafficRuleAddDialog();
            Stage workspaceStage = ModalWindow.showModal(checkNotNull(stage),
                    stage -> { trafficRuleAddDialog.setStage(stage); return trafficRuleAddDialog; },
                    "Add new traffic rule");

            workspaceStage.setOnHidden(
                    ev -> {
                        try {
                            TrafficRule trafficRule = trafficRuleAddDialog.getTrafficRule();
                            if (trafficRule != null) {
                                addTrafficRule(trafficRule);
                                refreshContent();
                            }
                        } catch (Exception e) {
                            Alert alert = new Alert(Alert.AlertType.ERROR, "Error adding known server: " + e, ButtonType.OK);
                            LOGGER.error("Error adding known server: ", e);
                            alert.showAndWait();
                        }
                    }
            );
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Error adding known server: " + e, ButtonType.OK);
            LOGGER.error("Error adding known server: ", e);
            alert.showAndWait();
        }

        refreshContent();
    }

    private void addTrafficRule(TrafficRule trafficRule) {
        if (trafficRule.addressRecord != null) {
            innerFilter.addAddressRecord(trafficRule.addressRecord, true);
        } else if (trafficRule.hostRecord != null) {
            innerFilter.addHostRecord(trafficRule.hostRecord, true);
        } else if (trafficRule.portRecord != null) {
            innerFilter.addPortRecord(trafficRule.portRecord, true);
        }
        refreshContent();
    }

    public void clearRules() {
        if (JavaFxUtils.showYesNoDialog("Delete all rules?") == JavaFxUtils.YesNo.YES) {
            innerFilter.clear();
            refreshContent();
        }
    }

    public void removeRule() {
        TrafficRule trafficRule = checkNotNull(trafficRulesTable).getSelectionModel().getSelectedItem();
        if (trafficRule != null) {
            if (trafficRule.addressRecord != null) {
                innerFilter.removeAddressRecord(trafficRule.addressRecord);
            } else if (trafficRule.hostRecord != null) {
                innerFilter.removeHostRecord(trafficRule.hostRecord);
            } else if (trafficRule.portRecord != null) {
                innerFilter.removePortRecord(trafficRule.portRecord);
            }
        }

        refreshAndRestoreCursor();
    }

    void refreshAndRestoreCursor() {
        int selectedIndex = checkNotNull(trafficRulesTable).getSelectionModel().getSelectedIndex();
        refreshContent();
        if (selectedIndex >= trafficRulesTable.itemsProperty().get().size()) {
            selectedIndex = trafficRulesTable.itemsProperty().get().size() - 1;
        }
        if (selectedIndex > 0) {
            checkNotNull(trafficRulesTable).getSelectionModel().select(selectedIndex);
        }
    }

    FilterType flipFilterType(FilterType type) {
        switch (type) {
            case BLACKLIST: return FilterType.WHITELIST;
            case WHITELIST: return FilterType.BLACKLIST;
            default: throw new IllegalArgumentException("FilterType should be either Blacklist or Whitelist");
        }
    }

    public void flipRule() {
        TrafficRule trafficRule = checkNotNull(trafficRulesTable).getSelectionModel().getSelectedItem();
        if (trafficRule != null) {
            if (trafficRule.addressRecord != null) {
                innerFilter.addAddressRecord(ImmutableAddressRecord.builder()
                        .dstHost(trafficRule.addressRecord.dstHost())
                        .dstPort(trafficRule.addressRecord.dstPort())
                        .filterType(flipFilterType(trafficRule.addressRecord.filterType()))
                        .creationTimestamp(System.currentTimeMillis())
                        .build(),
                        true);
            } else if (trafficRule.hostRecord != null) {
                innerFilter.removeHostRecord(trafficRule.hostRecord);
                innerFilter.addHostRecord(ImmutableHostRecord.builder()
                        .dstHost(trafficRule.hostRecord.dstHost())
                        .filterType(flipFilterType(trafficRule.hostRecord.filterType()))
                        .creationTimestamp(System.currentTimeMillis())
                        .build(),
                        true);
            } else if (trafficRule.portRecord != null) {
                innerFilter.removePortRecord(trafficRule.portRecord);
                innerFilter.addPortRecord(ImmutablePortRecord.builder()
                        .dstPort(trafficRule.portRecord.dstPort())
                        .filterType(flipFilterType(trafficRule.portRecord.filterType()))
                        .creationTimestamp(System.currentTimeMillis())
                        .build(),
                        true);
            }
        }
        refreshAndRestoreCursor();
    }

    public void saveRules() {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Traffic filter ruleset (*.rls)", "*.rls"));
            fileChooser.setTitle("Save Traffic filter ruleset");
            File configFile = fileChooser.showSaveDialog(checkNotNull(stage));

            if (configFile != null) {
                if (!configFile.getName().endsWith(".rls")) {
                    configFile = new File(configFile.getPath()  + ".rls");
                }
                AddressFilterList addressFilterList = innerFilter.getFullList();

                ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
                        .registerModule(new GuavaModule());
                mapper.writeValue(configFile, addressFilterList);

                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Traffic filter ruleset saved to : " + configFile.getPath(), ButtonType.OK);
                LOGGER.error("Traffic filter ruleset saved to : " + configFile.getPath());
                alert.showAndWait();
            }
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Error saving Traffic filter ruleset: " + e, ButtonType.OK);
            LOGGER.error("Error saving Traffic filter ruleset: ", e);
            alert.showAndWait();
        }
    }

    public void loadRules() {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Traffic filter ruleset (*.rls)", "*.rls"));
            fileChooser.setTitle("Open Traffic filter ruleset");

            File configFile = fileChooser.showOpenDialog(checkNotNull(stage));

            if (configFile != null) {
                ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
                        .registerModule(new GuavaModule());

                AddressFilterList addressFilterList = mapper.readValue(configFile, AddressFilterList.class);

                innerFilter.clear();
                innerFilter.addList(addressFilterList, true);
            }
            refreshContent();
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Error loading config file: " + e, ButtonType.OK);
            LOGGER.error("Error loading config file: ", e);
            alert.showAndWait();
        }
    }
}
