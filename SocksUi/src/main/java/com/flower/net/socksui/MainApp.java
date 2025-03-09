package com.flower.net.socksui;

import com.flower.fxutils.JavaFxUtils;
import com.flower.net.socksui.forms.ConnectionMonitorForm;
import com.flower.net.socksui.forms.ServerForm;
import com.flower.net.socksui.forms.traffic.TrafficControlForm;
import com.flower.crypt.keys.forms.MultiKeyProvider;
import com.flower.crypt.keys.forms.RsaFileKeyProvider;
import com.flower.crypt.keys.forms.RsaPkcs11KeyProvider;
import com.flower.crypt.keys.forms.RsaRawKeyProvider;
import com.flower.crypt.keys.forms.TabKeyProvider;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.SingleSelectionModel;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;

import javax.annotation.Nullable;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.flower.fxutils.JavaFxUtils.YesNo.NO;
import static com.google.common.base.Preconditions.checkNotNull;

public class MainApp {
    @Nullable Stage mainStage;

    @FXML @Nullable Label connectionsLabel;
    @FXML @Nullable Label serverInfoLabel;
    @FXML @Nullable TabPane tabs;

    @Nullable TabKeyProvider keyProvider;
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
        Alert alert = new Alert(Alert.AlertType.NONE, "Socks UI v 0.2.2", ButtonType.OK);
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
        openClientCertificateTab();
        openServerTab();
        openTrafficControlTab();
        openConnectionsTab();

        checkNotNull(serverForm).addConnectionFilter(checkNotNull(trafficControlForm));
        checkNotNull(serverForm).addConnectionListener(checkNotNull(connectionMonitorForm));

        checkNotNull(tabs).getSelectionModel().select(0);
    }

    private static TabKeyProvider buildMainKeyProvider(Stage mainStage) {
        RsaPkcs11KeyProvider rsaPkcs11KeyProvider = new RsaPkcs11KeyProvider(mainStage);
        RsaFileKeyProvider rsaFileKeyProvider = new RsaFileKeyProvider(mainStage);
        RsaRawKeyProvider rsaRawKeyProvider = new RsaRawKeyProvider();
        MultiKeyProvider multiKeyProvider = new MultiKeyProvider(mainStage, "RSA-2048",
                List.of(rsaPkcs11KeyProvider, rsaFileKeyProvider, rsaRawKeyProvider));
        SingleSelectionModel<Tab> tabPaneSelectionModel = multiKeyProvider.getChildProvidersTabPane().getSelectionModel();
        tabPaneSelectionModel.selectedItemProperty().addListener(new ChangeListener<Tab>() {
            final AtomicBoolean reverseInProgress = new AtomicBoolean(false);
            @Override
            public void changed(ObservableValue<? extends Tab> observableValue, Tab oldTab, Tab newTab) {
                if (oldTab != newTab) {
                    //If reverse is not in progress
                    if (!reverseInProgress.get()) {
                        if (NO == JavaFxUtils.showYesNoDialog(
                                "Confirm certificate change",
                                "Confirm certificate change",
                                "Tab switch will change the client TLS certificate used " +
                                "for encrypted channels. Proceed?")) {
                            try {
                                //Set reverse in progress
                                while (!reverseInProgress.compareAndSet(false, true));
                                //Reverse tab
                                tabPaneSelectionModel.select(oldTab);
                            } finally {
                                //Unset reverse in progress
                                reverseInProgress.compareAndSet(true, false);
                            }
                        }
                    }
                }
            }
        });
        return multiKeyProvider;
    }

    public void openClientCertificateTab() {
        keyProvider = buildMainKeyProvider(mainStage);
        keyProvider.initPreferences();

        final Tab tab = new Tab("Client cert", keyProvider.tabContent());
        tab.setClosable(false);

        addTab(tab);
    }

    public void openServerTab() {
        serverForm = new ServerForm(this, keyProvider);
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
