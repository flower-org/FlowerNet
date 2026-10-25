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
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkNotNull;

public class TrafficControlForm extends AnchorPane implements Refreshable, ConnectionFilter {
    final static Logger LOGGER = LoggerFactory.getLogger(TrafficControlForm.class);
    final static String CAPTURE_FLAG_PREF = "captureFlagPref";

    final static String ALLOW = "Allow";
    final static String DENY = "Deny";

    final static String AUTO_ADD_NONE = "No auto-add";
    final static String AUTO_ADD_ALLOW = "Auto-Allow";
    final static String AUTO_ADD_DENY = "Auto-Deny";

    final static String UNTITLED_LIST_NAME = "Untitled ";

    @Nullable Stage stage;
    @Nullable @FXML ComboBox<String> filteringModeComboBox;
    @Nullable @FXML CheckBox captureRequestsCheckBox;
    @Nullable @FXML TableView<CapturedRequest> capturedRequestsTable;
    final ObservableList<CapturedRequest> capturedRequests;
    @Nullable @FXML TextField maxRequests;
    @Nullable @FXML CheckBox allowDirectIpAccessCheckBox;
    @Nullable @FXML MenuButton captureFilterMenuButton;
    @Nullable @FXML ComboBox<String> autoAddComboBox;
    @Nullable @FXML TabPane ruleListsTabPane;

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

        capturedRequests = FXCollections.observableArrayList();
        checkNotNull(capturedRequestsTable).itemsProperty().set(capturedRequests);

        Preferences userPreferences = Preferences.userRoot();
        String captureFlagStr = userPreferences.get(CAPTURE_FLAG_PREF, "");
        boolean captureFlag = false;
        if (!StringUtils.isBlank(captureFlagStr)) {
            try {
                captureFlag = Boolean.parseBoolean(captureFlagStr);
            } catch (Exception e) { }
        }
        checkNotNull(captureRequestsCheckBox).selectedProperty().set(captureFlag);

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

        captureFilterChange();

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
                    if (!ruleList.saveToFile()) {
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
                    "Some rule lists can't be kept until the next start and will be lost:\n\n"
                            + String.join("\n", errors)
                            + "\n\nSave them to files to keep them.",
                    closeAnyway, ButtonType.CANCEL);
            alert.setHeaderText("Rule lists will be lost");
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
                addRuleList(RuleListForm.fromFile(file));
            }
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Error loading rule list: " + e, ButtonType.OK);
            LOGGER.error("Error loading rule list: ", e);
            alert.showAndWait();
        }
    }

    public void saveRuleList() {
        RuleListForm ruleList = getSelectedRuleListOrWarn();
        if (ruleList != null) {
            ruleList.saveRules();
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
            Platform.runLater(() -> {
                if (checkNotNull(captureRequestsCheckBox).selectedProperty().get()) {
                    CaptureFilter captureFilter = getCaptureFilter();
                    if (captureFilter.matchCapturedRecord(finalCheck.checkResult, finalCheck.isRuleMatched)) {
                        String hostOrIpFrom = null;
                        int port = -1;
                        if (from instanceof InetSocketAddress isa) {
                            hostOrIpFrom = (isa.getAddress() != null)
                                    ? isa.getAddress().getHostAddress() // resolved -> IP
                                    : isa.getHostString();              // unresolved -> original host
                            port = isa.getPort();
                        }

                        CapturedRequest capturedRequest = new CapturedRequest(dstHost, dstPort, finalCheck.checkResult,
                                finalCheck.isRuleMatched, Protocol.TCP, finalCheck.isDirectIpBlock, hostOrIpFrom, port);
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

    public void clearCapturedData() {
        capturedRequests.clear();
        checkNotNull(capturedRequestsTable).refresh();
    }

    public void allowCapture() {
        CapturedRequest capturedRequest = checkNotNull(capturedRequestsTable).getSelectionModel().getSelectedItem();
        if (capturedRequest != null) {
            allow(capturedRequest.getHost(), capturedRequest.getPort());
        }
    }

    @Override
    public void allow(String host, int port) {
        RuleListForm ruleList = getSelectedRuleListOrWarn();
        if (ruleList != null) {
            ruleList.allow(host, port);
        }
    }

    public void denyCapture() {
        CapturedRequest capturedRequest = checkNotNull(capturedRequestsTable).getSelectionModel().getSelectedItem();
        if (capturedRequest != null) {
            deny(capturedRequest.getHost(), capturedRequest.getPort());
        }
    }

    @Override
    public void deny(String host, int port) {
        RuleListForm ruleList = getSelectedRuleListOrWarn();
        if (ruleList != null) {
            ruleList.deny(host, port);
        }
    }

    public void allowCaptureHost() {
        CapturedRequest capturedRequest = checkNotNull(capturedRequestsTable).getSelectionModel().getSelectedItem();
        if (capturedRequest != null) {
            allowHost(capturedRequest.getHost());
        }
    }

    @Override
    public void allowHost(String host) {
        RuleListForm ruleList = getSelectedRuleListOrWarn();
        if (ruleList != null) {
            ruleList.allowHost(host);
        }
    }

    public void denyCaptureHost() {
        CapturedRequest capturedRequest = checkNotNull(capturedRequestsTable).getSelectionModel().getSelectedItem();
        if (capturedRequest != null) {
            denyHost(capturedRequest.getHost());
        }
    }

    @Override
    public void denyHost(String host) {
        RuleListForm ruleList = getSelectedRuleListOrWarn();
        if (ruleList != null) {
            ruleList.denyHost(host);
        }
    }

    public void allowCapturePort() {
        CapturedRequest capturedRequest = checkNotNull(capturedRequestsTable).getSelectionModel().getSelectedItem();
        if (capturedRequest != null) {
            allowPort(capturedRequest.getPort());
        }
    }

    @Override
    public void allowPort(int port) {
        RuleListForm ruleList = getSelectedRuleListOrWarn();
        if (ruleList != null) {
            ruleList.allowPort(port);
        }
    }

    public void denyCapturePort() {
        CapturedRequest capturedRequest = checkNotNull(capturedRequestsTable).getSelectionModel().getSelectedItem();
        if (capturedRequest != null) {
            denyPort(capturedRequest.getPort());
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

    public void captureFlagChange() {
        Preferences userPreferences = Preferences.userRoot();
        userPreferences.put(CAPTURE_FLAG_PREF, Boolean.toString(checkNotNull(captureRequestsCheckBox).selectedProperty().get()));
    }
}
