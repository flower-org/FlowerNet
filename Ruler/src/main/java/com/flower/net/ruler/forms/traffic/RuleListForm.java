package com.flower.net.ruler.forms.traffic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.flower.fxutils.JavaFxUtils;
import com.flower.fxutils.Refreshable;
import com.flower.net.config.access.Access;
import com.flower.net.conntrack.AutoAddRulesMode;
import com.flower.net.conntrack.ConnectionFilter;
import com.flower.net.conntrack.allowdenylist.AddressFilterList;
import com.flower.net.conntrack.allowdenylist.AllowDenyConnectionFilter;
import com.flower.net.conntrack.allowdenylist.ImmutableAddressRecord;
import com.flower.net.conntrack.allowdenylist.ImmutableHostRecord;
import com.flower.net.conntrack.allowdenylist.ImmutablePortRecord;
import com.flower.net.ruler.interfaces.DeniedHostsListener;
import com.google.common.collect.Streams;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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

    final StringProperty listName;
    /** True when the list content differs from what was last saved to / loaded from a file */
    final BooleanProperty dirty = new SimpleBooleanProperty(false);
    /** Rule content as of the last save/load, used to detect when changes were reverted */
    Set<Object> savedRulesSnapshot = Set.of();
    /** List name with "*" appended when there are unsaved changes */
    final StringBinding title;

    /** Create an empty list with the given name. */
    public RuleListForm(String listName) {
        this(listName, null);
    }

    /** Open a list from a ruleset file; the list is named after the file. */
    public static RuleListForm fromFile(File file) throws IOException {
        AddressFilterList addressFilterList = YAML_MAPPER.readValue(file, AddressFilterList.class);
        return new RuleListForm(file.getName(), addressFilterList);
    }

    public RuleListForm(String listName, @Nullable AddressFilterList initialRules) {
        this.listName = new SimpleStringProperty(listName);
        this.title = Bindings.when(dirty)
                .then(this.listName.concat("*"))
                .otherwise(this.listName);

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
        if (initialRules != null) {
            innerFilter.addList(initialRules, true);
        }

        mainRuleManager = new RuleManager(innerFilter) {
            @Override
            void refreshAndRestoreCursor() { refreshAndRestoreTableCursor(); }

            @Override
            void checkDeniedHosts() { checkNotNull(connectionMonitorForm).checkDeniedHosts(); }

            @Override
            @Nullable TrafficRule getSelectedTrafficRule() { return checkNotNull(trafficRulesTable).getSelectionModel().getSelectedItem(); }

            @Override
            @Nullable Stage getStage() { return stage; }

            @Override
            void onRulesChanged() { rulesChanged(); }

            @Override protected String clearRulesMsg() { return "Delete all rules in \"" + getListName() + "\"?"; }

            @Override protected String clearAllowRulesMsg() { return "Delete all Allow rules in \"" + getListName() + "\"?"; }

            @Override protected String clearDenyRulesMsg() { return "Delete all Deny rules in \"" + getListName() + "\"?"; }
        };

        trafficRules = FXCollections.observableArrayList();
        checkNotNull(trafficRulesTable).itemsProperty().set(trafficRules);

        markSaved();
        refreshContent();
    }

    public StringProperty listNameProperty() {
        return listName;
    }

    /** Bind the containing Tab's text to this: list name, with "*" appended when there are unsaved changes. */
    public StringBinding titleProperty() {
        return title;
    }

    public BooleanProperty dirtyProperty() {
        return dirty;
    }

    public boolean isDirty() {
        return dirty.get();
    }

    /** Re-evaluate dirty state after a change; this is the only place the comparison runs. */
    void rulesChanged() {
        dirty.set(!savedRulesSnapshot.equals(rulesSnapshot(mainRuleManager.filter)));
    }

    void markSaved() {
        savedRulesSnapshot = rulesSnapshot(mainRuleManager.filter);
        dirty.set(false);
    }

    record AddressRuleKey(boolean isWildcard, String host, int port, Access access) {}
    record HostRuleKey(boolean isWildcard, String host, Access access) {}
    record PortRuleKey(int port, Access access) {}

    /** Rule content ignoring creation timestamps and ordering */
    static Set<Object> rulesSnapshot(AllowDenyConnectionFilter filter) {
        Set<Object> snapshot = new HashSet<>();
        filter.getPortRecords().values().forEach(
                r -> snapshot.add(new PortRuleKey(r.dstPort(), r.access())));
        Streams.concat(filter.getHostRecords().values().stream(), filter.getWildcardHostRecords().values().stream())
                .forEach(r -> snapshot.add(new HostRuleKey(r.isWildcard(), r.dstHost(), r.access())));
        Streams.concat(filter.getAddressRecords().values().stream(), filter.getWildcardAddressRecords().values().stream())
                .flatMap(portMap -> portMap.values().stream())
                .forEach(r -> snapshot.add(new AddressRuleKey(r.isWildcard(), r.dstHost(), r.dstPort(), r.access())));
        return snapshot;
    }

    public String getListName() {
        return listName.get();
    }

    public void setListName(String listName) {
        this.listName.set(listName);
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
        if (existingRule == null) {
            rulesChanged();
        } else if (!recordsEqual.test(existingRule, record)) {
            if (JavaFxUtils.showYesNoDialog("Overwrite existing rule?") == JavaFxUtils.YesNo.YES) {
                R unused = addRecord.apply(record, true);
                rulesChanged();
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
                    Platform.runLater(() -> {
                        rulesChanged();
                        refreshContent();
                    });
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

    /** "Save as": writes the list to a chosen file and renames the list after that file. */
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
                setListName(configFile.getName());
                markSaved();

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
                setListName(configFile.getName());
                markSaved();
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
