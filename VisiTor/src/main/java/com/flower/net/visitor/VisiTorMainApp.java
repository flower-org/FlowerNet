package com.flower.net.visitor;

import com.flower.net.visitor.forms.DirectoryInfoForm;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

public class VisiTorMainApp {
    @Nullable Stage mainStage;

    @FXML @Nullable Label connectionsLabel;
    @FXML @Nullable Label serverInfoLabel;
    @FXML @Nullable TabPane tabs;

    @Nullable DirectoryInfoForm directoryInfoForm;

    public VisiTorMainApp() {
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
        Alert alert = new Alert(Alert.AlertType.NONE, "VisiTOR v 0.0.1", ButtonType.OK);
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
        openLoadDirectoryInfoTab();

        checkNotNull(tabs).getSelectionModel().select(0);
    }

    public void openLoadDirectoryInfoTab() {
        directoryInfoForm = new DirectoryInfoForm(this);
        directoryInfoForm.setStage(checkNotNull(mainStage));
        final Tab tab = new Tab("TOR Directory", directoryInfoForm);
        tab.setClosable(false);

        addTab(tab);
    }

    public void shutdownServer() {
        //checkNotNull(serverForm).stopServer();
    }
}
