package com.flower.socksui.forms;

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
import com.flower.socksui.ModalWindow;
import com.google.common.collect.Streams;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;
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

    public static class CapturedRequest {
        private final String host;
        private final Integer port;
        private final AddressCheck filterResult;
        /** Ture if not based on any rules, but based on general policy Whitelist/Blacklist */
        private final boolean isDefault;

        public CapturedRequest(String host, Integer port, AddressCheck filterResult, boolean isDefault) {
            this.host = host;
            this.port = port;
            this.filterResult = filterResult;
            this.isDefault = isDefault;
        }

        public String getHost() { return host; }

        public int getPort() { return port; }

        public String getFilterResult() {
            return (filterResult == AddressCheck.CONNECTION_ALLOWED ? "Allowed" : "Prohibited")
                    + (isDefault ? " (default)" : " (rule match)");
        }
    }

    public static class TrafficRule {
        @Nullable final AddressRecord addressRecord;
        @Nullable final HostRecord hostRecord;
        @Nullable final PortRecord portRecord;

        public TrafficRule(@Nullable AddressRecord addressRecord) {
            this.addressRecord = addressRecord;
            this.hostRecord = null;
            this.portRecord = null;
        }

        public TrafficRule(@Nullable HostRecord hostRecord) {
            this.addressRecord = null;
            this.hostRecord = hostRecord;
            this.portRecord = null;
        }

        public TrafficRule(@Nullable PortRecord portRecord) {
            this.addressRecord = null;
            this.hostRecord = null;
            this.portRecord = portRecord;
        }

        public FilterType getFilterType() {
            if (addressRecord != null) {
                return addressRecord.filterType();
            } else if (hostRecord != null) {
                return hostRecord.filterType();
            } else if (portRecord != null) {
                return portRecord.filterType();
            } else {
                throw new IllegalStateException("Either addressRecord or hostRecord or portRecord should be not null");
            }
        }
        public String getHost() {
            if (addressRecord != null) {
                return addressRecord.dstHost();
            } else if (hostRecord != null) {
                return hostRecord.dstHost();
            } else if (portRecord != null) {
                return "";
            } else {
                throw new IllegalStateException("Either addressRecord or hostRecord or portRecord should be not null");
            }
        }
        public String getPort() {
            if (addressRecord != null) {
                return Integer.toString(addressRecord.dstPort());
            } else if (hostRecord != null) {
                return "";
            } else if (portRecord != null) {
                return Integer.toString(portRecord.dstPort());
            } else {
                throw new IllegalStateException("Either addressRecord or hostRecord or portRecord should be not null");
            }
        }
    }

    @Nullable Stage stage;
    @Nullable @FXML ComboBox<String> filteringModeComboBox;
    @Nullable @FXML CheckBox captureRequestsCheckBox;
    @Nullable @FXML TableView<CapturedRequest> capturedRequestsTable;
    final ObservableList<CapturedRequest> capturedRequests;
    @Nullable @FXML TableView<TrafficRule> trafficRulesTable;
    final ObservableList<TrafficRule> trafficRules;
    @Nullable @FXML TextField maxRequests;
    @Nullable @FXML CheckBox unmatchedOnlyCheckBox;

    final WhitelistBlacklistConnectionFilter innerFilter;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public TrafficControlForm() {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("TrafficControlForm.fxml"));
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }

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

    @Override
    public AddressCheck approveConnection(String dstHost, int dstPort) {
        AddressCheck checkResult;
        boolean isDefault;

        TrafficControlType trafficControlType = getTrafficControlType();
        if (trafficControlType == TrafficControlType.OFF) {
            // If filtering is off, we allow all connections
            isDefault = true;
            checkResult = AddressCheck.CONNECTION_ALLOWED;
        } else if (trafficControlType == TrafficControlType.WHITELIST) {
            isDefault = false;
            checkResult = innerFilter.getRecordRule(dstHost, dstPort);

            // If matching record not found, we prohibit anything that's not whitelisted
            if (checkResult == null) {
                isDefault = true;
                checkResult = AddressCheck.CONNECTION_PROHIBITED;
            }
        } else if (trafficControlType == TrafficControlType.BLACKLIST) {
            isDefault = false;
            checkResult = innerFilter.getRecordRule(dstHost, dstPort);

            // If matching records not found, we allow everything that's not blacklisted
            if (checkResult == null) {
                isDefault = true;
                checkResult = AddressCheck.CONNECTION_ALLOWED;
            }
        } else {
            throw new IllegalArgumentException("Unknown traffic control type: " + trafficControlType);
        }

        final AddressCheck finalCheckResult = checkResult;
        final boolean finalIsDefault = isDefault;
        Platform.runLater(() -> {
            if (checkNotNull(captureRequestsCheckBox).selectedProperty().get()) {
                if (!checkNotNull(unmatchedOnlyCheckBox).selectedProperty().get() || finalIsDefault) {
                    CapturedRequest capturedRequest = new CapturedRequest(dstHost, dstPort, finalCheckResult, finalIsDefault);
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

        return checkResult;
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
                    .build();
            innerFilter.addAddressRecord(addressRecord, true);
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
                    .build();
            innerFilter.addAddressRecord(addressRecord, true);
            refreshContent();
        }
    }

    public void whitelistCaptureHost() {
        CapturedRequest capturedRequest = checkNotNull(capturedRequestsTable).getSelectionModel().getSelectedItem();
        if (capturedRequest != null) {
            HostRecord hostRecord = ImmutableHostRecord.builder()
                    .dstHost(capturedRequest.getHost())
                    .filterType(FilterType.WHITELIST)
                    .build();
            innerFilter.addHostRecord(hostRecord, true);
            refreshContent();
        }
    }

    public void blacklistCaptureHost() {
        CapturedRequest capturedRequest = checkNotNull(capturedRequestsTable).getSelectionModel().getSelectedItem();
        if (capturedRequest != null) {
            HostRecord hostRecord = ImmutableHostRecord.builder()
                    .dstHost(capturedRequest.getHost())
                    .filterType(FilterType.BLACKLIST)
                    .build();
            innerFilter.addHostRecord(hostRecord, true);
            refreshContent();
        }
    }

    public void whitelistCapturePort() {
        CapturedRequest capturedRequest = checkNotNull(capturedRequestsTable).getSelectionModel().getSelectedItem();
        if (capturedRequest != null) {
            PortRecord portRecord = ImmutablePortRecord.builder()
                    .dstPort(capturedRequest.getPort())
                    .filterType(FilterType.WHITELIST)
                    .build();
            innerFilter.addPortRecord(portRecord, true);
            refreshContent();
        }
    }

    public void blacklistCapturePort() {
        CapturedRequest capturedRequest = checkNotNull(capturedRequestsTable).getSelectionModel().getSelectedItem();
        if (capturedRequest != null) {
            PortRecord portRecord = ImmutablePortRecord.builder()
                    .dstPort(capturedRequest.getPort())
                    .filterType(FilterType.BLACKLIST)
                    .build();
            innerFilter.addPortRecord(portRecord, true);
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
        innerFilter.clear();
        refreshContent();
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
                        .build(),
                        true);
            } else if (trafficRule.hostRecord != null) {
                innerFilter.removeHostRecord(trafficRule.hostRecord);
                innerFilter.addHostRecord(ImmutableHostRecord.builder()
                        .dstHost(trafficRule.hostRecord.dstHost())
                        .filterType(flipFilterType(trafficRule.hostRecord.filterType()))
                        .build(),
                        true);
            } else if (trafficRule.portRecord != null) {
                innerFilter.removePortRecord(trafficRule.portRecord);
                innerFilter.addPortRecord(ImmutablePortRecord.builder()
                        .dstPort(trafficRule.portRecord.dstPort())
                        .filterType(flipFilterType(trafficRule.portRecord.filterType()))
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
