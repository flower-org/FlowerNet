package com.flower.net.socksui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * INCORRECT, DON'T RUN THIS
*/
public class SocksUiApplication extends Application {
    /**
     * Don't use this method directly, use SocksUiClientLauncher.
     * For whatever reason, running this directly will fail with an error.
     */
    public static void main(String[] args) {
        launch();
    }

    @Override
    public void start(Stage mainStage) throws IOException {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(SocksUiApplication.class.getResource("MainApp.fxml"));
            Parent rootNode = fxmlLoader.load();

            MainApp mainApp = fxmlLoader.getController();
            mainApp.setMainStage(mainStage);

            Scene mainScene = new Scene(rootNode, 1024, 768);

            //Close all threads when we close JavaFX windows.
            mainStage.setOnHidden(event -> {
                // TODO: close all tabs / clients
                // Shutdown Netty
                Platform.exit();
                mainApp.shutdownServer();
            });

            mainStage.setTitle("Socks UI");
            mainStage.setScene(mainScene);
            mainStage.setResizable(true);
            mainApp.showTabs();
            mainStage.show();
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }
}
