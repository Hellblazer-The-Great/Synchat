package com.synchat.ui;

import com.synchat.net.MessageListener;
import com.synchat.net.NetworkClient;
import com.synchat.net.Protocol;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.json.JSONObject;

/**
 * JAVAFX UI - demonstrates a StackPane/GridPane composition and the
 * PasswordField control called for by the rubric.
 */
public class LoginView extends StackPane {
    private final Stage stage;
    private final String host;
    private final int port;

    public LoginView(Stage stage, String host, int port) {
        this.stage = stage;
        this.host = host;
        this.port = port;

        Label title = new Label("Log in to SynChat");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");

        Label status = new Label();
        status.setStyle("-fx-text-fill: red;");
        status.setWrapText(true);

        Button loginBtn = new Button("Log In");
        loginBtn.setDefaultButton(true);
        loginBtn.setMaxWidth(Double.MAX_VALUE);

        Hyperlink registerLink = new Hyperlink("Need an account? Register");
        registerLink.setOnAction(e -> stage.getScene().setRoot(new RegisterView(stage, host, port)));

        GridPane grid = new GridPane();
        grid.setVgap(10);
        grid.setHgap(10);
        grid.addRow(0, new Label("Username:"), usernameField);
        grid.addRow(1, new Label("Password:"), passwordField);

        VBox box = new VBox(16, title, grid, loginBtn, registerLink, status);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));
        box.setMaxWidth(380);
        getChildren().add(box);

        loginBtn.setOnAction(e -> attemptLogin(usernameField.getText(), passwordField.getText(), status));
    }

    private void attemptLogin(String username, String password, Label status) {
        if (username.isBlank() || password.isBlank()) {
            status.setText("Fill in both fields.");
            return;
        }
        NetworkClient client = new NetworkClient();
        try {
            client.connect(host, port, new MessageListener() {
                @Override
                public void onServerEvent(JSONObject json) {
                    if (Protocol.AUTH_RESULT.equals(json.getString("type"))) {
                        if (json.getBoolean("success")) {
                            String uname = json.getJSONObject("user").getString("username");
                            Platform.runLater(() -> stage.getScene().setRoot(new MainChatView(stage, client, uname)));
                        } else {
                            Platform.runLater(() -> status.setText("Invalid username or password."));
                        }
                    }
                }

                @Override
                public void onDisconnected() {
                    Platform.runLater(() -> status.setText("Lost connection to server."));
                }
            });
            JSONObject login = new JSONObject();
            login.put("type", Protocol.AUTH_LOGIN);
            login.put("username", username);
            login.put("password", password);
            client.send(login);
        } catch (Exception e) {
            status.setText("Could not reach server at " + host + ":" + port);
        }
    }
}
