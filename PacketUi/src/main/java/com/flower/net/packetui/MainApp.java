package com.flower.net.packetui;

import com.flower.net.ruler.interfaces.ConnectionsTextSetter;
import com.flower.net.packetui.forms.ServerForm;
import com.flower.net.ruler.forms.traffic.TrafficControlForm;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;

import javax.annotation.Nullable;
import java.util.prefs.Preferences;

import static com.google.common.base.Preconditions.checkNotNull;

public class MainApp implements ConnectionsTextSetter {
    /** Own node, so Traffic Control settings don't collide with other apps on the same machine */
    static final String TRAFFIC_CONTROL_PREFS_PATH = "com/flower/net/packetui/trafficControl";

    @Nullable Stage mainStage;

    @FXML @Nullable Label connectionsLabel;
    @FXML @Nullable Label serverInfoLabel;
    @FXML @Nullable TabPane tabs;

    @Nullable ServerForm serverForm;
    @Nullable TrafficControlForm trafficControlForm;

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

    @Override
    public String countersTitle() {
        return "Packets";
    }

    public void showAboutDialog() {
        Alert alert = new Alert(Alert.AlertType.NONE, "Packet UI v 1.5.5", ButtonType.OK);
        alert.showAndWait();
    }

    void addTab(Tab tab) {
        checkNotNull(tabs).getTabs().add(tab);
        tabs.getSelectionModel().select(tab);
    }

    public void quit() {
        // Stage.close() doesn't fire onCloseRequest, so confirm here as well
        if (confirmClose()) {
            checkNotNull(mainStage).close();
        }
    }

    /** @return true if it's OK to close the application (e.g. user chose what to do with unsaved rule lists) */
    public boolean confirmClose() {
        return trafficControlForm == null || trafficControlForm.confirmClose();
    }

    public void closeAllTabs() {
        checkNotNull(tabs).getTabs().clear();
    }

    public void showTabs() {
        openServerTab();
        openTrafficControlTab();

        // TODO: implement conntrack?
        //openConnectionsTab();

        checkNotNull(serverForm).addConnectionFilter(checkNotNull(trafficControlForm));

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
        trafficControlForm = new TrafficControlForm(this, Preferences.userRoot().node(TRAFFIC_CONTROL_PREFS_PATH));
        trafficControlForm.setStage(checkNotNull(mainStage));
        final Tab tab = new Tab("Traffic Control", trafficControlForm);
        tab.setClosable(false);

        addTab(tab);
    }

    public void shutdownServer() {
        checkNotNull(serverForm).stopServer();
    }
}
