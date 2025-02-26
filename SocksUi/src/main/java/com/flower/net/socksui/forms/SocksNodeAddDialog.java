package com.flower.net.socksui.forms;

import static com.google.common.base.Preconditions.checkNotNull;

import com.flower.net.config.SocksNode;
import com.flower.net.config.SocksProtocolVersion;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;

public class SocksNodeAddDialog extends VBox {
    final static Logger LOGGER = LoggerFactory.getLogger(SocksNodeAddDialog.class);

    public final static String SOCKS_5S = "SOCKS5s";
    public final static String SOCKS_5 = "SOCKS5";
    public final static String SOCKS_PLUS = "SOCKS+";
    public final static String SOCKS_4A = "SOCKS4a";

    @FXML @Nullable ComboBox<String> socksTypeComboBox;
    @FXML @Nullable TextField hostTextField;
    @FXML @Nullable TextField portTextField;
    @FXML @Nullable TextField certTextField;
    @FXML @Nullable Button addButton;

    @Nullable Stage stage;
    @Nullable volatile SocksNode returnSocksNode = null;

    public SocksNodeAddDialog(@Nullable SocksNode nodeToEdit) {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("SocksNodeAddDialog.fxml"));
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }

        if (nodeToEdit != null) {
            checkNotNull(addButton).textProperty().set("Edit");
            selectServerType(nodeToEdit.socksProtocolVersion());
            checkNotNull(hostTextField).textProperty().set(nodeToEdit.serverAddress());
            checkNotNull(portTextField).textProperty().set(Integer.toString(nodeToEdit.serverPort()));
        }
    }

    public void selectServerType(SocksProtocolVersion protocolVersion) {
        String valueToSelect;
        switch (protocolVersion) {
            case SOCKS4a: valueToSelect = SOCKS_4A; break;
            case SOCKS5: valueToSelect = SOCKS_5; break;
            case SOCKS5s: valueToSelect = SOCKS_5S; break;
            case SOCKS_PLUS: valueToSelect = SOCKS_PLUS; break;
            default: return;
        }

        checkNotNull(socksTypeComboBox).getSelectionModel().select(valueToSelect);
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void selectCert() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Certificate (*.crt)", "*.crt"));
        fileChooser.setTitle("Load Certificate");
        File certificatFile = fileChooser.showOpenDialog(checkNotNull(stage));
        if (certificatFile != null) {
            checkNotNull(certTextField).textProperty().set(certificatFile.getPath());
        }
    }

    public void okClose() {
        try {
            String socksType = checkNotNull(socksTypeComboBox).valueProperty().get();
            String host = checkNotNull(hostTextField).textProperty().get().trim();
            String portStr = checkNotNull(portTextField).textProperty().get().trim();
            String filename = checkNotNull(certTextField).textProperty().get().trim();
            int port;
            try {
                port = Integer.parseInt(portStr);
            } catch (Exception e) {
                port = -1;
            }

            if (StringUtils.isBlank(socksType)) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Please specify SOCKS type", ButtonType.OK);
                alert.showAndWait();
            } else if (StringUtils.isBlank(host)) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Please specify host " + host, ButtonType.OK);
                alert.showAndWait();
            } else if (StringUtils.isBlank(filename)) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Please specify certificate " + host, ButtonType.OK);
                alert.showAndWait();
            } else if (port <= 0) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Please specify integer port > 0 (specified:" + portStr + ")", ButtonType.OK);
                alert.showAndWait();
            } else {
                switch (socksType) {
                    case SOCKS_5S:
                        returnSocksNode = SocksNode.of(SocksProtocolVersion.SOCKS5s, host, port, filename);
                        checkNotNull(stage).close();
                        break;
                    case SOCKS_5:
                        returnSocksNode = SocksNode.of(SocksProtocolVersion.SOCKS5, host, port);
                        checkNotNull(stage).close();
                        break;
                    default:
                        Alert alert = new Alert(Alert.AlertType.INFORMATION, "Proxy type not supported yet: " + socksType, ButtonType.OK);
                        alert.showAndWait();
                        break;
                }
            }
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "WorkspaceDialog close Error: " + e, ButtonType.OK);
            LOGGER.error("WorkspaceDialog close Error:", e);
            alert.showAndWait();
        }
    }

    @Nullable public SocksNode getSocksNode() {
        return returnSocksNode;
    }

    public void textFieldKeyRelease(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER){
            okClose();
        }
    }
}
