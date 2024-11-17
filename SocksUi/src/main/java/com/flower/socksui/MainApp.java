package com.flower.socksui;

import com.flower.socksui.forms.ConnectionMonitorForm;
import com.flower.socksui.forms.ServerForm;
import com.flower.socksui.forms.traffic.TrafficControlForm;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

public class MainApp {
    @Nullable Stage mainStage;

    @FXML @Nullable Label connectionsLabel;
    @FXML @Nullable Label serverInfoLabel;
    @FXML @Nullable TabPane tabs;

    @Nullable ServerForm serverForm;
    @Nullable TrafficControlForm trafficControlForm;
    @Nullable ConnectionMonitorForm connectionMonitorForm;

    public MainApp() {
        //This form is created automatically.
        //No need to load fxml explicitly
    }

    public void setMainStage(@Nullable Stage mainStage) {
        this.mainStage = mainStage;
    }

    public @Nullable Stage getMainStage() {
        return mainStage;
    }

    public void setStatusText(String text) {
        checkNotNull(serverInfoLabel).setText(text);
    }
    public void setConnectionsText(String text) {
        checkNotNull(connectionsLabel).setText(text);
    }

    public void showAboutDialog() {
        Alert alert = new Alert(Alert.AlertType.NONE, "Socks UI v 0.0.6", ButtonType.OK);
        alert.showAndWait();
    }

    void addTab(Tab tab) {
        checkNotNull(tabs).getTabs().add(tab);
        tabs.getSelectionModel().select(tab);
    }

    public void quit() { checkNotNull(mainStage).close(); }

    public void closeAllTabs() {
        checkNotNull(tabs).getTabs().clear();
    }

    public void showTabs() {
        openServerTab();
        openTrafficControlTab();
        openConnectionsTab();

        checkNotNull(serverForm).addConnectionFilter(checkNotNull(trafficControlForm));
        checkNotNull(serverForm).addConnectionListener(checkNotNull(connectionMonitorForm));

        checkNotNull(tabs).getSelectionModel().select(0);
    }

    public void openServerTab() {
        serverForm = new ServerForm(this);
        serverForm.setStage(checkNotNull(mainStage));
        final Tab tab = new Tab("Server", serverForm);
        tab.setClosable(false);

        addTab(tab);
    }

    public void openTrafficControlTab() {
        trafficControlForm = new TrafficControlForm(this);
        trafficControlForm.setStage(checkNotNull(mainStage));
        final Tab tab = new Tab("Traffic Control", trafficControlForm);
        tab.setClosable(false);

        addTab(tab);
    }

    public void openConnectionsTab() {
        connectionMonitorForm = new ConnectionMonitorForm(this, checkNotNull(trafficControlForm));
        connectionMonitorForm.setStage(checkNotNull(mainStage));
        final Tab tab = new Tab("Connection Monitor", connectionMonitorForm);
        tab.setClosable(false);

        addTab(tab);
    }

    public void shutdownServer() {
        checkNotNull(serverForm).stopServer();
    }
}
