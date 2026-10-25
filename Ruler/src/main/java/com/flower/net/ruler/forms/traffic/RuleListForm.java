package com.flower.net.ruler.forms.traffic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.flower.fxutils.JavaFxUtils;
import com.flower.fxutils.Refreshable;
import com.flower.net.config.access.Access;
import com.flower.net.conntrack.allowdenylist.AddressFilterList;
import com.flower.net.conntrack.allowdenylist.AllowDenyConnectionFilter;
import com.flower.net.conntrack.allowdenylist.ImmutableAddressFilterList;
import com.flower.net.conntrack.allowdenylist.ImmutableAddressRecord;
import com.flower.net.conntrack.allowdenylist.ImmutableHostRecord;
import com.flower.net.conntrack.allowdenylist.ImmutablePortRecord;
import com.google.common.collect.Streams;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

import static com.flower.net.conntrack.allowdenylist.AddressFilterList.AddressRecord;
import static com.flower.net.conntrack.allowdenylist.AddressFilterList.PortRecord;
import static com.flower.net.conntrack.allowdenylist.AddressFilterList.HostRecord;

import static com.google.common.base.Preconditions.checkNotNull;

public class RuleListForm extends AnchorPane implements Refreshable {
    final static Logger LOGGER = LoggerFactory.getLogger(RuleListForm.class);


    static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
            .registerModule(new GuavaModule());

    @Nullable Stage stage;

    @Nullable @FXML TableView<TrafficRule> trafficRulesTable;
    final ObservableList<TrafficRule> trafficRules;
    RuleManager mainRuleManager;

    @Nullable @FXML CheckBox activeCheckBox;
    /** On/off state for UI thread consumers (bound to the checkbox); listen to react to changes */
    final BooleanProperty active = new SimpleBooleanProperty(true);
    /** Mirror of {@link #active} safe to read from connection-handling threads */
    volatile boolean isActive = true;

    @Nullable RulesChangedListener rulesChangedListener;

    final StringProperty listName;
    /** Preferences key holding this untitled list across restarts, owned by {@link RuleListsPreferences}; null if not stored yet */
    @Nullable String preferencesKey;
    /** File this list was last saved to / loaded from; null if never */
    final ObjectProperty<File> file = new SimpleObjectProperty<>(null);
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
        RuleListForm ruleListForm = new RuleListForm(file.getName(), addressFilterList);
        ruleListForm.file.set(file);
        return ruleListForm;
    }

    /** Restore a list that was never saved to a file: it is dirty unless empty, as its rules exist nowhere else. */
    public static RuleListForm restoreUntitled(String listName, @Nullable AddressFilterList rules) {
        RuleListForm ruleListForm = new RuleListForm(listName, rules);
        ruleListForm.savedRulesSnapshot = Set.of();
        ruleListForm.updateDirty();
        return ruleListForm;
    }

    static final String FILE_NOT_FOUND_HOST = "file.not.found";
    static final int FILE_NOT_FOUND_PORT = 404;

    /**
     * Placeholder for a list whose file couldn't be read: keeps the file reference, holds a single marker rule and is off.
     * Not dirty, so closing the app won't offer to overwrite the file with the placeholder.
     */
    public static RuleListForm fileNotFound(File file) {
        AddressFilterList placeholderRules = ImmutableAddressFilterList.builder()
                .addAddressRecords(ImmutableAddressRecord.builder()
                        .dstHost(FILE_NOT_FOUND_HOST)
                        .dstPort(FILE_NOT_FOUND_PORT)
                        .access(Access.DENY)
                        .creationTimestamp(System.currentTimeMillis())
                        .isWildcard(false)
                        .build())
                .build();
        RuleListForm ruleListForm = new RuleListForm(file.getName(), placeholderRules);
        ruleListForm.file.set(file);
        ruleListForm.setActive(false);
        return ruleListForm;
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
            void checkDeniedHosts() { /* parent re-checks denied hosts via RulesChangedListener */ }

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

        CheckBox checkBox = checkNotNull(activeCheckBox);
        checkBox.selectedProperty().bindBidirectional(active);
        checkBox.textProperty().bind(Bindings.when(active).then("On").otherwise("Off"));
        checkBox.textFillProperty().bind(Bindings.when(active).then(Color.GREEN).otherwise(Color.RED));
        active.addListener((obs, oldValue, newValue) -> isActive = newValue);

        markSaved();
        refreshContent();
    }

    /** Thread-safe: may be called from connection-handling threads. */
    public boolean isActive() {
        return isActive;
    }

    /** UI thread only. */
    public void setActive(boolean active) {
        this.active.set(active);
    }

    /** UI thread only; add a listener to react when the list is switched on/off. */
    public BooleanProperty activeProperty() {
        return active;
    }

    public @Nullable File getFile() {
        return file.get();
    }

    /** UI thread only; add a listener to react when the list gets associated with another file. */
    public ObjectProperty<File> fileProperty() {
        return file;
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

    /** Call after any change to rules: re-evaluates dirty state (the only place the comparison runs) and notifies the parent. */
    void rulesChanged() {
        updateDirty();
        notifyRulesChanged();
    }

    void updateDirty() {
        dirty.set(!savedRulesSnapshot.equals(rulesSnapshot(mainRuleManager.filter)));
    }

    void notifyRulesChanged() {
        if (rulesChangedListener != null) {
            rulesChangedListener.rulesChanged(this);
        }
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

    /** Parent is notified after any change to this list's rules (e.g. to re-check denied hosts, persist). */
    public void setRulesChangedListener(@Nullable RulesChangedListener rulesChangedListener) {
        this.rulesChangedListener = rulesChangedListener;
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

    public void allow(String host, int port) {
        addAddressRule(host, port, Access.ALLOW);
    }

    public void deny(String host, int port) {
        addAddressRule(host, port, Access.DENY);
    }

    public void allowPort(int port) {
        addPortRule(port, Access.ALLOW);
    }

    public void denyPort(int port) {
        addPortRule(port, Access.DENY);
    }

    public void allowHost(String host) {
        addHostRule(host, Access.ALLOW);
    }

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
        addRuleConfirmOverwrite(addressRecord,
                mainRuleManager.filter::addAddressRecord, AddressRecord::recordsEqual);
    }

    void addHostRule(String host, Access access) {
        HostRecord hostRecord = ImmutableHostRecord.builder()
                .dstHost(host)
                .access(access)
                .creationTimestamp(System.currentTimeMillis())
                .isWildcard(false)
                .build();
        addRuleConfirmOverwrite(hostRecord,
                mainRuleManager.filter::addHostRecord, HostRecord::recordsEqual);
    }

    void addPortRule(int port, Access access) {
        PortRecord portRecord = ImmutablePortRecord.builder()
                .dstPort(port)
                .access(access)
                .creationTimestamp(System.currentTimeMillis())
                .build();
        addRuleConfirmOverwrite(portRecord,
                mainRuleManager.filter::addPortRecord, PortRecord::recordsEqual);
    }

    /**
     * Adds a record without overriding; if a different rule already exists for the same key,
     * asks the user whether to overwrite it.
     * @param addRecord (record, overrideExisting) -> previously existing record, or null
     */
    <R> void addRuleConfirmOverwrite(R record,
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
        refreshContent();
    }

    @Override
    public void refreshContent() {
        trafficRules.clear();
        trafficRules.addAll(getRulesList(mainRuleManager));
        checkNotNull(trafficRulesTable).refresh();
    }

    /**
     * Pure lookup: the access rule this list has for the destination, or null if no rule matches.
     * Thread-safe; does not consider whether the list is active - that's the caller's decision.
     */
    public @Nullable Access getAccess(String dstHost, int dstPort) {
        return mainRuleManager.filter.getRecordRule(dstHost, dstPort);
    }

    /**
     * Adds an address rule with the given access if this list has no rule for the destination yet.
     * Meant to be called by the parent on the auto-add target list, once no active list matched.
     * Works regardless of whether the list is active. Thread-safe, no user interaction.
     * @return true if a rule was added
     */
    public boolean autoAddIfAbsent(String dstHost, int dstPort, Access access) {
        AddressRecord addressRecord = ImmutableAddressRecord.builder()
                .dstHost(dstHost)
                .dstPort(dstPort)
                .access(access)
                .creationTimestamp(System.currentTimeMillis())
                .isWildcard(false)
                .build();
        AddressRecord oldRule = mainRuleManager.filter.addAddressRecord(addressRecord, false);
        if (oldRule != null) {
            return false;
        }
        Platform.runLater(() -> {
            rulesChanged();
            refreshContent();
        });
        return true;
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

    /**
     * "Save as": writes the list to a chosen file and renames the list after that file.
     * @return true if saved; false if cancelled or failed (error already shown)
     */
    public boolean saveRulesAs() {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Traffic filter ruleset (*.rls)", "*.rls"));
            fileChooser.setTitle("Save Traffic filter ruleset");
            File currentFile = file.get();
            if (currentFile != null) {
                File parentDir = currentFile.getParentFile();
                if (parentDir != null && parentDir.isDirectory()) {
                    fileChooser.setInitialDirectory(parentDir);
                }
                fileChooser.setInitialFileName(currentFile.getName());
            }
            File configFile = fileChooser.showSaveDialog(checkNotNull(stage));
            if (configFile == null) {
                return false;
            }
            if (!configFile.getName().endsWith(".rls")) {
                configFile = new File(configFile.getPath()  + ".rls");
            }
            writeToFile(configFile);

            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Traffic filter ruleset saved to : " + configFile.getPath(), ButtonType.OK);
            LOGGER.info("Traffic filter ruleset saved to: {}", configFile.getPath());
            alert.showAndWait();
            return true;
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Error saving Traffic filter ruleset: " + e, ButtonType.OK);
            LOGGER.error("Error saving Traffic filter ruleset: ", e);
            alert.showAndWait();
            return false;
        }
    }

    /**
     * "Save": writes the list to its file without asking; lists without a file fall back to "Save as".
     * @return true if saved; false if cancelled or failed (error already shown)
     */
    public boolean saveRules() {
        File currentFile = file.get();
        if (currentFile == null) {
            return saveRulesAs();
        }
        try {
            writeToFile(currentFile);
            LOGGER.info("Traffic filter ruleset saved to: {}", currentFile.getPath());
            return true;
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Error saving Traffic filter ruleset: " + e, ButtonType.OK);
            LOGGER.error("Error saving Traffic filter ruleset: ", e);
            alert.showAndWait();
            return false;
        }
    }

    void writeToFile(File configFile) throws IOException {
        YAML_MAPPER.writeValue(configFile, mainRuleManager.filter.getFullList());
        file.set(configFile);
        setListName(configFile.getName());
        markSaved();
    }

    public void loadRules() {
        if (isDirty() && JavaFxUtils.showYesNoDialog(
                "\"" + getListName() + "\" has unsaved changes that will be lost. Continue?") != JavaFxUtils.YesNo.YES) {
            return;
        }
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Traffic filter ruleset (*.rls)", "*.rls"));
            fileChooser.setTitle("Open Traffic filter ruleset");

            File configFile = fileChooser.showOpenDialog(checkNotNull(stage));

            if (configFile != null) {

                AddressFilterList addressFilterList = YAML_MAPPER.readValue(configFile, AddressFilterList.class);

                mainRuleManager.filter.clear();
                mainRuleManager.filter.addList(addressFilterList, true);
                file.set(configFile);
                setListName(configFile.getName());
                markSaved();
                notifyRulesChanged();
            }
            refreshContent();
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Error loading config file: " + e, ButtonType.OK);
            LOGGER.error("Error loading config file: ", e);
            alert.showAndWait();
        }
    }

    /** Snapshot of all rules in this list. */
    public AddressFilterList getRules() {
        return mainRuleManager.filter.getFullList();
    }

    /** Merge rules from a chosen file into this list; the list keeps its name and file. */
    public void appendRulesFromFile() {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Traffic filter ruleset (*.rls)", "*.rls"));
            fileChooser.setTitle("Append Traffic filter ruleset");

            File appendFile = fileChooser.showOpenDialog(checkNotNull(stage));
            if (appendFile == null) {
                return;
            }

            AddressFilterList appendList = YAML_MAPPER.readValue(appendFile, AddressFilterList.class);
            appendRules(appendList, appendFile.getName());
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Error appending rules from file: " + e, ButtonType.OK);
            LOGGER.error("Error appending rules from file: ", e);
            alert.showAndWait();
        }
    }

    /** Merge rules from another open list into this list; the source list is left unchanged. */
    public void appendRulesFrom(RuleListForm source) {
        appendRules(source.getRules(), source.getListName());
    }

    /**
     * Merge rules into this list, asking whether to override conflicting rules.
     * @param sourceName shown to the user in the conflict dialog
     * @return false if the user cancelled
     */
    boolean appendRules(AddressFilterList appendList, String sourceName) {
        boolean overrideExisting = false;
        int conflicts = mainRuleManager.filter.countListOverrides(appendList);
        if (conflicts > 0) {
            Optional<Boolean> override = shouldWeOverrideDialog("Override existing rules?",
                    conflicts + (conflicts == 1 ? " rule" : " rules") + " in \"" + sourceName
                            + "\" conflict with existing rules in \"" + getListName() + "\" (same target, different access).\n\n"
                            + BUTTON_TYPE_OVERRIDE.getText() + ": use the rules from \"" + sourceName + "\".\n"
                            + BUTTON_TYPE_IGNORE.getText() + ": keep the existing rules in \"" + getListName() + "\".\n"
                            + "Non-conflicting rules are appended either way.");
            if (override.isEmpty()) {
                return false;
            }
            overrideExisting = override.get();
        }

        mainRuleManager.filter.addList(appendList, overrideExisting);
        rulesChanged();
        refreshAndRestoreTableCursor();
        return true;
    }

    public final static ButtonType BUTTON_TYPE_OVERRIDE = new ButtonType("Override");
    public final static ButtonType BUTTON_TYPE_IGNORE = new ButtonType("Ignore conflicts");
    public final static ButtonType BUTTON_TYPE_CANCEL = new ButtonType("Cancel");

    /** Asks whether to override conflicting rules: true - override, false - ignore conflicts (keep existing), empty - cancel. */
    public static Optional<Boolean> shouldWeOverrideDialog(String titleHeader, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(titleHeader);
        alert.setHeaderText(titleHeader);
        alert.setContentText(content);

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
