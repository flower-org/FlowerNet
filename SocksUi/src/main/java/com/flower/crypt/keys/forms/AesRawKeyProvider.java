package com.flower.crypt.keys.forms;

import com.flower.crypt.keys.Aes256KeyContext;
import com.flower.crypt.keys.KeyContext;
import javafx.beans.value.ObservableValue;
import org.apache.commons.lang3.StringUtils;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Base64;
import java.util.prefs.Preferences;

import static com.flower.crypt.keys.UserPreferencesManager.getUserPreference;
import static com.flower.crypt.keys.UserPreferencesManager.updateUserPreference;
import static com.google.common.base.Preconditions.checkNotNull;

public class AesRawKeyProvider extends AnchorPane implements TabKeyProvider {
    final static Logger LOGGER = LoggerFactory.getLogger(AesRawKeyProvider.class);

    final static String AES_KEY = "flowerCertificateChooserAesKey";
    final static String AES_IV_CHECKBOX = "flowerCertificateChooserAesIvCheckbox";
    final static String AES_IV = "flowerCertificateChooserAesIv";

    @FXML @Nullable TextField aes256KeyTextField;
    @FXML @Nullable CheckBox aes256IvCheckBox;
    @FXML @Nullable TextField aes256IvTextField;

    public AesRawKeyProvider() {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("AesRawKeyProvider.fxml"));
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    @Override
    public String tabName() {
        return "AES-256";
    }

    @Override
    public AnchorPane tabContent() {
        return this;
    }

    @Override
    public KeyContext getKeyContext() {
        String aes256Base64Key = checkNotNull(aes256KeyTextField).textProperty().get();
        byte[] aes256Key = Base64.getDecoder().decode(aes256Base64Key);

        byte[] aes256Iv = null;
        if (checkNotNull(aes256IvCheckBox).selectedProperty().get()) {
            String aes256Base64Iv = checkNotNull(aes256IvTextField).textProperty().get();
            aes256Iv = Base64.getDecoder().decode(aes256Base64Iv);
        }
        return Aes256KeyContext.of(aes256Key, aes256Iv);
    }

    @Override
    public void initPreferences() {
        loadCertificateChooserPreferences();
        setCertificateChooserPreferencesHandlers();
    }

    public void loadCertificateChooserPreferences() {
        checkNotNull(aes256KeyTextField).textProperty().set(aesKey());
        String ivCheckboxStr = aesIvCheckbox();
        boolean ivCheckbox = StringUtils.isBlank(ivCheckboxStr) ? false : Boolean.parseBoolean(ivCheckboxStr);
        checkNotNull(aes256IvCheckBox).selectedProperty().set(ivCheckbox);
        checkNotNull(aes256IvTextField).textProperty().set(aesIv());
    }

    public void setCertificateChooserPreferencesHandlers() {
        checkNotNull(aes256KeyTextField).textProperty().addListener(this::certificateChooserTextChanged);
        checkNotNull(aes256IvTextField).textProperty().addListener(this::certificateChooserTextChanged);
        checkNotNull(aes256IvCheckBox).selectedProperty().addListener(this::certificateChooserBoolChanged);
    }

    public void certificateChooserTextChanged(ObservableValue<? extends String> observable, String _old, String _new) {
        updateCertificateChooserPreferences();
    }

    public void certificateChooserBoolChanged(ObservableValue<? extends Boolean> observable, Boolean _old, Boolean _new) {
        updateCertificateChooserPreferences();
    }

    public void updateCertificateChooserPreferences() {
        String aesKey = checkNotNull(aes256KeyTextField).textProperty().get();
        String aesIvCheckbox = checkNotNull(aes256IvTextField).textProperty().get();
        boolean aesIv = checkNotNull(aes256IvCheckBox).selectedProperty().get();

        updateAesUserPreferences(aesKey, aesIvCheckbox, Boolean.toString(aesIv));
    }

    public static void updateAesUserPreferences(String aesKey, String aesIv, String aesIvCheckbox) {
        Preferences userPreferences = Preferences.userRoot();

        updateUserPreference(userPreferences, AES_KEY, aesKey);
        updateUserPreference(userPreferences, AES_IV_CHECKBOX, aesIvCheckbox);
        updateUserPreference(userPreferences, AES_IV, aesIv);
    }

    public static String aesKey() { return getUserPreference(AES_KEY); }
    public static String aesIvCheckbox() { return getUserPreference(AES_IV_CHECKBOX); }
    public static String aesIv() { return getUserPreference(AES_IV); }
}
