package com.flower.net.ruler.forms.traffic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.flower.fxutils.JavaFxUtils;
import com.flower.fxutils.Refreshable;
import com.flower.net.config.access.Access;
import com.flower.net.conntrack.AutoAddRulesMode;
import com.flower.net.conntrack.ConnectionFilter;
import com.flower.net.conntrack.allowdenylist.*;
import com.flower.net.ruler.interfaces.ConnectionsTextSetter;
import com.flower.net.ruler.interfaces.DeniedHostsListener;
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
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.prefs.Preferences;

import static com.flower.net.conntrack.allowdenylist.AddressFilterList.AddressRecord;
import static com.flower.net.conntrack.allowdenylist.AddressFilterList.PortRecord;
import static com.flower.net.conntrack.allowdenylist.AddressFilterList.HostRecord;

import static com.google.common.base.Preconditions.checkNotNull;

public class RuleListForm extends AnchorPane implements Refreshable, ConnectionFilter {
    final static Logger LOGGER = LoggerFactory.getLogger(RuleListForm.class);

    final static String TRAFFIC_RULES_PREF = "trafficRulesPref";

    static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
            .registerModule(new GuavaModule());

    @Nullable Stage stage;

    @Nullable @FXML TableView<TrafficRule> trafficRulesTable;
    final ObservableList<TrafficRule> trafficRules;
    RuleManager mainRuleManager;

    protected AutoAddRulesMode autoAddRulesMode = AutoAddRulesMode.NONE;

    @Nullable @FXML ToggleButton activateToggleButton;

    @Nullable DeniedHostsListener connectionMonitorForm;

    public RuleListForm() {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("RuleListForm.fxml"));
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }

        AllowDenyConnectionFilter innerFilter = new AllowDenyConnectionFilter();
        innerFilter.clear();

        Preferences userPreferences = Preferences.userRoot();
        try {
            String trafficRules = userPreferences.get(TRAFFIC_RULES_PREF, "");
            AddressFilterList addressFilterList = YAML_MAPPER.readValue(trafficRules, AddressFilterList.class);
            innerFilter.addList(addressFilterList, true);
        } catch (Exception e) {}

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

        trafficRules = FXCollections.observableArrayList();
        checkNotNull(trafficRulesTable).itemsProperty().set(trafficRules);

        refreshContent();
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setConnectionMonitorForm(DeniedHostsListener connectionMonitorForm) {
        this.connectionMonitorForm = connectionMonitorForm;
    }

    public AutoAddRulesMode getAutoAddRulesMode() {
        return autoAddRulesMode;
    }

    public void setAutoAddRulesMode(AutoAddRulesMode autoAddRulesMode) {
        this.autoAddRulesMode = autoAddRulesMode;
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

    public void clearAllowRules() {
        mainRuleManager.clearAllowRules();
    }

    public void clearDenyRules() {
        mainRuleManager.clearDenyRules();
    }

    @Override
    public void allow(String host, int port) {
        addAddressRule(host, port, Access.ALLOW);
    }

    @Override
    public void deny(String host, int port) {
        addAddressRule(host, port, Access.DENY);
    }

    @Override
    public void allowPort(int port) {
        addPortRule(port, Access.ALLOW);
    }

    @Override
    public void denyPort(int port) {
        addPortRule(port, Access.DENY);
    }

    @Override
    public void allowHost(String host) {
        addHostRule(host, Access.ALLOW);
    }

    @Override
    public void denyHost(String host) {
        addHostRule(host, Access.DENY);
    }

    void addAddressRule(String host, int port, Access access) {
        AddressRecord addressRecord = ImmutableAddressRecord.builder()
                .dstHost(host)
                .dstPort(port)
                .access(access)
                .creationTimestamp(System.currentTimeMillis())
                .isWildcard(false)
                .build();
        addRuleConfirmOverwrite(addressRecord, access,
                mainRuleManager.filter::addAddressRecord, AddressRecord::recordsEqual);
    }

    void addHostRule(String host, Access access) {
        HostRecord hostRecord = ImmutableHostRecord.builder()
                .dstHost(host)
                .access(access)
                .creationTimestamp(System.currentTimeMillis())
                .isWildcard(false)
                .build();
        addRuleConfirmOverwrite(hostRecord, access,
                mainRuleManager.filter::addHostRecord, HostRecord::recordsEqual);
    }

    void addPortRule(int port, Access access) {
        PortRecord portRecord = ImmutablePortRecord.builder()
                .dstPort(port)
                .access(access)
                .creationTimestamp(System.currentTimeMillis())
                .build();
        addRuleConfirmOverwrite(portRecord, access,
                mainRuleManager.filter::addPortRecord, PortRecord::recordsEqual);
    }

    /**
     * Adds a record without overriding; if a different rule already exists for the same key,
     * asks the user whether to overwrite it.
     * @param addRecord (record, overrideExisting) -> previously existing record, or null
     */
    <R> void addRuleConfirmOverwrite(R record, Access access,
                                     BiFunction<R, Boolean, R> addRecord,
                                     BiPredicate<R, R> recordsEqual) {
        R existingRule = addRecord.apply(record, false);
        if (existingRule != null && !recordsEqual.test(existingRule, record)) {
            if (JavaFxUtils.showYesNoDialog("Overwrite existing rule?") == JavaFxUtils.YesNo.YES) {
                R unused = addRecord.apply(record, true);
            }
        }
        if (access == Access.DENY) {
            checkNotNull(connectionMonitorForm).checkDeniedHosts();
        }
        refreshContent();
    }

    @Override
    public void refreshContent() {
        trafficRules.clear();
        trafficRules.addAll(getRulesList(mainRuleManager));
        checkNotNull(trafficRulesTable).refresh();

        Preferences userPreferences = Preferences.userRoot();
        try {
            // Save main rules
            AddressFilterList addressFilterList = mainRuleManager.filter.getFullList();

            StringWriter writer = new StringWriter();
            YAML_MAPPER.writeValue(writer, addressFilterList);

            String filterList = writer.getBuffer().toString();

            userPreferences.put(TRAFFIC_RULES_PREF, filterList);
        } catch (Exception e) {}
    }

    @Nullable Access getAccess(String dstHost, int dstPort) {
        return mainRuleManager.filter.getRecordRule(dstHost, dstPort);
    }

    @Override
    public @Nullable Access approveConnection(String dstHost, int dstPort, @Nullable SocketAddress from) {
        Access access = getAccess(dstHost, dstPort);

        if (access == null) {
            if (autoAddRulesMode == AutoAddRulesMode.AUTO_DENY || autoAddRulesMode == AutoAddRulesMode.AUTO_ALLOW) {
                AddressRecord addressRecord = ImmutableAddressRecord.builder()
                        .dstHost(dstHost)
                        .dstPort(dstPort)
                        .access(autoAddRulesMode == AutoAddRulesMode.AUTO_ALLOW ? Access.ALLOW : Access.DENY)
                        .creationTimestamp(System.currentTimeMillis())
                        .isWildcard(false)
                        .build();
                AddressRecord oldRule = mainRuleManager.filter.addAddressRecord(addressRecord, false);

                // We need this check, because even when list is inactive we still want to auto-add.
                if (oldRule == null) {
                    Platform.runLater(this::refreshContent);
                    return approveConnection(dstHost, dstPort, from);
                }
            }
        }

        return access;
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

    void refreshAndRestoreTableCursor() {
        int selectedIndex = checkNotNull(trafficRulesTable).getSelectionModel().getSelectedIndex();

        refreshContent();

        if (selectedIndex >= trafficRulesTable.itemsProperty().get().size()) {
            selectedIndex = trafficRulesTable.itemsProperty().get().size() - 1;
        }
        if (selectedIndex >= 0) {
            checkNotNull(trafficRulesTable).getSelectionModel().select(selectedIndex);
        }
    }

    // --------------- Save and Load from file ---------------

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

                YAML_MAPPER.writeValue(configFile, addressFilterList);

                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Traffic filter ruleset saved to : " + configFile.getPath(), ButtonType.OK);
                LOGGER.info("Traffic filter ruleset saved to: {}", configFile.getPath());
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

                AddressFilterList addressFilterList = YAML_MAPPER.readValue(configFile, AddressFilterList.class);

                mainRuleManager.filter.clear();
                mainRuleManager.filter.addList(addressFilterList, true);
                checkNotNull(connectionMonitorForm).checkDeniedHosts();
            }
            refreshContent();
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Error loading config file: " + e, ButtonType.OK);
            LOGGER.error("Error loading config file: ", e);
            alert.showAndWait();
        }
    }

    // TODO: implement append rules:
    /*
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
    */
}
