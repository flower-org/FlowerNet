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
import javafx.scene.control.TextArea;
import javafx.scene.layout.AnchorPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.net.ssl.KeyManagerFactory;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Objects;
import java.util.prefs.Preferences;

import static com.flower.crypt.keys.UserPreferencesManager.getUserPreference;
import static com.flower.crypt.keys.UserPreferencesManager.updateUserPreference;
import static com.google.common.base.Preconditions.checkNotNull;

public class RsaRawKeyProvider extends AnchorPane implements TabKeyProvider, RsaKeyProvider {
    final static Logger LOGGER = LoggerFactory.getLogger(RsaRawKeyProvider.class);
    final static String RAW_CERTIFICATE = "flowerCertificateChooserRawCertificate";
    final static String RAW_PRIVATE_KEY = "flowerCertificateChooserRawPrivateKey";

    @FXML @Nullable TextArea rawCertificateTextArea;
    @FXML @Nullable TextArea rawPrivateKeyTextArea;

    //TODO: access intentionally not safe
    @Nullable RawKeyContext currentContext = null;
    static final class RawKeyContext {
        final String certificateStr;
        final String keyStr;
        final KeyManagerFactory keyManagerFactory;

        RawKeyContext(String certificateStr, String keyStr, KeyManagerFactory keyManagerFactory) {
            this.certificateStr = certificateStr;
            this.keyStr = keyStr;
            this.keyManagerFactory = keyManagerFactory;
        }

        public boolean sameContext(String certificateStr, String keyStr) {
            return Objects.equals(certificateStr, this.certificateStr)
                    && Objects.equals(keyStr, this.keyStr);
        }
    }

    public RsaRawKeyProvider() {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("RsaRawKeyProvider.fxml"));
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    public void testRawKeys() {
        try {
            String certificateStr = checkNotNull(rawCertificateTextArea).textProperty().get();
            String keyStr = checkNotNull(rawPrivateKeyTextArea).textProperty().get();
            Certificate certificate = PkiUtil.getCertificateFromString(certificateStr);
            PrivateKey key = PkiUtil.getPrivateKeyFromString(keyStr);

            testKeys(certificate, key);
        } catch (Exception e) {
            LOGGER.error("Raw keys test error", e);
            Alert alert = new Alert(Alert.AlertType.ERROR, e.toString(), ButtonType.OK);
            alert.showAndWait();
        }
    }

    public static void testKeys(Certificate certificate, PrivateKey key) throws NoSuchPaddingException,
            IllegalBlockSizeException, NoSuchAlgorithmException, BadPaddingException, InvalidKeyException, SignatureException {
        String encryptTestResult = PkiUtil.testKeyPairMatchByEncrypting(certificate.getPublicKey(), key) ? "SUCCESS" : "FAIL";
        String signTestResult = PkiUtil.testKeyPairMatchBySigning(certificate.getPublicKey(), key) ? "SUCCESS" : "FAIL";

        Alert alert = new Alert(Alert.AlertType.INFORMATION,
                String.format("Encryption test: %s.\nSignature test: %s", encryptTestResult, signTestResult),
                ButtonType.OK);
        alert.showAndWait();
    }

    @Override
    public String tabName() {
        return "Raw";
    }

    @Override
    public AnchorPane tabContent() {
        return this;
    }

    @Override
    public KeyManagerFactory getKeyManagerFactory() {
        try {
            String certificateStr = checkNotNull(rawCertificateTextArea).textProperty().get();
            String keyStr = checkNotNull(rawPrivateKeyTextArea).textProperty().get();

            if (currentContext != null && currentContext.sameContext(certificateStr, keyStr)) {
                return currentContext.keyManagerFactory;
            } else {
                X509Certificate certificate = PkiUtil.getCertificateFromString(certificateStr);
                PrivateKey key = PkiUtil.getPrivateKeyFromString(keyStr);
                KeyManagerFactory keyManagerFactory = PkiUtil.getKeyManagerFromCertAndPrivateKey(certificate, key);
                currentContext = new RawKeyContext(certificateStr, keyStr, keyManagerFactory);
                return keyManagerFactory;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public KeyContext getKeyContext() {
        try {
            String certificateStr = checkNotNull(rawCertificateTextArea).textProperty().get();
            String keyStr = checkNotNull(rawPrivateKeyTextArea).textProperty().get();
            X509Certificate certificate = PkiUtil.getCertificateFromString(certificateStr);
            PrivateKey key = PkiUtil.getPrivateKeyFromString(keyStr);

            return RsaKeyContext.of(certificate.getPublicKey(), key, certificate);
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
        checkNotNull(rawCertificateTextArea).textProperty().set(rawCertificate());
        checkNotNull(rawPrivateKeyTextArea).textProperty().set(rawPrivateKey());
    }

    public void setCertificateChooserPreferencesHandlers() {
        checkNotNull(rawCertificateTextArea).textProperty().addListener(this::certificateChooserTextChanged);;
        checkNotNull(rawPrivateKeyTextArea).textProperty().addListener(this::certificateChooserTextChanged);
    }

    public void certificateChooserTextChanged(ObservableValue<? extends String> observable, String _old, String _new) {
        updateCertificateChooserPreferences();
    }

    public void updateCertificateChooserPreferences() {
        String rawCertificate = checkNotNull(rawCertificateTextArea).textProperty().get();
        String rawPrivateKey = checkNotNull(rawPrivateKeyTextArea).textProperty().get();

        updateUserPreferences(rawCertificate, rawPrivateKey);
    }

    public static void updateUserPreferences(String rawCertificate, String rawPrivateKey) {
        Preferences userPreferences = Preferences.userRoot();

        updateUserPreference(userPreferences, RAW_CERTIFICATE, rawCertificate);
        updateUserPreference(userPreferences, RAW_PRIVATE_KEY, rawPrivateKey);
    }

    public static String rawCertificate() { return getUserPreference(RAW_CERTIFICATE); }
    public static String rawPrivateKey() { return getUserPreference(RAW_PRIVATE_KEY); }
}