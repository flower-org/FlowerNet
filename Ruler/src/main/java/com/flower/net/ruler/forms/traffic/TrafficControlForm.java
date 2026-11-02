package com.flower.net.ruler.forms.traffic;

import com.flower.fxutils.JavaFxUtils;
import com.flower.fxutils.Refreshable;
import com.flower.net.config.access.Access;
import com.flower.net.config.access.Protocol;
import com.flower.net.conntrack.AutoAddRulesMode;
import com.flower.net.conntrack.ConnectionFilter;
import com.flower.net.conntrack.TrafficDirection;
import com.flower.net.ruler.interfaces.ConnectionsTextSetter;
import com.flower.net.ruler.interfaces.DeniedHostsListener;
import com.flower.net.utils.IpAddressUtil;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkNotNull;

public class TrafficControlForm extends AnchorPane implements Refreshable, ConnectionFilter {
    final static Logger LOGGER = LoggerFactory.getLogger(TrafficControlForm.class);

    final static String ALLOW = "Allow";
    final static String DENY = "Deny";

    final static String AUTO_ADD_NONE = "No auto-add";
    final static String AUTO_ADD_ALLOW = "Auto-Allow";
    final static String AUTO_ADD_DENY = "Auto-Deny";

    final static String UNTITLED_LIST_NAME = "Untitled ";

    final static String FILTERING_MODE_PREF = "filteringModePref";
    final static String ALLOW_DIRECT_IP_ACCESS_PREF = "allowDirectIpAccessPref";
    final static String DIRECTION_DEFAULT_FLAG_PREF = "directionDefaultFlagPref";
    final static String DIRECTION_DEFAULT_ALLOW_IN_PREF = "directionDefaultAllowInPref";
    final static String DIRECTION_DEFAULT_ALLOW_OUT_PREF = "directionDefaultAllowOutPref";
    final static String DIRECTION_DEFAULT_ALLOW_FORWARD_PREF = "directionDefaultAllowForwardPref";

    @Nullable Stage stage;
    @Nullable @FXML ComboBox<String> filteringModeComboBox;
    @Nullable @FXML CheckBox allowDirectIpAccessCheckBox;
    @Nullable @FXML CheckBox directionDefaultCheckBox;
    @Nullable @FXML MenuButton directionDefaultMenuButton;
    @Nullable @FXML CheckMenuItem directionDefaultInMenuItem;
    @Nullable @FXML CheckMenuItem directionDefaultOutMenuItem;
    @Nullable @FXML CheckMenuItem directionDefaultForwardMenuItem;
    @Nullable @FXML ComboBox<String> autoAddComboBox;
    @Nullable @FXML TabPane ruleListsTabPane;
    @Nullable @FXML MenuButton appendMenuButton;
    @Nullable @FXML TabPane captureTabPane;

    final AtomicLong totalConnections;
    final AtomicLong allowedConnections;
    final AtomicLong prohibitedConnections;
    final ConnectionsTextSetter mainForm;

    @Nullable DeniedHostsListener connectionMonitorForm;

    /** Rule lists in tab order (= priority order); snapshot replaced on the UI thread, read on connection threads */
    volatile List<RuleListForm> ruleLists = List.of();
    /** List in the selected tab: target for allow/deny actions and auto-add */
    volatile @Nullable RuleListForm selectedRuleList;
    volatile AutoAddRulesMode autoAddRulesMode = AutoAddRulesMode.NONE;
    /**
     * Direction default: when enabled, decides unmatched traffic by its direction (as reported by nfbridge) before
     * the filtering mode does; allowed directions get ALLOW, the rest DENY. Traffic without a direction (SOCKS)
     * isn't affected. Snapshots replaced on the UI thread, read on connection threads.
     */
    volatile boolean directionDefaultEnabled;
    volatile Set<TrafficDirection> directionDefaultAllowed = Collections.unmodifiableSet(EnumSet.allOf(TrafficDirection.class));
    /** Capture views in tab order; snapshot replaced on the UI thread, read on connection threads */
    volatile List<CaptureForm> captureForms = List.of();
    /** Answers of active lists per destination; invalidated right after rules, list activation or list order change */
    final RuleLookupCache ruleLookupCache = new RuleLookupCache(this::lookupActiveLists);

    /** Numbering for new lists; never reused within a session */
    int untitledListCounter = 0;

    /** Debounces persisting rule lists to preferences; runs on the UI thread, no extra threads */
    final PauseTransition persistDelay = new PauseTransition(Duration.seconds(1));
    static final Pattern UNTITLED_NUMBER = Pattern.compile(Pattern.quote(UNTITLED_LIST_NAME) + "(\\d+)");

    public void setStage(Stage stage) {
        this.stage = stage;
        for (RuleListForm ruleList : ruleLists) {
            ruleList.setStage(stage);
        }
    }

    public void setConnectionMonitorForm(DeniedHostsListener connectionMonitorForm) {
        this.connectionMonitorForm = connectionMonitorForm;
    }

    public TrafficControlForm(ConnectionsTextSetter mainForm) {
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

        TabPane captureTabs = checkNotNull(captureTabPane);
        captureTabs.getTabs().addListener((ListChangeListener<Tab>) change ->
                captureForms = captureTabs.getTabs().stream().map(tab -> (CaptureForm) tab.getContent()).toList());
        addCaptureForm("Capture Stats", new CaptureStatsForm());
        addCaptureForm("Raw Capture", new RawCaptureForm());
        captureTabs.getSelectionModel().selectFirst();

        ComboBox<String> autoAddCombo = checkNotNull(autoAddComboBox);
        autoAddCombo.valueProperty().addListener((obs, oldValue, newValue) -> autoAddRulesMode = toAutoAddRulesMode(newValue));
        autoAddRulesMode = toAutoAddRulesMode(autoAddCombo.getValue());

        initFilteringMode();
        initDirectIpAccess();
        initDirectionDefault();

        TabPane tabPane = checkNotNull(ruleListsTabPane);
        tabPane.getTabs().addListener((ListChangeListener<Tab>) change -> {
            ruleLists = tabPane.getTabs().stream().map(tab -> (RuleListForm) tab.getContent()).toList();
            ruleLookupCache.invalidate();
            // e.g. closing an active Allow list may deny existing connections
            filteringChanged();
            schedulePersistRuleLists();
        });
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) ->
                selectedRuleList = newTab == null ? null : (RuleListForm) newTab.getContent());
        tabPane.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() == MouseButton.SECONDARY && event.isStillSincePress()
                    && event.getTarget() instanceof Node target) {
                Tab tab = findTabHeaderTab(target, tabPane);
                if (tab != null) {
                    RuleListForm ruleList = (RuleListForm) tab.getContent();
                    ruleList.setActive(!ruleList.isActive());
                    event.consume();
                }
            }
        });

        MenuButton appendMenu = checkNotNull(appendMenuButton);
        appendMenu.setOnShowing(event -> rebuildAppendMenu());
        rebuildAppendMenu();
        persistDelay.setOnFinished(event -> persistRuleLists());
        restoreRuleLists();
    }

    /** Restores the filtering mode from preferences and keeps it saved */
    void initFilteringMode() {
        Preferences preferences = Preferences.userRoot();
        ComboBox<String> comboBox = checkNotNull(filteringModeComboBox);
        String savedMode = preferences.get(FILTERING_MODE_PREF, null);
        if (savedMode != null && comboBox.getItems().contains(savedMode)) {
            comboBox.setValue(savedMode);
        }
        comboBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) {
                preferences.put(FILTERING_MODE_PREF, newValue);
            }
            filteringChanged();
        });
    }

    /** Restores the direct IP access checkbox from preferences and keeps it saved */
    void initDirectIpAccess() {
        Preferences preferences = Preferences.userRoot();
        CheckBox checkBox = checkNotNull(allowDirectIpAccessCheckBox);
        checkBox.setSelected(preferences.getBoolean(ALLOW_DIRECT_IP_ACCESS_PREF, checkBox.isSelected()));
        checkBox.selectedProperty().addListener((obs, oldValue, newValue) -> {
            preferences.putBoolean(ALLOW_DIRECT_IP_ACCESS_PREF, newValue);
            filteringChanged();
        });
    }

    /** Restores the direction default controls from preferences and keeps them saved */
    void initDirectionDefault() {
        Preferences preferences = Preferences.userRoot();
        CheckBox enabledCheckBox = checkNotNull(directionDefaultCheckBox);
        enabledCheckBox.setSelected(preferences.getBoolean(DIRECTION_DEFAULT_FLAG_PREF, false));
        checkNotNull(directionDefaultInMenuItem).setSelected(preferences.getBoolean(DIRECTION_DEFAULT_ALLOW_IN_PREF, true));
        checkNotNull(directionDefaultOutMenuItem).setSelected(preferences.getBoolean(DIRECTION_DEFAULT_ALLOW_OUT_PREF, true));
        checkNotNull(directionDefaultForwardMenuItem).setSelected(preferences.getBoolean(DIRECTION_DEFAULT_ALLOW_FORWARD_PREF, true));
        updateDirectionDefault();

        enabledCheckBox.selectedProperty().addListener((obs, oldValue, newValue) -> directionDefaultChanged());
        for (CheckMenuItem item : List.of(directionDefaultInMenuItem, directionDefaultOutMenuItem, directionDefaultForwardMenuItem)) {
            item.selectedProperty().addListener((obs, oldValue, newValue) -> directionDefaultChanged());
        }
    }

    void directionDefaultChanged() {
        updateDirectionDefault();
        Preferences preferences = Preferences.userRoot();
        preferences.putBoolean(DIRECTION_DEFAULT_FLAG_PREF, directionDefaultEnabled);
        preferences.putBoolean(DIRECTION_DEFAULT_ALLOW_IN_PREF, checkNotNull(directionDefaultInMenuItem).isSelected());
        preferences.putBoolean(DIRECTION_DEFAULT_ALLOW_OUT_PREF, checkNotNull(directionDefaultOutMenuItem).isSelected());
        preferences.putBoolean(DIRECTION_DEFAULT_ALLOW_FORWARD_PREF, checkNotNull(directionDefaultForwardMenuItem).isSelected());
        filteringChanged();
    }

    /** Copies the controls into the snapshot read by connection threads, and updates the menu button */
    void updateDirectionDefault() {
        EnumSet<TrafficDirection> allowed = EnumSet.noneOf(TrafficDirection.class);
        if (checkNotNull(directionDefaultInMenuItem).isSelected()) { allowed.add(TrafficDirection.IN); }
        if (checkNotNull(directionDefaultOutMenuItem).isSelected()) { allowed.add(TrafficDirection.OUT); }
        if (checkNotNull(directionDefaultForwardMenuItem).isSelected()) { allowed.add(TrafficDirection.FORWARD); }
        directionDefaultAllowed = Collections.unmodifiableSet(allowed);
        directionDefaultEnabled = checkNotNull(directionDefaultCheckBox).isSelected();

        MenuButton menuButton = checkNotNull(directionDefaultMenuButton);
        menuButton.setDisable(!directionDefaultEnabled);
        menuButton.setText(formatDirectionDefault(allowed));
    }

    /** Allowed directions, e.g. "IN/OUT/FORWARD", "IN"; "DENY" if all are denied */
    static String formatDirectionDefault(Set<TrafficDirection> allowed) {
        List<String> names = new ArrayList<>();
        for (TrafficDirection direction : TrafficDirection.values()) {
            if (allowed.contains(direction)) {
                names.add(direction.name());
            }
        }
        return names.isEmpty() ? "DENY" : String.join("/", names);
    }

    /**
     * @return the tab whose header contains the node, or null if the node isn't in a tab header.
     * Relies on an undocumented JavaFX implementation detail: TabPaneSkin's header keeps its Tab in getProperties()
     * under Tab.class (verified on JavaFX 21; Tab.getStyleableNode() returns null there). If a JavaFX upgrade drops it,
     * this returns null and right-click toggling of rule lists silently stops working.
     */
    static @Nullable Tab findTabHeaderTab(Node node, TabPane tabPane) {
        for (Node n = node; n != null && n != tabPane; n = n.getParent()) {
            if (n.getProperties().get(Tab.class) instanceof Tab tab && tab.getTabPane() == tabPane) {
                return tab;
            }
        }
        return null;
    }

    /** Adds a capture view as a non-closeable tab */
    <T extends Node & CaptureForm> void addCaptureForm(String title, T captureForm) {
        captureForm.setConnectionFilter(this);
        checkNotNull(captureTabPane).getTabs().add(new Tab(title, captureForm));
    }

    // ------------ Persistence across restarts ------------

    void restoreRuleLists() {
        for (RuleListForm ruleList : RuleListsPreferences.load()) {
            addRuleList(ruleList);
            Matcher matcher = UNTITLED_NUMBER.matcher(ruleList.getListName());
            if (ruleList.getFile() == null && matcher.matches()) {
                try {
                    untitledListCounter = Math.max(untitledListCounter, Integer.parseInt(matcher.group(1)));
                } catch (NumberFormatException e) {
                    LOGGER.warn("Unexpected untitled list name: {}", ruleList.getListName());
                }
            }
        }
        checkNotNull(ruleListsTabPane).getSelectionModel().selectFirst();
    }

    void schedulePersistRuleLists() {
        persistDelay.playFromStart();
    }

    /** @return errors for anything that couldn't be persisted; also logged */
    List<String> persistRuleLists() {
        persistDelay.stop();
        return RuleListsPreferences.save(ruleLists);
    }

    /**
     * Call before the application closes (UI thread). Offers to save file-based lists with unsaved changes
     * and persists all lists; untitled lists are kept in preferences without asking.
     * @return true if it's OK to close, false if the user cancelled
     */
    public boolean confirmClose() {
        persistDelay.stop();
        TabPane tabPane = checkNotNull(ruleListsTabPane);
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.YES);
        ButtonType dontSave = new ButtonType("Don't save", ButtonBar.ButtonData.NO);
        for (Tab tab : List.copyOf(tabPane.getTabs())) {
            RuleListForm ruleList = (RuleListForm) tab.getContent();
            if (ruleList.getFile() != null && ruleList.isDirty()) {
                tabPane.getSelectionModel().select(tab);
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                        "\"" + ruleList.getListName() + "\" has unsaved changes. Save them to " + ruleList.getFile().getPath() + "?",
                        save, dontSave, ButtonType.CANCEL);
                alert.setHeaderText("Unsaved changes");
                ButtonType answer = alert.showAndWait().orElse(ButtonType.CANCEL);
                if (answer == save) {
                    if (!ruleList.saveRules()) {
                        schedulePersistRuleLists();
                        return false;
                    }
                } else if (answer != dontSave) {
                    schedulePersistRuleLists();
                    return false;
                }
            }
        }

        List<String> errors = persistRuleLists();
        if (!errors.isEmpty()) {
            ButtonType closeAnyway = new ButtonType("Close anyway", ButtonBar.ButtonData.OK_DONE);
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "Some rule lists can't be saved for the next start:\n\n"
                            + String.join("\n", errors)
                            + "\n\nSave them to files to keep them.",
                    closeAnyway, ButtonType.CANCEL);
            alert.setHeaderText("Rule lists not saved");
            if (alert.showAndWait().orElse(ButtonType.CANCEL) != closeAnyway) {
                return false;
            }
        }
        return true;
    }

    // ------------ Rule list tabs ------------

    void addRuleList(RuleListForm ruleList) {
        if (stage != null) {
            ruleList.setStage(stage);
        }
        ruleList.setRulesChangedListener(this::rulesChanged);
        ruleList.addRulesChangeListener(ruleLookupCache::invalidate);
        ruleList.activeProperty().addListener((obs, oldValue, newValue) -> {
            ruleLookupCache.invalidate();
            filteringChanged();
            schedulePersistRuleLists();
        });
        ruleList.fileProperty().addListener((obs, oldValue, newValue) -> schedulePersistRuleLists());
        ruleList.listNameProperty().addListener((obs, oldValue, newValue) -> schedulePersistRuleLists());

        Tab tab = new Tab();
        tab.setContent(ruleList);
        tab.textProperty().bind(ruleList.titleProperty());
        tab.setGraphic(ActiveTabMarker.create(ruleList.activeProperty()));
        tab.setOnCloseRequest(event -> {
            if (ruleList.isDirty() && JavaFxUtils.showYesNoDialog(
                    "\"" + ruleList.getListName() + "\" has unsaved changes that will be lost. Close anyway?") != JavaFxUtils.YesNo.YES) {
                event.consume();
            }
        });

        TabPane tabPane = checkNotNull(ruleListsTabPane);
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
    }

    void rulesChanged(RuleListForm ruleList) {
        filteringChanged();
        schedulePersistRuleLists();
    }

    /**
     * Call on the UI thread after anything that can change filtering results: rules, list activation or order,
     * filtering mode, direct IP access. Re-checks existing connections and re-evaluates capture views.
     */
    void filteringChanged() {
        checkDeniedHosts();
        for (CaptureForm captureForm : captureForms) {
            captureForm.rulesChanged(this::getCheckResult);
        }
    }

    void checkDeniedHosts() {
        if (connectionMonitorForm != null) {
            connectionMonitorForm.checkDeniedHosts();
        }
    }

    public void newRuleList() {
        untitledListCounter++;
        addRuleList(new RuleListForm(UNTITLED_LIST_NAME + untitledListCounter));
    }

    public void loadRuleList() {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Traffic filter ruleset (*.rls)", "*.rls"));
            fileChooser.setTitle("Open Traffic filter ruleset");

            File file = fileChooser.showOpenDialog(checkNotNull(stage));
            if (file != null) {
                Tab existingTab = findTabByFile(file);
                if (existingTab != null) {
                    checkNotNull(ruleListsTabPane).getSelectionModel().select(existingTab);
                } else {
                    addRuleList(RuleListForm.fromFile(file));
                }
            }
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Error loading rule list: " + e, ButtonType.OK);
            LOGGER.error("Error loading rule list: ", e);
            alert.showAndWait();
        }
    }

    @Nullable Tab findTabByFile(File file) {
        for (Tab tab : checkNotNull(ruleListsTabPane).getTabs()) {
            File tabFile = ((RuleListForm) tab.getContent()).getFile();
            if (tabFile != null && isSameFile(tabFile, file)) {
                return tab;
            }
        }
        return null;
    }

    static boolean isSameFile(File file1, File file2) {
        try {
            // resolves symlinks, case-insensitive file systems etc.
            return Files.isSameFile(file1.toPath(), file2.toPath());
        } catch (IOException e) {
            // e.g. one of the files doesn't exist
            return file1.toPath().toAbsolutePath().normalize().equals(file2.toPath().toAbsolutePath().normalize());
        }
    }

    public void saveRuleList() {
        RuleListForm ruleList = getSelectedRuleListOrWarn();
        if (ruleList != null) {
            boolean unused = ruleList.saveRules();
        }
    }

    public void saveRuleListAs() {
        RuleListForm ruleList = getSelectedRuleListOrWarn();
        if (ruleList != null) {
            boolean unused = ruleList.saveRulesAs();
        }
    }

    /** Rebuilds the Append menu each time it opens: from file, or from any other open list into the selected one. */
    void rebuildAppendMenu() {
        MenuButton menuButton = checkNotNull(appendMenuButton);
        menuButton.getItems().clear();
        RuleListForm target = selectedRuleList;
        if (target == null) {
            MenuItem noTarget = new MenuItem("Select a list to append to");
            noTarget.setDisable(true);
            menuButton.getItems().add(noTarget);
            return;
        }

        MenuItem fromFile = new MenuItem("From file...");
        fromFile.setOnAction(event -> target.appendRulesFromFile());
        menuButton.getItems().add(fromFile);

        boolean separatorAdded = false;
        for (RuleListForm source : ruleLists) {
            if (source == target) {
                continue;
            }
            if (!separatorAdded) {
                menuButton.getItems().add(new SeparatorMenuItem());
                separatorAdded = true;
            }
            File sourceFile = source.getFile();
            MenuItem fromList = new MenuItem("From \"" + source.titleProperty().get() + "\""
                    + (sourceFile == null ? "" : "  (" + sourceFile.getPath() + ")"));
            fromList.setMnemonicParsing(false);
            fromList.setOnAction(event -> target.appendRulesFrom(source));
            menuButton.getItems().add(fromList);
        }
    }

    /** UI thread only */
    @Nullable RuleListForm getSelectedRuleListOrWarn() {
        RuleListForm ruleList = selectedRuleList;
        if (ruleList == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "No rule list selected. Create or load a rule list first.", ButtonType.OK);
            alert.showAndWait();
        }
        return ruleList;
    }

    static AutoAddRulesMode toAutoAddRulesMode(@Nullable String value) {
        if (value == null) {
            return AutoAddRulesMode.NONE;
        }
        switch (value) {
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

    /** First match among active lists, in tab order. Thread-safe, cached. */
    @Nullable Access getActiveListsAccess(String dstHost, int dstPort) {
        return ruleLookupCache.get(dstHost, dstPort).access();
    }

    /**
     * An endpoint is a known server if any rule of an active list matches it (host+port, host or port;
     * exact or wildcard): rules only ever describe servers. Thread-safe, cached.
     */
    @Override
    public boolean isKnownServer(String host, int port) {
        return ruleLookupCache.get(host, port).knownServer();
    }

    /** Uncached lookup over active lists, in tab order. */
    RuleLookupCache.Result lookupActiveLists(String host, int port) {
        for (RuleListForm ruleList : ruleLists) {
            if (ruleList.isActive()) {
                Access access = ruleList.getAccess(host, port);
                if (access != null) {
                    return new RuleLookupCache.Result(access);
                }
            }
        }
        return new RuleLookupCache.Result(null);
    }

    /**
     * Precedence: rules of active lists, then the direct IP block, then the direction default (if enabled and the
     * traffic has a direction), then the filtering mode.
     */
    CheckResult getCheckResult(String dstHost, int dstPort, @Nullable TrafficDirection direction) {
        Access checkResult = getActiveListsAccess(dstHost, dstPort);

        boolean isDirectIpBlock = false;
        boolean isDirectionDefault = false;
        boolean isRuleMatched;

        if (checkResult != null) {
            isRuleMatched = true;
        } else {
            isRuleMatched = false;
            boolean isDirectIpAccessAllowed = checkNotNull(allowDirectIpAccessCheckBox).selectedProperty().get();
            if (!isDirectIpAccessAllowed && IpAddressUtil.isIPAddress(dstHost)) {
                isDirectIpBlock = true;
                checkResult = Access.DENY;
            } else if (direction != null && directionDefaultEnabled) {
                isDirectionDefault = true;
                checkResult = directionDefaultAllowed.contains(direction) ? Access.ALLOW : Access.DENY;
            } else {
                checkResult = getDefaultAccessType();
            }
        }

        return new CheckResult(checkResult, isDirectIpBlock, isRuleMatched, isDirectionDefault);
    }

    @Override
    public Access approveConnection(String dstHost, int dstPort, Protocol protocol, @Nullable SocketAddress from,
                                    @Nullable TrafficDirection direction) {
        CheckResult check = getCheckResult(dstHost, dstPort, direction);

        // from == null is a re-check of an existing connection (see DeniedHostsListener) - never auto-add there
        if (!check.isRuleMatched && from != null) {
            AutoAddRulesMode autoAddRulesMode = this.autoAddRulesMode;
            RuleListForm target = selectedRuleList;
            if (autoAddRulesMode != AutoAddRulesMode.NONE && target != null) {
                Access autoAddAccess = autoAddRulesMode == AutoAddRulesMode.AUTO_ALLOW ? Access.ALLOW : Access.DENY;
                // Auto-add even when the target list is inactive; the added rule only counts if it's active
                if (target.autoAddIfAbsent(dstHost, dstPort, autoAddAccess)) {
                    check = getCheckResult(dstHost, dstPort, direction);
                }
            }
        }
        final CheckResult finalCheck = check;

        if (from != null) {
            CapturedRequest capturedRequest = null;
            for (CaptureForm captureForm : captureForms) {
                if (captureForm.isCaptureEnabled()) {
                    if (capturedRequest == null) {
                        capturedRequest = CapturedRequest.of(dstHost, dstPort, finalCheck.checkResult,
                                finalCheck.isRuleMatched, protocol, finalCheck.isDirectIpBlock,
                                finalCheck.isDirectionDefault, from, direction);
                    }
                    captureForm.addCapture(capturedRequest);
                }
            }

            totalConnections.incrementAndGet();
            if (finalCheck.checkResult == Access.ALLOW) {
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
    public void allow(String host, int port) {
        RuleListForm ruleList = getSelectedRuleListOrWarn();
        if (ruleList != null) {
            ruleList.allow(host, port);
        }
    }

    @Override
    public void deny(String host, int port) {
        RuleListForm ruleList = getSelectedRuleListOrWarn();
        if (ruleList != null) {
            ruleList.deny(host, port);
        }
    }

    @Override
    public void allowHost(String host) {
        RuleListForm ruleList = getSelectedRuleListOrWarn();
        if (ruleList != null) {
            ruleList.allowHost(host);
        }
    }

    @Override
    public void denyHost(String host) {
        RuleListForm ruleList = getSelectedRuleListOrWarn();
        if (ruleList != null) {
            ruleList.denyHost(host);
        }
    }

    @Override
    public void allowPort(int port) {
        RuleListForm ruleList = getSelectedRuleListOrWarn();
        if (ruleList != null) {
            ruleList.allowPort(port);
        }
    }

    @Override
    public void denyPort(int port) {
        RuleListForm ruleList = getSelectedRuleListOrWarn();
        if (ruleList != null) {
            ruleList.denyPort(port);
        }
    }

    @Override
    public void refreshContent() {
        for (RuleListForm ruleList : ruleLists) {
            ruleList.refreshContent();
        }
    }
}
