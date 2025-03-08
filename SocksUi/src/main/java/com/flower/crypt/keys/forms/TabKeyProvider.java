package com.flower.crypt.keys.forms;

import com.flower.crypt.keys.KeyProvider;
import javafx.scene.layout.AnchorPane;

public interface TabKeyProvider extends KeyProvider {
    String tabName();
    AnchorPane tabContent();
    void initPreferences();
}
