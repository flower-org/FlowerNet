package com.flower.socksui;

import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import javax.annotation.Nullable;
import java.util.function.Function;

public class ModalWindow {
    public static Stage showModal(Event event, Function<Stage, Parent> rootFunction, String title, @Nullable StageStyle stageStyle) {
        Window window;
        if (event.getSource() instanceof Stage) {
            window = ((Stage) event.getSource()).getScene().getWindow();
        } else if (event.getSource() instanceof Node) {
            window = ((Node) event.getSource()).getScene().getWindow();
        } else {
            throw new RuntimeException("Unknown event source for Windowed event");
        }

        return showModal(window, rootFunction, title, stageStyle);
    }

    public static Stage showModal(Window window, Function<Stage, Parent> rootFunction, String title, @Nullable StageStyle stageStyle) {
        Stage stage = new Stage();
        Parent root = rootFunction.apply(stage);

        stage.setScene(new Scene(root));
        stage.setTitle(title);
        stage.initModality(Modality.WINDOW_MODAL);

        //stage.initStyle(StageStyle.UNDECORATED);
        if (stageStyle != null) {
            stage.initStyle(stageStyle);
        }

        stage.initOwner(window);
        stage.resizableProperty().set(false);
        stage.show();

        return stage;
    }

    public static Stage showModalUndecorated(Event event, Function<Stage, Parent> rootFunction, String title) {
        return showModal(event, rootFunction, title, StageStyle.UNDECORATED);
    }

    public static Stage showModalUtility(Event event, Function<Stage, Parent> rootFunction, String title) {
        return showModal(event, rootFunction, title, StageStyle.UTILITY);
    }

    public static Stage showModal(Event event, Function<Stage, Parent> rootFunction, String title) {
        return showModal(event, rootFunction, title, null);
    }

    public static Stage showModal(Window window, Function<Stage, Parent> rootFunction, String title) {
        return showModal(window, rootFunction, title, null);
    }

    public static Stage showModalUndecorated(Window window, Function<Stage, Parent> rootFunction, String title) {
        return showModal(window, rootFunction, title, StageStyle.UNDECORATED);
    }

    public static Stage showModalUtility(Window window, Function<Stage, Parent> rootFunction, String title) {
        return showModal(window, rootFunction, title, StageStyle.UTILITY);
    }
}
