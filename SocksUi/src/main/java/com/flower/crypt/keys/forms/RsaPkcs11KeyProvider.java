package com.flower.crypt.keys.forms;

import com.flower.crypt.keys.KeyContext;
import com.flower.fxutils.ModalWindow;
import javafx.beans.value.ObservableValue;
import javafx.stage.Stage;
import org.apache.commons.lang3.StringUtils;
import com.flower.crypt.keys.RsaKeyContext;
import com.flower.crypt.keys.RsaKeyProvider;
import com.flower.crypt.PkiUtil;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Objects;
import java.util.prefs.Preferences;

import static com.flower.crypt.keys.UserPreferencesManager.getUserPreference;
import static com.flower.crypt.keys.UserPreferencesManager.updateUserPreference;
import static com.google.common.base.Preconditions.checkNotNull;

public class RsaPkcs11KeyProvider extends AnchorPane implements TabKeyProvider, RsaKeyProvider {
    final static Logger LOGGER = LoggerFactory.getLogger(RsaPkcs11KeyProvider.class);

    final static String PKCS11_LIBRARY_PATH = "flowerCertificateChooserPkcs11LibraryPath";
    final static String PKCS11_CERTIFICATE_ALIAS = "flowerCertificateChooserPkcs11CertificateAlias";
    final static String PKCS11_PRIVATE_KEY_ALIAS = "flowerCertificateChooserPkcs11PrivateKeyAlias";

    @FXML @Nullable TextField pkcs11LibTextField;
    @FXML @Nullable PasswordField pkcs11TokenPinTextField;
    @FXML @Nullable ComboBox<String> certificatesComboBox;
    @FXML @Nullable ComboBox<String> privateKeysComboBox;

    @Nullable KeyStore pkcs11KeyStore;
    protected final Stage mainStage;

    //TODO: access intentionally not safe
    @Nullable Pkcs11KeyContext currentContext = null;
    static final class Pkcs11KeyContext {
        final KeyStore pkcs11KeyStore;
        final KeyManagerFactory keyManagerFactory;

        Pkcs11KeyContext(KeyStore pkcs11KeyStore, KeyManagerFactory keyManagerFactory) {
            this.pkcs11KeyStore = pkcs11KeyStore;
            this.keyManagerFactory = keyManagerFactory;
        }

        public boolean sameContext(KeyStore pkcs11KeyStore) {
            return Objects.equals(pkcs11KeyStore, this.pkcs11KeyStore);
        }
    }

    public RsaPkcs11KeyProvider(Stage mainStage) {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("RsaPkcs11KeyProvider.fxml"));
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }

        this.mainStage = mainStage;
    }

    public void testPkcs11Keys() {
        try {
            if (pkcs11KeyStore == null) {
                throw new RuntimeException("PKCS#11 store not loaded");
            }
            String certAlias = checkNotNull(certificatesComboBox).getValue();
            String keyAlias = checkNotNull(privateKeysComboBox).getValue();

            Certificate certificate = PkiUtil.getCertificateFromKeyStore(checkNotNull(pkcs11KeyStore), certAlias);
            PrivateKey key = (PrivateKey)PkiUtil.getKeyFromKeyStore(pkcs11KeyStore, keyAlias);

            RsaRawKeyProvider.testKeys(certificate, key);
        } catch (Exception e) {
            LOGGER.error("PKCS11 keys test error", e);
            Alert alert = new Alert(Alert.AlertType.ERROR, e.toString(), ButtonType.OK);
            alert.showAndWait();
        }
    }

    @Override
    public String tabName() {
        return "PKCS#11";
    }

    @Override
    public AnchorPane tabContent() {
        return this;
    }

    @Override
    public KeyManagerFactory getKeyManagerFactory() {
        if (pkcs11KeyStore == null) {
            throw new RuntimeException("PKCS#11 store not loaded");
        }

        if (currentContext != null && currentContext.sameContext(pkcs11KeyStore)) {
            return currentContext.keyManagerFactory;
        } else {
            try {
                KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                keyManagerFactory.init(pkcs11KeyStore, null);
                currentContext = new Pkcs11KeyContext(pkcs11KeyStore, keyManagerFactory);
                return keyManagerFactory;
            } catch (NoSuchAlgorithmException | UnrecoverableKeyException | KeyStoreException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public KeyContext getKeyContext() {
        if (pkcs11KeyStore == null) {
            throw new RuntimeException("PKCS#11 store not loaded");
        }
        String certAlias = checkNotNull(certificatesComboBox).getValue();
        String keyAlias = checkNotNull(privateKeysComboBox).getValue();

        Certificate certificate = PkiUtil.getCertificateFromKeyStore(checkNotNull(pkcs11KeyStore), certAlias);
        PrivateKey key = (PrivateKey)PkiUtil.getKeyFromKeyStore(pkcs11KeyStore, keyAlias);
        return RsaKeyContext.of(certificate.getPublicKey(), key, (X509Certificate)certificate);
    }

    public void loadPkcs11() {
        try {
            String pkcs11Lib = checkNotNull(pkcs11LibTextField).textProperty().get();
            String pkcs11TokenPin = checkNotNull(pkcs11TokenPinTextField).textProperty().get();

            if (StringUtils.isBlank(pkcs11Lib)) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "PKCS#11 Library Path is empty", ButtonType.OK);
                alert.showAndWait();
                return;
            }

            pkcs11KeyStore = PkiUtil.loadPKCS11KeyStore(pkcs11Lib, pkcs11TokenPin);

            List<String> keyAliases = PkiUtil.getKeyAliasesFromKeyStore(pkcs11KeyStore);
            List<String> certificateAliases = PkiUtil.getCertificateAliasesFromKeyStore(pkcs11KeyStore);

            String certificateBefore = checkNotNull(certificatesComboBox).valueProperty().get();
            String keyBefore = checkNotNull(privateKeysComboBox).valueProperty().get();

            checkNotNull(certificatesComboBox).getItems().clear();
            checkNotNull(certificatesComboBox).getItems().addAll(certificateAliases);
            if (!certificateAliases.isEmpty()) {
                if (!certificateAliases.contains(certificateBefore)) {
                    certificateBefore = certificateAliases.get(0);
                }
                checkNotNull(certificatesComboBox).valueProperty().set(certificateBefore);
            }

            checkNotNull(privateKeysComboBox).getItems().clear();
            checkNotNull(privateKeysComboBox).getItems().addAll(keyAliases);
            if (!keyAliases.isEmpty()) {
                if (!keyAliases.contains(keyBefore)) {
                    keyBefore = keyAliases.get(0);
                }
                checkNotNull(privateKeysComboBox).valueProperty().set(keyBefore);
            }

            Alert alert = new Alert(Alert.AlertType.INFORMATION, "PKCS#11 successfully loaded", ButtonType.OK);
            alert.showAndWait();
        } catch (Exception e) {
            LOGGER.error("Error loading PKCS#11", e);
            Alert alert = new Alert(Alert.AlertType.ERROR, e.toString(), ButtonType.OK);
            alert.showAndWait();
        }
    }

    @Override
    public void initPreferences() {
        loadCertificateChooserPreferences();
        setCertificateChooserPreferencesHandlers();
    }

    public void loadCertificateChooserPreferences() {
        checkNotNull(pkcs11LibTextField).textProperty().set(pkcs11LibraryPath());
        checkNotNull(certificatesComboBox).getSelectionModel().select(pkcs11CertificateAlias());
        checkNotNull(privateKeysComboBox).getSelectionModel().select(pkcs11PrivateKeyAlias());
    }
    public void setCertificateChooserPreferencesHandlers() {
        checkNotNull(pkcs11LibTextField).textProperty().addListener(this::certificateChooserTextChanged);
        checkNotNull(certificatesComboBox).valueProperty().addListener(this::certificateChooserTextChanged);
        checkNotNull(privateKeysComboBox).valueProperty().addListener(this::certificateChooserTextChanged);
    }

    public void certificateChooserTextChanged(ObservableValue<? extends String> observable, String _old, String _new) {
        updateCertificateChooserPreferences();
    }

    public void updateCertificateChooserPreferences() {
        String pkcs11LibraryPath = checkNotNull(pkcs11LibTextField).textProperty().get();
        String pkcs11CertificateAlias = checkNotNull(certificatesComboBox).valueProperty().get();
        String pkcs11PrivateKeyAlias = checkNotNull(privateKeysComboBox).valueProperty().get();

        updatePkcs11UserPreferences(pkcs11LibraryPath, pkcs11CertificateAlias,
                pkcs11PrivateKeyAlias);
    }

    public static void updatePkcs11UserPreferences(String pkcs11LibraryPath, String pkcs11CertificateAlias,
                                                   String pkcs11PrivateKeyAlias) {
        Preferences userPreferences = Preferences.userRoot();

        updateUserPreference(userPreferences, PKCS11_LIBRARY_PATH, pkcs11LibraryPath);
        updateUserPreference(userPreferences, PKCS11_CERTIFICATE_ALIAS, pkcs11CertificateAlias);
        updateUserPreference(userPreferences, PKCS11_PRIVATE_KEY_ALIAS, pkcs11PrivateKeyAlias);
    }

    public static String pkcs11LibraryPath() { return getUserPreference(PKCS11_LIBRARY_PATH); }
    public static String pkcs11CertificateAlias() { return getUserPreference(PKCS11_CERTIFICATE_ALIAS); }
    public static String pkcs11PrivateKeyAlias() { return getUserPreference(PKCS11_PRIVATE_KEY_ALIAS); }

    /** Show Text dialog */
    protected void showTextDialog(String title, String text) {
        try {
            ShowTextDialog dialog = new ShowTextDialog(text);
            Stage workspaceStage = ModalWindow.showModal(checkNotNull(mainStage),
                    stage -> { dialog.setStage(stage); return dialog; },
                    title);
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Error showing text dialog: " + e, ButtonType.OK);
            LOGGER.error("Error showing text dialog: ", e);
            alert.showAndWait();
        }
    }

    public void showCertificate() {
        try {
            if (pkcs11KeyStore == null) {
                throw new RuntimeException("PKCS#11 store not loaded");
            }
            String certAlias = checkNotNull(certificatesComboBox).getValue();
            Certificate certificate = PkiUtil.getCertificateFromKeyStore(checkNotNull(pkcs11KeyStore), certAlias);
            String certificateStr = PkiUtil.getCertificateAsPem(certificate);
            showTextDialog("Certificate: " + certAlias, certificateStr);
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Error showing certificate: " + e, ButtonType.OK);
            LOGGER.error("Error showing certificate: ", e);
            alert.showAndWait();
        }
    }

    public void showKey() {
        try {
            if (pkcs11KeyStore == null) {
                throw new RuntimeException("PKCS#11 store not loaded");
            }
            String keyAlias = checkNotNull(privateKeysComboBox).getValue();
            PrivateKey key = (PrivateKey)PkiUtil.getKeyFromKeyStore(pkcs11KeyStore, keyAlias);
            String keyStr = PkiUtil.getKeyAsPem(key);
            showTextDialog("Key: " + keyAlias, keyStr);
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Error showing key: " + e, ButtonType.OK);
            LOGGER.error("Error showing key: ", e);
            alert.showAndWait();
        }
    }
}