package com.flower.socksui;

import com.flower.socksui.forms.ServerForm;
import com.flower.socksui.forms.TrafficControlForm;
import javafx.event.Event;
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

    @FXML @Nullable Label serverInfoLabel;
    @FXML @Nullable TabPane tabs;

    public MainApp() {
        //This form is created automatically.
        //No need to load fxml explicitly
    }

    public void setMainStage(@Nullable Stage mainStage) {
        this.mainStage = mainStage;
    }

    public void setStatusText(String text) {
        checkNotNull(serverInfoLabel).setText(text);
    }

    public void showAboutDialog() {
        Alert alert = new Alert(Alert.AlertType.NONE, "Socks UI v 0.0.1", ButtonType.OK);
        alert.showAndWait();
    }

    void addTab(Tab tab) {
        checkNotNull(tabs).getTabs().add(tab);
        tabs.getSelectionModel().select(tab);
    }

    public void logout(Event event) {
        closeAllTabs();
    }

    public void quit() { checkNotNull(mainStage).close(); }

    public void closeAllTabs() {
        checkNotNull(tabs).getTabs().clear();
    }

    public void showTabs() {
        openServerTab();
        openTrafficControlTab();

        checkNotNull(tabs).getSelectionModel().select(0);
    }

    public void openServerTab() {
        ServerForm serverForm = new ServerForm();
        serverForm.setStage(checkNotNull(mainStage));
        final Tab tab = new Tab("Server", serverForm);
        tab.setClosable(false);

        addTab(tab);
    }

    public void openTrafficControlTab() {
        TrafficControlForm trafficControlForm = new TrafficControlForm();
        trafficControlForm.setStage(checkNotNull(mainStage));
        final Tab tab = new Tab("Traffic Control", trafficControlForm);
        tab.setClosable(false);

        addTab(tab);
    }
}
