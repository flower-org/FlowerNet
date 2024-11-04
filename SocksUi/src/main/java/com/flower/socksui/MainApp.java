package com.flower.socksui;

import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

public class MainApp {
    @Nullable Stage mainStage;

    @FXML @Nullable Label serverInfoLabel;
    @FXML @Nullable TabPane tabs;
    @FXML @Nullable CheckMenuItem httpLogModeCheckBox;

    @Nullable String myUsername;
    @Nullable String server;

    public MainApp() {
        //This form is created automatically.
        //No need to load fxml explicitly
    }

/*    public void showChatForm(List<String> otherUsernames) {
        ChatForm chatForm = new ChatForm(getServer(), getMyUsername(), otherUsernames);
        chatForm.setStage(checkNotNull(mainStage));
        final Tab tab = new Tab(
                String.format("Chat %s / %s", getMyUsername(), otherUsernames.stream().collect(Collectors.joining(", "))),
                chatForm);
        tab.setClosable(true);

        addTab(tab);
    }*/

    public void setMainStage(@Nullable Stage mainStage) {
        this.mainStage = mainStage;
    }

    public void setStatusText(String text) {
        checkNotNull(serverInfoLabel).setText(text);
    }

    public void showAboutDialog() {
        Alert alert = new Alert(Alert.AlertType.NONE, "Socks UI v 0.0.1", ButtonType.OK);
        alert.showAndWait();
    }

    void addTab(Tab tab) {
        checkNotNull(tabs).getTabs().add(tab);
        tabs.getSelectionModel().select(tab);
    }

    public void logout(Event event) {
        closeAllTabs();
//        showSignInDialog(null, checkNotNull(mainStage));
    }

    public void quit() { checkNotNull(mainStage).close(); }

    public void closeAllTabs() {
        checkNotNull(tabs).getTabs().clear();
    }

/*    public void showOpenChatDialog(Event event) {
        OpenChatDialog openChatDialog = new OpenChatDialog(checkNotNull(server), checkNotNull(myUsername), this);
        Stage openChatStage = ModalWindow.showModal(checkNotNull(mainStage),
                        stage -> { openChatDialog.setStage(stage); return openChatDialog; },
                        "Open Group Chat");

        openChatStage.setOnHidden(
                ev -> {
                    try {
                        //Will throw if Sign-in response is null
                        //showSearchForm();
                    } catch (Exception e) {
                        quit();
                    }
                }
        );
    }

    public void showSignInDialog(@Nullable Event event, @Nullable Window window) {
        SignInDialog signInDialog = new SignInDialog();
        Stage signInStage = event != null
                ?
            ModalWindow.showModalUndecorated(event,
            stage -> { signInDialog.setStage(stage); return signInDialog; },
            "Sign in")
                :
            ModalWindow.showModalUndecorated(checkNotNull(window),
            stage -> { signInDialog.setStage(stage); return signInDialog; },
            "Sign in");

        signInStage.setOnHidden(
            ev -> {
                try {
                    myUsername = signInDialog.username();
                    server = signInDialog.server();
                    checkNotNull(serverInfoLabel).textProperty().set(server);
                } catch (Exception e) {
                    quit();
                }
            }
        );
    }
*/
    public String getMyUsername() {
        return checkNotNull(myUsername);
    }

    public String getServer() {
        return checkNotNull(server);
    }
}
