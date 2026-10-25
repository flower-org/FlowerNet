package com.flower.net.ruler.forms.traffic;

import com.flower.fxutils.Refreshable;
import com.flower.net.config.access.Protocol;
import com.flower.net.conntrack.AutoAddRulesMode;
import com.flower.net.conntrack.ConnectionFilter;
import com.flower.net.config.access.Access;
import com.flower.fxutils.JavaFxUtils;
import com.flower.net.ruler.interfaces.ConnectionsTextSetter;
import com.flower.net.ruler.interfaces.DeniedHostsListener;
import com.flower.net.utils.IpAddressUtil;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
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

    @Nullable Stage stage;
    @Nullable @FXML ComboBox<String> filteringModeComboBox;
    @Nullable @FXML CheckBox allowDirectIpAccessCheckBox;
    @Nullable @FXML ComboBox<String> autoAddComboBox;
    @Nullable @FXML TabPane ruleListsTabPane;
    @Nullable @FXML MenuButton appendMenuButton;
    @Nullable @FXML RawCaptureForm rawCaptureForm;

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

        checkNotNull(rawCaptureForm).setConnectionFilter(this);

        ComboBox<String> autoAddCombo = checkNotNull(autoAddComboBox);
        autoAddCombo.valueProperty().addListener((obs, oldValue, newValue) -> autoAddRulesMode = toAutoAddRulesMode(newValue));
        autoAddRulesMode = toAutoAddRulesMode(autoAddCombo.getValue());

        TabPane tabPane = checkNotNull(ruleListsTabPane);
        tabPane.getTabs().addListener((ListChangeListener<Tab>) change -> {
            ruleLists = tabPane.getTabs().stream().map(tab -> (RuleListForm) tab.getContent()).toList();
            // e.g. closing an active Allow list may deny existing connections
            checkDeniedHosts();
            schedulePersistRuleLists();
        });
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) ->
                selectedRuleList = newTab == null ? null : (RuleListForm) newTab.getContent());

        MenuButton appendMenu = checkNotNull(appendMenuButton);
        appendMenu.setOnShowing(event -> rebuildAppendMenu());
        rebuildAppendMenu();
        persistDelay.setOnFinished(event -> persistRuleLists());
        restoreRuleLists();
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
        ruleList.activeProperty().addListener((obs, oldValue, newValue) -> {
            checkDeniedHosts();
            schedulePersistRuleLists();
        });
        ruleList.fileProperty().addListener((obs, oldValue, newValue) -> schedulePersistRuleLists());
        ruleList.listNameProperty().addListener((obs, oldValue, newValue) -> schedulePersistRuleLists());

        Tab tab = new Tab();
        tab.setContent(ruleList);
        tab.textProperty().bind(ruleList.titleProperty());
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
        checkDeniedHosts();
        schedulePersistRuleLists();
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

    /** First match among active lists, in tab order. Thread-safe. */
    @Nullable Access getActiveListsAccess(String dstHost, int dstPort) {
        for (RuleListForm ruleList : ruleLists) {
            if (ruleList.isActive()) {
                Access access = ruleList.getAccess(dstHost, dstPort);
                if (access != null) {
                    return access;
                }
            }
        }
        return null;
    }

    CheckResult getCheckResult(String dstHost, int dstPort) {
        Access checkResult = getActiveListsAccess(dstHost, dstPort);

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

        // from == null is a re-check of an existing connection (see DeniedHostsListener) - never auto-add there
        if (!check.isRuleMatched && from != null) {
            AutoAddRulesMode autoAddRulesMode = this.autoAddRulesMode;
            RuleListForm target = selectedRuleList;
            if (autoAddRulesMode != AutoAddRulesMode.NONE && target != null) {
                Access autoAddAccess = autoAddRulesMode == AutoAddRulesMode.AUTO_ALLOW ? Access.ALLOW : Access.DENY;
                // Auto-add even when the target list is inactive; the added rule only counts if it's active
                if (target.autoAddIfAbsent(dstHost, dstPort, autoAddAccess)) {
                    check = getCheckResult(dstHost, dstPort);
                }
            }
        }
        final CheckResult finalCheck = check;

        if (from != null) {
            RawCaptureForm captureForm = checkNotNull(rawCaptureForm);
            if (captureForm.isCaptureEnabled()) {
                captureForm.addCapture(CapturedRequest.of(dstHost, dstPort, finalCheck.checkResult,
                        finalCheck.isRuleMatched, Protocol.TCP, finalCheck.isDirectIpBlock, from));
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
