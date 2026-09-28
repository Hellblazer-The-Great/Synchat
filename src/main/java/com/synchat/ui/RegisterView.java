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

public class RegisterView extends StackPane {
    private final Stage stage;
    private final String host;
    private final int port;

    public RegisterView(Stage stage, String host, int port) {
        this.stage = stage;
        this.host = host;
        this.port = port;

        Label title = new Label("Create your SynChat account");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        TextField usernameField = new TextField();
        usernameField.setPromptText("Choose a username");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Choose a password");
        PasswordField confirmField = new PasswordField();
        confirmField.setPromptText("Confirm password");

        Label status = new Label();
        status.setStyle("-fx-text-fill: red;");
        status.setWrapText(true);

        Button registerBtn = new Button("Register");
        registerBtn.setDefaultButton(true);
        registerBtn.setMaxWidth(Double.MAX_VALUE);

        Hyperlink backLink = new Hyperlink("Already have an account? Log in");
        backLink.setOnAction(e -> stage.getScene().setRoot(new LoginView(stage, host, port)));

        GridPane grid = new GridPane();
        grid.setVgap(10);
        grid.setHgap(10);
        grid.addRow(0, new Label("Username:"), usernameField);
        grid.addRow(1, new Label("Password:"), passwordField);
        grid.addRow(2, new Label("Confirm:"), confirmField);

        VBox box = new VBox(16, title, grid, registerBtn, backLink, status);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));
        box.setMaxWidth(380);
        getChildren().add(box);

        registerBtn.setOnAction(e ->
                attemptRegister(usernameField.getText(), passwordField.getText(), confirmField.getText(), status));
    }

    private void attemptRegister(String username, String password, String confirm, Label status) {
        if (username.isBlank() || password.isBlank()) {
            status.setText("Fill in all fields.");
            return;
        }
        if (!password.equals(confirm)) {
            status.setText("Passwords do not match.");
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
                            Platform.runLater(() -> status.setText("Registration failed (username taken?)."));
                        }
                    }
                }

                @Override
                public void onDisconnected() {
                    Platform.runLater(() -> status.setText("Lost connection to server."));
                }
            });
            JSONObject reg = new JSONObject();
            reg.put("type", Protocol.AUTH_REGISTER);
            reg.put("username", username);
            reg.put("password", password);
            client.send(reg);
        } catch (Exception e) {
            status.setText("Could not reach server at " + host + ":" + port);
        }
    }
}
