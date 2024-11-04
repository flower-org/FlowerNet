package com.flower.socksui.forms;

import javafx.fxml.FXMLLoader;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;

public class ServerForm extends AnchorPane {
    final static Logger LOGGER = LoggerFactory.getLogger(ServerForm.class);

    @Nullable Stage stage;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public ServerForm() {

        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("ServerForm.fxml"));
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }
}