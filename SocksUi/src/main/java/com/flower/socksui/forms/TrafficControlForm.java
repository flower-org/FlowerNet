package com.flower.socksui.forms;

import com.flower.conntrack.ConnectionListenerAndFilter;
import com.flower.conntrack.whiteblacklist.FilterType;
import com.flower.conntrack.whiteblacklist.ImmutableAddressRecord;
import com.flower.conntrack.whiteblacklist.ImmutableHostRecord;
import com.flower.conntrack.whiteblacklist.ImmutablePortRecord;
import com.flower.conntrack.whiteblacklist.WhitelistBlacklistConnectionFilter;
import com.flower.socksui.ModalWindow;
import com.google.common.collect.Streams;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableView;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.flower.conntrack.whiteblacklist.AddressFilterList.AddressRecord;
import static com.flower.conntrack.whiteblacklist.AddressFilterList.HostRecord;
import static com.flower.conntrack.whiteblacklist.AddressFilterList.PortRecord;

public class TrafficControlForm extends AnchorPane implements Refreshable, ConnectionListenerAndFilter {
    final static Logger LOGGER = LoggerFactory.getLogger(TrafficControlForm.class);

    final static String OFF = "Off";
    final static String WHITELIST = "Whitelist";
    final static String BLACKLIST = "Blacklist";

    public static class CapturedRequest {
        final String host;
        final Integer port;
        final AddressCheck filterResult;

        public CapturedRequest(String host, Integer port, AddressCheck filterResult) {
            this.host = host;
            this.port = port;
            this.filterResult = filterResult;
        }

        public String getHost() { return host; }

        public int port() { return port; }

        public String getPort() { return port > 0 ? Integer.toString(port) : ""; }

        public String getFilterResult() {
            return filterResult == AddressCheck.CONNECTION_ALLOWED ? "Allowed" : "Prohibited";
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
        public Integer getPort() {
            if (addressRecord != null) {
                return addressRecord.dstPort();
            } else if (hostRecord != null) {
                return -1;
            } else if (portRecord != null) {
                return portRecord.dstPort();
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

        innerFilter = new WhitelistBlacklistConnectionFilter(getFilterType());

        capturedRequests = FXCollections.observableArrayList();
        checkNotNull(capturedRequestsTable).itemsProperty().set(capturedRequests);

        trafficRules = FXCollections.observableArrayList();
        checkNotNull(trafficRulesTable).itemsProperty().set(trafficRules);

        refreshContent();
    }

    public FilterType getFilterType() {
        String modeStr = checkNotNull(filteringModeComboBox).getSelectionModel().getSelectedItem();
        switch (modeStr) {
            case OFF: return FilterType.OFF;
            case BLACKLIST: return FilterType.BLACKLIST;
            case WHITELIST:
            default: return FilterType.WHITELIST;
        }
    }

    @Override
    public AddressCheck approveConnection(String dstHost, int dstPort) {
        AddressCheck checkResult = innerFilter.approveConnection(dstHost, dstPort);

        if (checkNotNull(captureRequestsCheckBox).selectedProperty().get()) {
            CapturedRequest capturedRequest = new CapturedRequest(dstHost, dstPort, checkResult);
            capturedRequests.add(capturedRequest);
            checkNotNull(capturedRequestsTable).refresh();
        }

        return checkResult;
    }

    public void filteringModeChanged() {
        innerFilter.setFilterType(getFilterType());
    }

    public void clearCapturedData() {
        capturedRequests.clear();
    }

    public void whitelistCapture() {
        CapturedRequest capturedRequest = checkNotNull(capturedRequestsTable).getSelectionModel().getSelectedItem();
        if (capturedRequest != null) {
            AddressRecord addressRecord = ImmutableAddressRecord.builder()
                    .dstHost(capturedRequest.getHost())
                    .dstPort(capturedRequest.port())
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
                    .dstPort(capturedRequest.port())
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
                    .dstPort(capturedRequest.port())
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
                    .dstPort(capturedRequest.port())
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
        refreshContent();
    }

    public void saveRules() {
        //
    }

    public void loadRules() {
        //
    }
}
