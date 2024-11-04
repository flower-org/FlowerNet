package com.flower.socksui.forms;

import javafx.fxml.FXMLLoader;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;

public class TrafficControlForm extends AnchorPane {
    final static Logger LOGGER = LoggerFactory.getLogger(TrafficControlForm.class);

    @Nullable Stage stage;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public TrafficControlForm() {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("TrafficControlForm.fxml"));
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }
}
