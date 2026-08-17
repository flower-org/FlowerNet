package com.flower.net.socksui.forms.traffic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.flower.net.conntrack.allowdenylist.AddressFilterList;
import com.flower.net.conntrack.allowdenylist.AllowDenyConnectionFilter;
import com.flower.net.socksui.MainApp;
import com.flower.net.socksui.forms.ConnectionMonitorForm;
import com.flower.net.socksui.forms.Refreshable;
import com.google.common.collect.Streams;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Tab;
import javafx.scene.control.TableView;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

import static com.google.common.base.Preconditions.checkNotNull;

public class FilterRulesAnchorPane extends AnchorPane implements Refreshable/*, ConnectionFilter, TrafficController*/ {
    @Nullable @FXML TableView<TrafficRule> trafficRulesTable;
    final ObservableList<TrafficRule> trafficRules;

    RuleManager mainRuleManager;

    final ConnectionMonitorForm connectionMonitorForm;
    final Stage stage;
    @Nullable Tab tab;
    @Nullable File savedToFile;

    public FilterRulesAnchorPane(ConnectionMonitorForm connectionMonitorForm, Stage stage) {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("FilterRulesAnchorPane.fxml"));
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }

        this.connectionMonitorForm = connectionMonitorForm;
        this.stage = stage;

        trafficRules = FXCollections.observableArrayList();
        checkNotNull(trafficRulesTable).itemsProperty().set(trafficRules);

        AllowDenyConnectionFilter innerFilter = new AllowDenyConnectionFilter();
        innerFilter.clear();

        // TODO: init rules
        // TODO: init file association, if any
        // TODO: init changed state

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

        // TODO: implement load prefs
        /*
        Preferences userPreferences = Preferences.userRoot();
        try {
            String trafficRules = userPreferences.get(TRAFFIC_RULES_PREF, "");
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
                    .registerModule(new GuavaModule());
            AddressFilterList addressFilterList = mapper.readValue(trafficRules, AddressFilterList.class);
            innerFilter.addList(addressFilterList, true);
        } catch (Exception e) {}
        */
    }

    @Override
    public void refreshContent() {
        // TODO: implement preferences save
        trafficRules.clear();
        trafficRules.addAll(getRulesList(mainRuleManager));
        checkNotNull(trafficRulesTable).refresh();

        /*
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
        */
    }

    public void newRule() {
        mainRuleManager.newRule();
        modified();
    }

    public void removeRule() {
        mainRuleManager.removeRule();
        modified();
    }

    public void clearRules() {
        mainRuleManager.clearRules();
        modified();
    }

    public void deriveRule() {
        mainRuleManager.deriveRule();
        modified();
    }

    public void flipRule() {
        mainRuleManager.flipRule();
        modified();
    }

    public void clearAllowTmpRules() {
        mainRuleManager.clearAllowRules();
        modified();
    }

    public void clearDenyTmpRules() {
        mainRuleManager.clearDenyRules();
        modified();
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

    public void setTab(Tab tab) {
        this.tab = tab;
    }

    // update file binding in ruleset control
    // update tab text to "filename"
    public void savedTo(File file) {
        savedToFile = file;
        checkNotNull(tab).setText(file.getName());
    }

    public void modified() {
        // TODO: implement comparison with last saved list state and restore "unmodified" when applicable
        String tabText = checkNotNull(tab).getText();
        if (!tabText.endsWith("*")) {
            checkNotNull(tab).setText(tabText.trim() + " *");
        }
    }
}
