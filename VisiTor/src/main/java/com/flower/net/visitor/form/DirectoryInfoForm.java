package com.flower.net.visitor.form;

import com.flower.net.conntrack.ConnectionId;
import com.flower.net.conntrack.ConnectionInfo;
import com.flower.net.conntrack.ConnectionListener;
import com.flower.net.conntrack.Destination;
import com.flower.net.visitor.VisiTorMainApp;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.security.auth.Refreshable;
import java.io.IOException;
import java.util.ArrayList;

import static com.flower.net.config.access.Access.DENY;
import static com.google.common.base.Preconditions.checkNotNull;

public class DirectoryInfoForm extends AnchorPane {
    final static Logger LOGGER = LoggerFactory.getLogger(DirectoryInfoForm.class);

    @Nullable Stage stage;
    VisiTorMainApp visiTorMainApp;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public DirectoryInfoForm(VisiTorMainApp visiTorMainApp) {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("DirectoryInfoForm.fxml"));
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }

        this.visiTorMainApp = visiTorMainApp;
    }
}
