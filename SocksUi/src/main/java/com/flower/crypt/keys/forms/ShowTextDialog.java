package com.flower.crypt.keys.forms;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;

import static com.google.common.base.Preconditions.checkNotNull;

public class ShowTextDialog extends VBox {
    final static Logger LOGGER = LoggerFactory.getLogger(ShowTextDialog.class);

    @FXML @Nullable TextArea showTextArea;

    @Nullable Stage stage;

    public ShowTextDialog(String text) {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("ShowTextDialog.fxml"));
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }

        checkNotNull(showTextArea).textProperty().set(text);
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void okClose() {
        try {
            checkNotNull(stage).close();
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "ShowTextDialog close Error: " + e, ButtonType.OK);
            LOGGER.error("ShowTextDialog close Error:", e);
            alert.showAndWait();
        }
    }
}
