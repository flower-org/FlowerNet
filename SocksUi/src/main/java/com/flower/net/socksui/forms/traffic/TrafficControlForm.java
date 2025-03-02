package com.flower.net.socksui.forms.traffic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.flower.net.conntrack.ConnectionFilter;
import com.flower.net.config.access.Access;
import com.flower.net.conntrack.allowdenylist.AddressFilterList;
import com.flower.net.conntrack.allowdenylist.ImmutableAddressRecord;
import com.flower.net.conntrack.allowdenylist.ImmutableHostRecord;
import com.flower.net.conntrack.allowdenylist.ImmutablePortRecord;
import com.flower.net.conntrack.allowdenylist.AllowDenyConnectionFilter;
import com.flower.net.socksui.JavaFxUtils;
import com.flower.net.socksui.MainApp;
import com.flower.net.socksui.forms.ConnectionMonitorForm;
import com.flower.net.socksui.forms.Refreshable;
import com.flower.net.utils.IpAddressUtil;
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
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.SocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.prefs.Preferences;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.flower.net.conntrack.allowdenylist.AddressFilterList.AddressRecord;
import static com.flower.net.conntrack.allowdenylist.AddressFilterList.HostRecord;
import static com.flower.net.conntrack.allowdenylist.AddressFilterList.PortRecord;

public class TrafficControlForm extends AnchorPane implements Refreshable, ConnectionFilter, TrafficController {
    final static Logger LOGGER = LoggerFactory.getLogger(TrafficControlForm.class);
    final static String TRAFFIC_RULES_PREF = "trafficRulesPref";
    final static String TMP_TRAFFIC_RULES_PREF = "tmpTrafficRulesPref";
    final static String CAPTURE_FLAG_PREF = "captureFlagPref";

    final static String ALLOW = "Allow";
    final static String DENY = "Deny";

    final static String INACTIVE_FILTER_LIST_MODE = "Inactive";
    final static String TMP_WITH_MAIN_LIST_FILTER_LIST_MODE = "With main list";
    final static String EXCLUSIVE_TMP_LIST_FILTER_LIST_MODE = "Exclusive";

    final static String AUTO_ADD_NONE = "No auto-add";
    final static String AUTO_ADD_ALLOW = "Auto-Allow";
    final static String AUTO_ADD_DENY = "Auto-Deny";

    @Nullable Stage stage;
    @Nullable @FXML ComboBox<String> filteringModeComboBox;
    @Nullable @FXML CheckBox captureRequestsCheckBox;
    @Nullable @FXML TableView<CapturedRequest> capturedRequestsTable;
    final ObservableList<CapturedRequest> capturedRequests;
    @Nullable @FXML TableView<TrafficRule> trafficRulesTable;
    final ObservableList<TrafficRule> trafficRules;
    @Nullable @FXML TableView<TrafficRule> tmpTrafficRulesTable;
    final ObservableList<TrafficRule> tmpTrafficRules;
    @Nullable @FXML TextField maxRequests;
    @Nullable @FXML CheckBox allowDirectIpAccessCheckBox;
    @Nullable @FXML MenuButton captureFilterMenuButton;
    @Nullable @FXML ComboBox<String> activateComboBox;
    @Nullable @FXML ComboBox<String> autoAddComboBox;

    final AtomicLong totalConnections;
    final AtomicLong allowedConnections;
    final AtomicLong prohibitedConnections;
    final MainApp mainForm;

    @Nullable ConnectionMonitorForm connectionMonitorForm;

    RuleManager mainRuleManager;
    RuleManager tmpRuleManager;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setConnectionMonitorForm(ConnectionMonitorForm connectionMonitorForm) {
        this.connectionMonitorForm = connectionMonitorForm;
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

        capturedRequests = FXCollections.observableArrayList();
        checkNotNull(capturedRequestsTable).itemsProperty().set(capturedRequests);

        trafficRules = FXCollections.observableArrayList();
        checkNotNull(trafficRulesTable).itemsProperty().set(trafficRules);

        tmpTrafficRules = FXCollections.observableArrayList();
        checkNotNull(tmpTrafficRulesTable).itemsProperty().set(tmpTrafficRules);

        AllowDenyConnectionFilter innerFilter = new AllowDenyConnectionFilter();
        innerFilter.clear();

        Preferences userPreferences = Preferences.userRoot();
        try {
            String trafficRules = userPreferences.get(TRAFFIC_RULES_PREF, "");
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
                    .registerModule(new GuavaModule());
            AddressFilterList addressFilterList = mapper.readValue(trafficRules, AddressFilterList.class);
            innerFilter.addList(addressFilterList, true);
        } catch (Exception e) {}

        AllowDenyConnectionFilter tmpRulesFilter = new AllowDenyConnectionFilter();
        tmpRulesFilter.clear();
        try {
            String trafficRules = userPreferences.get(TMP_TRAFFIC_RULES_PREF, "");
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
                    .registerModule(new GuavaModule());
            AddressFilterList tmpAddressFilterList = mapper.readValue(trafficRules, AddressFilterList.class);
            tmpRulesFilter.addList(tmpAddressFilterList, true);
        } catch (Exception e) {}

        String captureFlagStr = userPreferences.get(CAPTURE_FLAG_PREF, "");
        boolean captureFlag = false;
        if (!StringUtils.isBlank(captureFlagStr)) {
            try {
                captureFlag = Boolean.parseBoolean(captureFlagStr);
            } catch (Exception e) { }
        }
        checkNotNull(captureRequestsCheckBox).selectedProperty().set(captureFlag);

        mainRuleManager = new RuleManager(innerFilter) {
            @Override
            void refreshAndRestoreCursor() { refreshAndRestoreTableCursor(); }

            @Override
            void checkDeniedHosts() { checkNotNull(connectionMonitorForm).checkDeniedHosts(); }

            @Override
            @Nullable TrafficRule getSelectedTrafficRule() { return checkNotNull(trafficRulesTable).getSelectionModel().getSelectedItem(); }

            @Override
            @Nullable Stage getStage() { return stage; }
        };

        tmpRuleManager = new RuleManager(tmpRulesFilter) {
            @Override
            void refreshAndRestoreCursor() { refreshAndRestoreTableCursor(); }

            @Override
            void checkDeniedHosts() { checkNotNull(connectionMonitorForm).checkDeniedHosts(); }

            @Override
            @Nullable TrafficRule getSelectedTrafficRule() { return checkNotNull(tmpTrafficRulesTable).getSelectionModel().getSelectedItem(); }

            @Override
            @Nullable Stage getStage() { return stage; }

            @Override protected String clearRulesMsg() { return "Delete all temporary rules?"; }

            @Override protected String clearAllowRulesMsg() { return "Delete all temporary Allow rules?"; }

            @Override protected String clearDenyRulesMsg() { return "Delete all temporary Deny rules?"; }
        };

        refreshContent();

        captureFilterChange();
    }

    public enum FilterListMode {
        MAIN,
        MAIN_AND_TMP,
        TMP
    }

    FilterListMode getFilterListMode() {
        switch (checkNotNull(activateComboBox).getSelectionModel().getSelectedItem()) {
            case INACTIVE_FILTER_LIST_MODE:
                return FilterListMode.MAIN;
            case TMP_WITH_MAIN_LIST_FILTER_LIST_MODE:
                return FilterListMode.MAIN_AND_TMP;
            case EXCLUSIVE_TMP_LIST_FILTER_LIST_MODE:
                return FilterListMode.TMP;
            default:
                throw new IllegalStateException("Unknown filter list mode");
        }
    }

    public enum AutoAddRulesMode {
        NONE,
        AUTO_ALLOW,
        AUTO_DENY
    }

    AutoAddRulesMode getAutoAddRulesMode() {
        switch (checkNotNull(autoAddComboBox).getSelectionModel().getSelectedItem()) {
            case AUTO_ADD_NONE:
                return AutoAddRulesMode.NONE;
            case AUTO_ADD_ALLOW:
                return AutoAddRulesMode.AUTO_ALLOW;
            case AUTO_ADD_DENY:
                return AutoAddRulesMode.AUTO_DENY;
            default:
                throw new IllegalStateException("Unknown Auto-add mode");
        }
    }

    public Access getDefaultAccessType() {
        String modeStr = checkNotNull(filteringModeComboBox).getSelectionModel().getSelectedItem();
        switch (modeStr) {
            case DENY: return Access.DENY;
            case ALLOW:
            default: return Access.ALLOW;
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

    public void filterListModeChange() {
        checkNotNull(connectionMonitorForm).checkDeniedHosts();
    }

    static class CheckResult {
        final Access checkResult;
        final boolean isDirectIpBlock;
        final boolean isRuleMatched;

        CheckResult(Access checkResult, boolean isDirectIpBlock, boolean isRuleMatched) {
            this.checkResult = checkResult;
            this.isDirectIpBlock = isDirectIpBlock;
            this.isRuleMatched = isRuleMatched;
        }
    }

    CheckResult getCheckResult(String dstHost, int dstPort) {
        FilterListMode filterListMode = getFilterListMode();
        Access checkResult;
        if (filterListMode == FilterListMode.MAIN) {
            checkResult = mainRuleManager.filter.getRecordRule(dstHost, dstPort);
        } else if (filterListMode == FilterListMode.TMP) {
            checkResult = tmpRuleManager.filter.getRecordRule(dstHost, dstPort);
        } else if (filterListMode == FilterListMode.MAIN_AND_TMP) {
            checkResult = tmpRuleManager.filter.getRecordRule(dstHost, dstPort);
            if (checkResult == null) {
                checkResult = mainRuleManager.filter.getRecordRule(dstHost, dstPort);
            }
        } else {
            throw new IllegalStateException("Unknown filter list mode");
        }

        boolean isDirectIpBlock = false;
        boolean isRuleMatched;

        Access defaultAccessType = getDefaultAccessType();
        boolean isDirectIpAccessAllowed = checkNotNull(allowDirectIpAccessCheckBox).selectedProperty().get();
        if (!isDirectIpAccessAllowed && IpAddressUtil.isIPAddress(dstHost)) {
            isDirectIpBlock = true;
            isRuleMatched = false;
            checkResult = Access.DENY;
        } else {
            isRuleMatched = true;
            // If matching record not found, we fall back to defaultAccessType
            if (checkResult == null) {
                isRuleMatched = false;
                checkResult = defaultAccessType;
            }
        }

        return new CheckResult(checkResult, isDirectIpBlock, isRuleMatched);
    }

    @Override
    public Access approveConnection(String dstHost, int dstPort, @Nullable SocketAddress from) {
        CheckResult check = getCheckResult(dstHost, dstPort);

        if (!check.isRuleMatched) {
            AutoAddRulesMode autoAddRulesMode = getAutoAddRulesMode();
            if (autoAddRulesMode != AutoAddRulesMode.NONE) {
                AddressRecord addressRecord = ImmutableAddressRecord.builder()
                        .dstHost(dstHost)
                        .dstPort(dstPort)
                        .access(autoAddRulesMode == AutoAddRulesMode.AUTO_ALLOW ? Access.ALLOW : Access.DENY)
                        .creationTimestamp(System.currentTimeMillis())
                        .isWildcard(false)
                        .build();
                AddressRecord oldRule = tmpRuleManager.filter.addAddressRecord(addressRecord, false);

                // We need this check, because even when tmp list is inactive we still want to auto-add.
                if (oldRule == null) {
                    Platform.runLater(this::refreshContent);
                    return approveConnection(dstHost, dstPort, from);
                }
            }
        }

        if (from != null) {
            Platform.runLater(() -> {
                if (checkNotNull(captureRequestsCheckBox).selectedProperty().get()) {
                    CaptureFilter captureFilter = getCaptureFilter();
                    if (captureFilter.matchCapturedRecord(check.checkResult, check.isRuleMatched)) {
                        CapturedRequest capturedRequest = new CapturedRequest(dstHost, dstPort, check.checkResult,
                                check.isRuleMatched, check.isDirectIpBlock, from);
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

            totalConnections.incrementAndGet();
            if (check.checkResult == Access.ALLOW) {
                allowedConnections.incrementAndGet();
            } else {
                prohibitedConnections.incrementAndGet();
            }

            Platform.runLater(this::updateConnectionStats);
        }

        return check.checkResult;
    }

    public void updateConnectionStats() {
        mainForm.setConnectionsText(
                String.format("Connections: total: %d allowed: %d prohibited: %d",
                        totalConnections.get(), allowedConnections.get(), prohibitedConnections.get())
        );
    }

    @Override
    public void clearCapturedData() {
        capturedRequests.clear();
        checkNotNull(trafficRulesTable).refresh();
    }

    public void allowCapture() {
        CapturedRequest capturedRequest = checkNotNull(capturedRequestsTable).getSelectionModel().getSelectedItem();
        if (capturedRequest != null) {
            allow(capturedRequest.getHost(), capturedRequest.getPort());
        }
    }

    @Override
    public void allow(String host, int port) {
        RuleManager activeRuleManager = getActiveRuleManager();

        AddressRecord addressRecord = ImmutableAddressRecord.builder()
                .dstHost(host)
                .dstPort(port)
                .access(Access.ALLOW)
                .creationTimestamp(System.currentTimeMillis())
                .isWildcard(false)
                .build();
        AddressRecord existingRule = activeRuleManager.filter.addAddressRecord(addressRecord, false);
        if (existingRule != null && !AddressRecord.recordsEqual(existingRule, addressRecord)) {
            //Ask to reload
            if (JavaFxUtils.showYesNoDialog("Overwrite existing rule?") == JavaFxUtils.YesNo.YES) {
                activeRuleManager.filter.addAddressRecord(addressRecord, true);
            }
        }
        refreshContent();
    }

    public void denyCapture() {
        CapturedRequest capturedRequest = checkNotNull(capturedRequestsTable).getSelectionModel().getSelectedItem();
        if (capturedRequest != null) {
            deny(capturedRequest.getHost(), capturedRequest.getPort());
        }
    }

    @Override
    public void deny(String host, int port) {
        RuleManager activeRuleManager = getActiveRuleManager();

        AddressRecord addressRecord = ImmutableAddressRecord.builder()
                .dstHost(host)
                .dstPort(port)
                .access(Access.DENY)
                .creationTimestamp(System.currentTimeMillis())
                .isWildcard(false)
                .build();
        AddressRecord existingRule = activeRuleManager.filter.addAddressRecord(addressRecord, false);
        if (existingRule != null && !AddressRecord.recordsEqual(existingRule, addressRecord)) {
            //Ask to reload
            if (JavaFxUtils.showYesNoDialog("Overwrite existing rule?") == JavaFxUtils.YesNo.YES) {
                activeRuleManager.filter.addAddressRecord(addressRecord, true);
            }
        }
        checkNotNull(connectionMonitorForm).checkDeniedHosts();
        refreshContent();
    }

    public void allowCaptureHost() {
        CapturedRequest capturedRequest = checkNotNull(capturedRequestsTable).getSelectionModel().getSelectedItem();
        if (capturedRequest != null) {
            allowHost(capturedRequest.getHost());
        }
    }

    @Override
    public void allowHost(String host) {
        RuleManager activeRuleManager = getActiveRuleManager();

        HostRecord hostRecord = ImmutableHostRecord.builder()
                .dstHost(host)
                .access(Access.ALLOW)
                .creationTimestamp(System.currentTimeMillis())
                .isWildcard(false)
                .build();
        HostRecord existingRule = activeRuleManager.filter.addHostRecord(hostRecord, false);
        if (existingRule != null && !HostRecord.recordsEqual(existingRule, hostRecord)) {
            //Ask to reload
            if (JavaFxUtils.showYesNoDialog("Overwrite existing rule?") == JavaFxUtils.YesNo.YES) {
                activeRuleManager.filter.addHostRecord(hostRecord, true);
            }
        }
        refreshContent();
    }

    public void denyCaptureHost() {
        CapturedRequest capturedRequest = checkNotNull(capturedRequestsTable).getSelectionModel().getSelectedItem();
        if (capturedRequest != null) {
            denyHost(capturedRequest.getHost());
        }
    }

    @Override
    public void denyHost(String host) {
        RuleManager activeRuleManager = getActiveRuleManager();

        HostRecord hostRecord = ImmutableHostRecord.builder()
                .dstHost(host)
                .access(Access.DENY)
                .creationTimestamp(System.currentTimeMillis())
                .isWildcard(false)
                .build();
        HostRecord existingRule = activeRuleManager.filter.addHostRecord(hostRecord, false);
        if (existingRule != null && !HostRecord.recordsEqual(existingRule, hostRecord)) {
            //Ask to reload
            if (JavaFxUtils.showYesNoDialog("Overwrite existing rule?") == JavaFxUtils.YesNo.YES) {
                activeRuleManager.filter.addHostRecord(hostRecord, true);
            }
        }
        checkNotNull(connectionMonitorForm).checkDeniedHosts();
        refreshContent();
    }

    public void allowCapturePort() {
        CapturedRequest capturedRequest = checkNotNull(capturedRequestsTable).getSelectionModel().getSelectedItem();
        if (capturedRequest != null) {
            allowPort(capturedRequest.getPort());
        }
    }

    @Override
    public void allowPort(int port) {
        RuleManager activeRuleManager = getActiveRuleManager();

        PortRecord portRecord = ImmutablePortRecord.builder()
                .dstPort(port)
                .access(Access.ALLOW)
                .creationTimestamp(System.currentTimeMillis())
                .build();
        PortRecord existingRule = activeRuleManager.filter.addPortRecord(portRecord, false);
        if (existingRule != null && !PortRecord.recordsEqual(existingRule, portRecord)) {
            //Ask to reload
            if (JavaFxUtils.showYesNoDialog("Overwrite existing rule?") == JavaFxUtils.YesNo.YES) {
                activeRuleManager.filter.addPortRecord(portRecord, true);
            }
        }
        refreshContent();
    }

    public void denyCapturePort() {
        CapturedRequest capturedRequest = checkNotNull(capturedRequestsTable).getSelectionModel().getSelectedItem();
        if (capturedRequest != null) {
            denyPort(capturedRequest.getPort());
        }
    }

    RuleManager getActiveRuleManager() {
        FilterListMode listMode = getFilterListMode();
        RuleManager ruleManager;
        if (listMode.equals(FilterListMode.MAIN)) {
            ruleManager = mainRuleManager;
        } else {
            ruleManager = tmpRuleManager;
        }
        return ruleManager;
    }

    @Override
    public void denyPort(int port) {
        RuleManager activeRuleManager = getActiveRuleManager();

        PortRecord portRecord = ImmutablePortRecord.builder()
                .dstPort(port)
                .access(Access.DENY)
                .creationTimestamp(System.currentTimeMillis())
                .build();
        PortRecord existingRule = activeRuleManager.filter.addPortRecord(portRecord, false);
        if (existingRule != null && !PortRecord.recordsEqual(existingRule, portRecord)) {
            //Ask to reload
            if (JavaFxUtils.showYesNoDialog("Overwrite existing rule?") == JavaFxUtils.YesNo.YES) {
                activeRuleManager.filter.addPortRecord(portRecord, true);
            }
        }
        checkNotNull(connectionMonitorForm).checkDeniedHosts();
        refreshContent();
    }

    List<TrafficRule> getRulesList(RuleManager ruleManager) {
        return Streams.concat(
                ruleManager.filter.getPortRecords()
                    .entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> new TrafficRule(e.getValue())),
                ruleManager.filter.getHostRecords()
                    .entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> new TrafficRule(e.getValue())),
                ruleManager.filter.getAddressRecords()
                    .entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(Map.Entry::getValue)
                    .flatMap(m -> m.entrySet().stream())
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> new TrafficRule(e.getValue())),
                ruleManager.filter.getWildcardHostRecords()
                    .entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> new TrafficRule(e.getValue())),
                ruleManager.filter.getWildcardAddressRecords()
                    .entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(Map.Entry::getValue)
                    .flatMap(m -> m.entrySet().stream())
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> new TrafficRule(e.getValue()))
        ).toList();
    }

    @Override
    public void refreshContent() {
        trafficRules.clear();
        trafficRules.addAll(getRulesList(mainRuleManager));
        checkNotNull(trafficRulesTable).refresh();

        tmpTrafficRules.clear();
        tmpTrafficRules.addAll(getRulesList(tmpRuleManager));
        checkNotNull(tmpTrafficRulesTable).refresh();

        Preferences userPreferences = Preferences.userRoot();
        try {
            // Save main rules
            AddressFilterList addressFilterList = mainRuleManager.filter.getFullList();

            StringWriter writer = new StringWriter();
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
                    .registerModule(new GuavaModule());
            mapper.writeValue(writer, addressFilterList);

            String filterList = writer.getBuffer().toString();

            userPreferences.put(TRAFFIC_RULES_PREF, filterList);
        } catch (Exception e) {}

        try {
            // Save tmp rules
            AddressFilterList tmpAddressFilterList = tmpRuleManager.filter.getFullList();

            StringWriter writer = new StringWriter();
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
                    .registerModule(new GuavaModule());
            mapper.writeValue(writer, tmpAddressFilterList);

            String tmpFilterList = writer.getBuffer().toString();

            userPreferences.put(TMP_TRAFFIC_RULES_PREF, tmpFilterList);
        } catch (Exception e) {}
    }

    public void captureFlagChange() {
        Preferences userPreferences = Preferences.userRoot();
        userPreferences.put(CAPTURE_FLAG_PREF, Boolean.toString(checkNotNull(captureRequestsCheckBox).selectedProperty().get()));
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
                AddressFilterList addressFilterList = mainRuleManager.filter.getFullList();

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

                mainRuleManager.filter.clear();
                mainRuleManager.filter.addList(addressFilterList, true);
            }
            refreshContent();
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Error loading config file: " + e, ButtonType.OK);
            LOGGER.error("Error loading config file: ", e);
            alert.showAndWait();
        }
    }

    void refreshAndRestoreTableCursor() {
        int selectedIndex = checkNotNull(trafficRulesTable).getSelectionModel().getSelectedIndex();
        int tmpSelectedIndex = checkNotNull(tmpTrafficRulesTable).getSelectionModel().getSelectedIndex();

        refreshContent();

        if (selectedIndex >= trafficRulesTable.itemsProperty().get().size()) {
            selectedIndex = trafficRulesTable.itemsProperty().get().size() - 1;
        }
        if (selectedIndex >= 0) {
            checkNotNull(trafficRulesTable).getSelectionModel().select(selectedIndex);
        }

        if (tmpSelectedIndex >= tmpTrafficRulesTable.itemsProperty().get().size()) {
            tmpSelectedIndex = tmpTrafficRulesTable.itemsProperty().get().size() - 1;
        }
        if (tmpSelectedIndex >= 0) {
            checkNotNull(tmpTrafficRulesTable).getSelectionModel().select(tmpSelectedIndex);
        }
    }

    // ------------ Traffic rule list ------------

    public void newRule() {
        mainRuleManager.newRule();
    }

    public void removeRule() {
        mainRuleManager.removeRule();
    }

    public void clearRules() {
        mainRuleManager.clearRules();
    }

    public void deriveRule() {
        mainRuleManager.deriveRule();
    }

    public void flipRule() {
        mainRuleManager.flipRule();
    }

    // ------------ Temporary traffic rule list ------------

    public void newTmpRule() {
        tmpRuleManager.newRule();
    }

    public void removeTmpRule() {
        tmpRuleManager.removeRule();
    }

    public void clearTmpRules() {
        tmpRuleManager.clearRules();
    }

    public void clearAllowTmpRules() {
        tmpRuleManager.clearAllowRules();
    }

    public void clearDenyTmpRules() {
        tmpRuleManager.clearDenyRules();
    }

    public void deriveTmpRule() {
        tmpRuleManager.deriveRule();
    }

    public void flipTmpRule() {
        tmpRuleManager.flipRule();
    }

    public void mergeTmpRules() {
        if (JavaFxUtils.showYesNoDialog("Merge temporary filter rule list into main?") == JavaFxUtils.YesNo.YES) {
            boolean overrideExisting = false;
            AddressFilterList tmpRuleList = tmpRuleManager.filter.getFullList();
            if (mainRuleManager.filter.isListOverrideRequired(tmpRuleList)) {
                Optional<Boolean> override = shouldWeOverrideDialog("Override main?",
                        "Some rules conflict with existing rules in the main rule list. Should we override main list rules?");
                if (override.isEmpty()) {
                    return;
                }
                overrideExisting = override.get();
            }

            mainRuleManager.filter.addList(tmpRuleList, overrideExisting);
            tmpRuleManager.filter.clear();

            checkNotNull(activateComboBox).getSelectionModel().select(0);
            checkNotNull(autoAddComboBox).getSelectionModel().select(0);

            refreshContent();
        }
    }

    public final static ButtonType BUTTON_TYPE_OVERRIDE = new ButtonType("Override");
    public final static ButtonType BUTTON_TYPE_IGNORE = new ButtonType("Ignore conflicts");
    public final static ButtonType BUTTON_TYPE_CANCEL = new ButtonType("Cancel");

    public static Optional<Boolean> shouldWeOverrideDialog(String titleHeader, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(titleHeader);
        alert.setHeaderText(titleHeader);
        alert.setContentText(content);

        // Customize the buttons (Yes/No)
        alert.getButtonTypes().setAll(BUTTON_TYPE_OVERRIDE, BUTTON_TYPE_IGNORE, BUTTON_TYPE_CANCEL);
        Optional<ButtonType> dialogResult = alert.showAndWait();
        if (dialogResult.isPresent()) {
            if (dialogResult.get() == BUTTON_TYPE_OVERRIDE) {
                return Optional.of(true);
            } else if (dialogResult.get() == BUTTON_TYPE_IGNORE) {
                return Optional.of(false);
            }
        }
        return Optional.empty();
    }
}
