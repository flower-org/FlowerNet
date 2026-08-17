package com.flower.net.socksui.forms.traffic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.flower.fxutils.JavaFxUtils;
import com.flower.net.conntrack.ConnectionFilter;
import com.flower.net.config.access.Access;
import com.flower.net.conntrack.allowdenylist.AddressFilterList;
import com.flower.net.conntrack.allowdenylist.ImmutableAddressRecord;
import com.flower.net.conntrack.allowdenylist.ImmutableHostRecord;
import com.flower.net.conntrack.allowdenylist.ImmutablePortRecord;
import com.flower.net.conntrack.allowdenylist.AllowDenyConnectionFilter;
import com.flower.net.socksui.MainApp;
import com.flower.net.socksui.forms.ConnectionMonitorForm;
import com.flower.net.socksui.forms.Refreshable;
import com.google.common.collect.Streams;
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

    @Nullable @FXML TabPane filterRulesTabPane;
    @Nullable @FXML ComboBox<String> filteringModeComboBox;
    @Nullable @FXML CheckBox captureRequestsCheckBox;
    @Nullable @FXML TableView<CapturedRequest> capturedRequestsTable;
    final ObservableList<CapturedRequest> capturedRequests;
    @Nullable @FXML TextField maxRequests;
    @Nullable @FXML CheckBox allowDirectIpAccessCheckBox;
    @Nullable @FXML MenuButton captureFilterMenuButton;
    @Nullable @FXML ComboBox<String> autoAddComboBox;

    final AtomicLong totalConnections;
    final AtomicLong allowedConnections;
    final AtomicLong prohibitedConnections;
    final MainApp mainForm;

    @Nullable ConnectionMonitorForm connectionMonitorForm;

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

        AllowDenyConnectionFilter innerFilter = new AllowDenyConnectionFilter();
        innerFilter.clear();

        Preferences userPreferences = Preferences.userRoot();
        String captureFlagStr = userPreferences.get(CAPTURE_FLAG_PREF, "");
        boolean captureFlag = false;
        if (!StringUtils.isBlank(captureFlagStr)) {
            try {
                captureFlag = Boolean.parseBoolean(captureFlagStr);
            } catch (Exception e) { }
        }
        checkNotNull(captureRequestsCheckBox).selectedProperty().set(captureFlag);

        refreshContent();

        captureFilterChange();
    }

    public enum FilterListMode {
        MAIN,
        MAIN_AND_TMP,
        TMP
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

    public boolean isDirectIpAccessAllowed() {
        return checkNotNull(allowDirectIpAccessCheckBox).selectedProperty().get();
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

    @Override
    public Access approveConnection(String dstHost, int dstPort, @Nullable SocketAddress from) {
        // TODO: implement
        //return ruleListManager.approveConnection(dstHost, dstPort, from);
        return Access.ALLOW;
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
        // Why refresh rules? Probably a mistype?
        //checkNotNull(trafficRulesTable).refresh();
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
        getActiveRulesetControl().modified();
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
        getActiveRulesetControl().modified();
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
        getActiveRulesetControl().modified();
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
        getActiveRulesetControl().modified();
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
        getActiveRulesetControl().modified();
    }

    public void denyCapturePort() {
        CapturedRequest capturedRequest = checkNotNull(capturedRequestsTable).getSelectionModel().getSelectedItem();
        if (capturedRequest != null) {
            denyPort(capturedRequest.getPort());
        }
    }

    protected RuleManager getActiveRuleManager() {
        FilterRulesAnchorPane activeRuleset = getActiveRulesetControl();
        return activeRuleset.mainRuleManager;
    }

    protected FilterRulesAnchorPane getActiveRulesetControl() {
        Tab tab = checkNotNull(filterRulesTabPane).getSelectionModel().getSelectedItem();
        return (FilterRulesAnchorPane)tab.getContent();
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
        getActiveRulesetControl().modified();
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
        // refresh all rules tables
        for (Tab tab : checkNotNull(filterRulesTabPane).getTabs()) {
            FilterRulesAnchorPane filterRulesAnchorPane = (FilterRulesAnchorPane)tab.getContent();
            filterRulesAnchorPane.refreshContent();
            checkNotNull(filterRulesAnchorPane).refreshContent();
        }
    }

    public void captureFlagChange() {
        Preferences userPreferences = Preferences.userRoot();
        userPreferences.put(CAPTURE_FLAG_PREF, Boolean.toString(checkNotNull(captureRequestsCheckBox).selectedProperty().get()));
    }

    public void saveRuleset() {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Traffic filter ruleset (*.rls)", "*.rls"));
            fileChooser.setTitle("Save Traffic filter ruleset");

            FilterRulesAnchorPane activeRulesetControl = getActiveRulesetControl();
            AddressFilterList addressFilterList = activeRulesetControl.mainRuleManager.filter.getFullList();;

            if (activeRulesetControl.savedToFile != null) {
                fileChooser.setInitialDirectory(activeRulesetControl.savedToFile.getParentFile());
                fileChooser.setInitialFileName(activeRulesetControl.savedToFile.getName());
            }

            File configFile = fileChooser.showSaveDialog(checkNotNull(stage));

            if (configFile != null) {
                if (!configFile.getName().endsWith(".rls")) {
                    configFile = new File(configFile.getPath()  + ".rls");
                }
                ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
                        .registerModule(new GuavaModule());
                mapper.writeValue(configFile, addressFilterList);

                activeRulesetControl.savedTo(configFile);

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

    public void loadRuleset() {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Traffic filter ruleset (*.rls)", "*.rls"));
            fileChooser.setTitle("Open Traffic filter ruleset");

            File configFile = fileChooser.showOpenDialog(checkNotNull(stage));

            if (configFile != null) {
                ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
                        .registerModule(new GuavaModule());

                AddressFilterList addressFilterList = mapper.readValue(configFile, AddressFilterList.class);

                Tab filterRulesAnchorPaneTab = null;
                for (Tab tab : checkNotNull(filterRulesTabPane).getTabs()) {
                    FilterRulesAnchorPane rulesAnchorPane = (FilterRulesAnchorPane)tab.getContent();
                    if (configFile.equals(rulesAnchorPane.savedToFile)) {
                        filterRulesAnchorPaneTab = tab;
                        break;
                    }
                }

                if (filterRulesAnchorPaneTab == null) {
                    FilterRulesAnchorPane filterRulesAnchorPane = new FilterRulesAnchorPane(connectionMonitorForm, stage);
                    filterRulesAnchorPane.mainRuleManager.filter.clear();
                    filterRulesAnchorPane.mainRuleManager.filter.addList(addressFilterList, true);

                    final Tab tab = new Tab(configFile.getName(), filterRulesAnchorPane);
                    tab.setClosable(true);

                    filterRulesAnchorPane.setTab(tab);
                    filterRulesAnchorPane.savedTo(configFile);

                    addTab(tab);

                } else {
                    checkNotNull(filterRulesTabPane).getSelectionModel().select(filterRulesAnchorPaneTab);
                }
            }
            refreshContent();
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Error loading config file: " + e, ButtonType.OK);
            LOGGER.error("Error loading config file: ", e);
            alert.showAndWait();
        }
    }

    protected void addTab(Tab tab) {
        checkNotNull(filterRulesTabPane).getTabs().add(tab);
        filterRulesTabPane.getSelectionModel().select(tab);
    }

    public void newRuleset() {
        int maxRulesetNo = 0;
        for (Tab tab : checkNotNull(filterRulesTabPane).getTabs()) {
            String tabText = tab.getText();
            if (tabText.startsWith("New Ruleset ")) {
                String numberStr = tabText.substring(12);
                if (numberStr.endsWith("*")) {
                    numberStr = numberStr.substring(0, numberStr.length()-1).trim();
                }
                try {
                    int num = Integer.parseInt(numberStr);
                    if (num > maxRulesetNo) { maxRulesetNo = num; }
                } catch (NumberFormatException nfe) {}
            }
        }

        FilterRulesAnchorPane filterRulesAnchorPane = new FilterRulesAnchorPane(connectionMonitorForm, stage);

        final Tab tab = new Tab("New Ruleset " + (maxRulesetNo + 1), filterRulesAnchorPane);
        tab.setClosable(true);

        filterRulesAnchorPane.setTab(tab);
        filterRulesAnchorPane.modified();

        addTab(tab);
    }

    // ------------ Traffic rule list ------------

    // ------------ Temporary traffic rule list ------------

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
