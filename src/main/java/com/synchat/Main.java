package com.synchat;

import com.synchat.ui.WelcomeView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Entry point. Boots straight into the Welcome screen, where the user
 * chooses to either host a server on the current Wi-Fi network or join
 * one (including joining "127.0.0.1" to run several instances locally).
 */
public class Main extends Application {

    @Override
    public void start(Stage stage) {
        Scene scene = new Scene(new WelcomeView(stage), 950, 620);
        stage.setMinWidth(760);
        stage.setMinHeight(500);
        stage.setResizable(true); // explicit: the whole app window is user-resizable
        stage.setTitle("SynChat");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
