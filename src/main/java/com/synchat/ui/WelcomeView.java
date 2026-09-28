package com.synchat.ui;

import com.synchat.server.ChatServer;
import com.synchat.util.NetworkUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * JAVAFX UI - landing screen built from a StackPane holding a centered
 * VBox "card". Lets the user either HOST a server (bound to all network
 * interfaces so devices on the same Wi-Fi can join) or JOIN an existing
 * one - including joining "127.0.0.1", which is how several instances of
 * this app running on one machine demonstrate multi-user handling locally.
 */
public class WelcomeView extends StackPane {
    private static final int PORT = 5000;
    private final Stage stage;

    public WelcomeView(Stage stage) {
        this.stage = stage;
        getStyleClass().add("auth-root");

        Label title = new Label("SynChat");
        title.getStyleClass().add("app-title");

        Label subtitle = new Label("A local network instant messenger");
        subtitle.getStyleClass().add("subtitle");

        Button hostBtn = new Button("Host on this Wi-Fi Network");
        hostBtn.getStyleClass().add("button-primary");
        hostBtn.setMaxWidth(Double.MAX_VALUE);
        hostBtn.setOnAction(e -> host());

        TextField ipField = new TextField();
        ipField.setPromptText("Server IP (e.g. 192.168.1.23, or 127.0.0.1 for local testing)");

        Button joinBtn = new Button("Join a Server");
        joinBtn.getStyleClass().add("button-outline");
        joinBtn.setMaxWidth(Double.MAX_VALUE);
        joinBtn.setOnAction(e -> join(ipField.getText().isBlank() ? "127.0.0.1" : ipField.getText().trim()));

        VBox box = new VBox(14, title, subtitle, new Separator(), hostBtn, new Separator(), ipField, joinBtn);
        box.getStyleClass().add("card");
        box.setAlignment(Pos.CENTER);
        box.setMaxWidth(440);

        getChildren().add(box);
        setPadding(new Insets(20));
    }

    private void host() {
        try {
            ChatServer.getInstance().start(PORT);
            String ip = NetworkUtils.getLocalWifiAddress();
            Alert alert = new Alert(Alert.AlertType.INFORMATION,
                    "Server started!\n\nOther devices on this Wi-Fi network can join by entering:\n"
                            + ip + "\n\n(You can also run this app again on this same computer and join with 127.0.0.1 to test multiple users locally.)");
            alert.setHeaderText("Hosting on " + ip + ":" + PORT);
            alert.setResizable(true);
            alert.showAndWait();
            goToLogin("127.0.0.1", PORT);
        } catch (Exception ex) {
            Alert error = new Alert(Alert.AlertType.ERROR, "Could not start server: " + ex.getMessage());
            error.setResizable(true);
            error.showAndWait();
        }
    }

    private void join(String host) {
        goToLogin(host, PORT);
    }

    private void goToLogin(String host, int port) {
        stage.getScene().setRoot(new LoginView(stage, host, port));
    }
}
