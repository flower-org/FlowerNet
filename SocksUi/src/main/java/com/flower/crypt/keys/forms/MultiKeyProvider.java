package com.flower.crypt.keys.forms;

import com.flower.crypt.keys.KeyContext;
import com.flower.crypt.keys.RsaKeyProvider;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public class MultiKeyProvider extends AnchorPane implements TabKeyProvider, RsaKeyProvider {
    final static Logger LOGGER = LoggerFactory.getLogger(MultiKeyProvider.class);

    @Nullable @FXML TabPane childProvidersTabPane;

    protected final String tabName;
    protected final Map<Tab, TabKeyProvider> providerMap;
    protected final Collection<TabKeyProvider> childKeyProviders;
    protected final Stage mainStage;

    public MultiKeyProvider(Stage mainStage, String tabName, Collection<TabKeyProvider> childKeyProviders) {
        providerMap = new HashMap<>();

        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("MultiKeyProvider.fxml"));
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }

        this.mainStage = mainStage;
        this.tabName = tabName;
        this.childKeyProviders = childKeyProviders;

        childKeyProviders.forEach(prov -> providerMap.put(addTab(prov), prov));
        checkNotNull(childProvidersTabPane).getSelectionModel().select(0);
    }

    public Tab addTab(TabKeyProvider tabKeyProvider) {
        String tabName = tabKeyProvider.tabName();
        AnchorPane tabContent = tabKeyProvider.tabContent();

        final Tab tab = new Tab(tabName, tabContent);
        checkNotNull(childProvidersTabPane).getTabs().add(tab);
        return tab;
    }

    protected TabKeyProvider getSelectedProvider() {
        Tab selectedTab = checkNotNull(childProvidersTabPane).getSelectionModel().getSelectedItem();
        return checkNotNull(providerMap.get(selectedTab));
    }

    @Override
    public KeyContext getKeyContext() {
        return getSelectedProvider().getKeyContext();
    }

    @Override
    public String tabName() {
        return tabName;
    }

    @Override
    public AnchorPane tabContent() {
        return this;
    }

    @Override
    public void initPreferences() {
        childKeyProviders.forEach(TabKeyProvider::initPreferences);
    }

    @Override
    public KeyManagerFactory getKeyManagerFactory() { return ((RsaKeyProvider)getSelectedProvider()).getKeyManagerFactory(); }
}
