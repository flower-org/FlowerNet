package com.flower.crypt.keys.forms;

import com.flower.crypt.keys.KeyContext;
import javafx.beans.value.ObservableValue;
import com.flower.crypt.keys.RsaKeyContext;
import com.flower.crypt.PkiUtil;
import com.flower.crypt.keys.RsaKeyProvider;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.Objects;
import java.util.prefs.Preferences;

import static com.flower.crypt.keys.UserPreferencesManager.getUserPreference;
import static com.flower.crypt.keys.UserPreferencesManager.updateUserPreference;
import static com.google.common.base.Preconditions.checkNotNull;

public class RsaFileKeyProvider extends AnchorPane implements TabKeyProvider, RsaKeyProvider {
    final static Logger LOGGER = LoggerFactory.getLogger(RsaFileKeyProvider.class);

    final static String FILE_CERTIFICATE = "flowerCertificateChooserFileCertificate";
    final static String FILE_PRIVATE_KEY = "flowerCertificateChooserFilePrivateKey";

    @FXML @Nullable TextField fileCertificateTextField;
    @FXML @Nullable TextField filePrivateKeyTextField;

    @Nullable Certificate fileCertificate;
    @Nullable PrivateKey fileKey;

    protected final Stage mainStage;

    //TODO: access intentionally not safe
    @Nullable FileKeyContext currentContext = null;
    static final class FileKeyContext {
        final Certificate fileCertificate;
        final PrivateKey fileKey;
        final KeyManagerFactory keyManagerFactory;

        FileKeyContext(Certificate fileCertificate, PrivateKey fileKey, KeyManagerFactory keyManagerFactory) {
            this.fileCertificate = fileCertificate;
            this.fileKey = fileKey;
            this.keyManagerFactory = keyManagerFactory;
        }

        public boolean sameContext(Certificate fileCertificate, PrivateKey fileKey) {
            return Objects.equals(fileCertificate, this.fileCertificate)
                    && Objects.equals(fileKey, this.fileKey);
        }
    }

    public RsaFileKeyProvider(Stage mainStage) {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("RsaFileKeyProvider.fxml"));
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }

        this.mainStage = mainStage;
    }

    public void testFileKeys() {
        try {
            if (fileCertificate == null) {
                throw new RuntimeException("Certificate not loaded");
            }
            if (fileKey == null) {
                throw new RuntimeException("Key not loaded");
            }
            RsaRawKeyProvider.testKeys(fileCertificate, fileKey);
        } catch (Exception e) {
            LOGGER.error("File keys test error", e);
            Alert alert = new Alert(Alert.AlertType.ERROR, e.toString(), ButtonType.OK);
            alert.showAndWait();
        }
    }

    public void openCertificateFile() {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Certificate (*.crt)", "*.crt"));
            fileChooser.setTitle("Load Certificate");
            File certificateFile = fileChooser.showOpenDialog(checkNotNull(mainStage));
            if (certificateFile == null) { return; }

            checkNotNull(fileCertificateTextField).textProperty().set(certificateFile.getPath());

            loadCertificateFromFile(certificateFile);
        } catch (Exception e) {
            LOGGER.error("Error opening certificate file", e);
            Alert alert = new Alert(Alert.AlertType.ERROR, e.toString(), ButtonType.OK);
            alert.showAndWait();
        }
    }

    protected void loadCertificateFromFile(File certificateFile) throws IOException {
        fileCertificate = PkiUtil.getCertificateFromStream(new FileInputStream(certificateFile));
        Alert alert = new Alert(Alert.AlertType.INFORMATION, "Certificate successfully loaded: " + certificateFile.getPath(), ButtonType.OK);
        alert.showAndWait();
    }

    public void openPrivateKeyFile() {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Key (*.key)", "*.key"));
            fileChooser.setTitle("Load Key");
            File keyFile = fileChooser.showOpenDialog(checkNotNull(mainStage));
            if (keyFile == null) { return; }

            checkNotNull(filePrivateKeyTextField).textProperty().set(keyFile.getPath());

            loadPrivateKeyFromFile(keyFile);
        } catch (Exception e) {
            LOGGER.error("Error opening private key file", e);
            Alert alert = new Alert(Alert.AlertType.ERROR, e.toString(), ButtonType.OK);
            alert.showAndWait();
        }
    }

    protected void loadPrivateKeyFromFile(File keyFile) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        fileKey = PkiUtil.getPrivateKeyFromStream(new FileInputStream(keyFile));
        Alert alert = new Alert(Alert.AlertType.INFORMATION, "Key successfully loaded: " + keyFile.getPath(), ButtonType.OK);
        alert.showAndWait();
    }

    public void loadCertificateFileKey(KeyEvent event) {
        try {
            if (event.getCode() == KeyCode.ENTER) {
                loadCertificateFile();
            }
        } catch (Exception e) {
            LOGGER.error("Error loading certificate from file", e);
            Alert alert = new Alert(Alert.AlertType.ERROR, e.toString(), ButtonType.OK);
            alert.showAndWait();
        }
    }

    public void loadPrivateKeyFileKey(KeyEvent event) {
        try {
            if (event.getCode() == KeyCode.ENTER) {
                loadPrivateKeyFile();
            }
        } catch (Exception e) {
            LOGGER.error("Error loading private key from file", e);
            Alert alert = new Alert(Alert.AlertType.ERROR, e.toString(), ButtonType.OK);
            alert.showAndWait();
        }
    }

    public void loadCertificateFile() {
        try {
            File certificateFile = new File(checkNotNull(fileCertificateTextField).textProperty().get());
            loadCertificateFromFile(certificateFile);
        } catch (Exception e) {
            LOGGER.error("Error loading certificate from file", e);
            Alert alert = new Alert(Alert.AlertType.ERROR, e.toString(), ButtonType.OK);
            alert.showAndWait();
        }
    }

    public void loadPrivateKeyFile() {
        try {
            File keyFile = new File(checkNotNull(filePrivateKeyTextField).textProperty().get());
            loadPrivateKeyFromFile(keyFile);
        } catch (Exception e) {
            LOGGER.error("Error loading private key from file", e);
            Alert alert = new Alert(Alert.AlertType.ERROR, e.toString(), ButtonType.OK);
            alert.showAndWait();
        }
    }

    @Override
    public String tabName() {
        return "Files";
    }

    @Override
    public AnchorPane tabContent() {
        return this;
    }

    @Override
    public KeyManagerFactory getKeyManagerFactory() {
        try {
            if (fileCertificate == null) {
                throw new RuntimeException("Certificate not loaded");
            }
            if (fileKey == null) {
                throw new RuntimeException("Key not loaded");
            }
            if (currentContext != null && currentContext.sameContext(fileCertificate, fileKey)) {
                return currentContext.keyManagerFactory;
            } else {
                KeyManagerFactory keyManagerFactory = PkiUtil.getKeyManagerFromCertAndPrivateKey((X509Certificate)fileCertificate, fileKey);
                currentContext = new FileKeyContext(fileCertificate, fileKey, keyManagerFactory);
                return keyManagerFactory;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public KeyContext getKeyContext() {
        try {
            if (fileCertificate == null) {
                throw new RuntimeException("Certificate not loaded");
            }
            if (fileKey == null) {
                throw new RuntimeException("Key not loaded");
            }
            return RsaKeyContext.of(fileCertificate.getPublicKey(), fileKey, (X509Certificate)fileCertificate);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void initPreferences() {
        loadCertificateChooserPreferences();
        setCertificateChooserPreferencesHandlers();
    }

    public void loadCertificateChooserPreferences() {
        checkNotNull(fileCertificateTextField).textProperty().set(fileCertificate());
        checkNotNull(filePrivateKeyTextField).textProperty().set(filePrivateKey());
    }
    public void setCertificateChooserPreferencesHandlers() {
        checkNotNull(fileCertificateTextField).textProperty().addListener(this::certificateChooserTextChanged);
        checkNotNull(filePrivateKeyTextField).textProperty().addListener(this::certificateChooserTextChanged);
    }

    public void certificateChooserTextChanged(ObservableValue<? extends String> observable, String _old, String _new) {
        updateCertificateChooserPreferences();
    }

    public void updateCertificateChooserPreferences() {
        String fileCertificate = checkNotNull(fileCertificateTextField).textProperty().get();
        String filePrivateKey = checkNotNull(filePrivateKeyTextField).textProperty().get();
        updateFileUserPreferences(fileCertificate, filePrivateKey);
    }

    public static void updateFileUserPreferences(String fileCertificate, String filePrivateKey) {
        Preferences userPreferences = Preferences.userRoot();

        updateUserPreference(userPreferences, FILE_CERTIFICATE, fileCertificate);
        updateUserPreference(userPreferences, FILE_PRIVATE_KEY, filePrivateKey);
    }

    public static String fileCertificate() { return getUserPreference(FILE_CERTIFICATE); }
    public static String filePrivateKey() { return getUserPreference(FILE_PRIVATE_KEY); }
}