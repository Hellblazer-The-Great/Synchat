package com.synchat.ui;

import com.synchat.net.MessageListener;
import com.synchat.net.NetworkClient;
import com.synchat.net.Protocol;
import com.synchat.service.QuoteService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * JAVAFX UI + LAYOUT RESPONSIVENESS
 * BorderPane root, a SplitPane dividing the sidebar from the chat area, a
 * TabPane for Online users / Friend requests, a ListView of connected
 * users, a MenuBar, and a chat log built from HBox/Label "bubbles".
 *
 * Responsiveness: the sidebar's relative width, the quote banner's wrap
 * width and each chat bubble's max width are all bound to the window's
 * current width instead of being hard-coded, so the layout adapts as the
 * user resizes the application window.
 */
public class MainChatView extends BorderPane implements MessageListener {
    private final NetworkClient client;
    private final String myUsername;
    private final ObservableList<String> onlineUsers = FXCollections.observableArrayList();
    private final ListView<String> userList = new ListView<>(onlineUsers);
    private final VBox chatLog = new VBox(6);
    private final TextField inputField = new TextField();
    private final Label chatHeader = new Label("Select an online user to start chatting");
    private String activeChatPartner = null;

    public MainChatView(Stage stage, NetworkClient client, String myUsername) {
        this.client = client;
        this.myUsername = myUsername;
        client.setListener(this);

        setTop(buildTop());

        SplitPane splitPane = new SplitPane();
        splitPane.getItems().addAll(buildSidebar(), buildChatArea());
        splitPane.setDividerPositions(0.28);
        setCenter(splitPane);

        // RESPONSIVENESS: keep the sidebar a sensible fraction of the window as it's resized.
        stage.widthProperty().addListener((obs, oldW, newW) -> {
            double target = Math.max(0.18, Math.min(0.35, 260.0 / newW.doubleValue()));
            splitPane.setDividerPositions(target);
        });

        setPrefSize(900, 600);
        setupFileMenuActions(stage);
    }

    private VBox buildTop() {
        MenuBar menuBar = new MenuBar();
        Menu fileMenu = new Menu("File");
        menuBar.getMenus().add(fileMenu);
        fileMenu.setId("fileMenu");

        Label quoteLabel = new Label("Loading today's quote...");
        quoteLabel.setStyle("-fx-font-style: italic; -fx-text-fill: #555;");
        quoteLabel.setWrapText(true);
        quoteLabel.maxWidthProperty().bind(this.widthProperty().subtract(220)); // RESPONSIVENESS
        new QuoteService().fetchDailyQuote(quoteLabel::setText);

        HBox header = new HBox(16, new Label("Logged in as: " + myUsername), quoteLabel);
        header.setPadding(new Insets(6, 12, 6, 12));
        header.setAlignment(Pos.CENTER_LEFT);

        return new VBox(menuBar, header);
    }

    private void setupFileMenuActions(Stage stage) {
        MenuBar menuBar = (MenuBar) ((VBox) getTop()).getChildren().get(0);
        Menu fileMenu = menuBar.getMenus().get(0);

        MenuItem addFriend = new MenuItem("Add Friend...");
        addFriend.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setHeaderText("Send a friend request");
            dialog.setContentText("Username:");
            dialog.showAndWait().ifPresent(uname -> {
                JSONObject req = new JSONObject();
                req.put("type", Protocol.FRIEND_REQUEST);
                req.put("username", uname);
                client.send(req);
            });
        });

        MenuItem logout = new MenuItem("Log Out");
        logout.setOnAction(e -> {
            client.close();
            stage.getScene().setRoot(new WelcomeView(stage));
        });

        MenuItem exit = new MenuItem("Exit");
        exit.setOnAction(e -> {
            client.close();
            stage.close();
        });

        fileMenu.getItems().addAll(addFriend, new SeparatorMenuItem(), logout, exit);
    }

    private VBox buildSidebar() {
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab onlineTab = new Tab("Online", userList);
        userList.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null) openConversation(selected);
        });

        VBox requestsBox = new VBox(8);
        requestsBox.setPadding(new Insets(10));
        requestsBox.getChildren().add(new Label("Incoming friend requests appear here as pop-ups."));
        Tab requestsTab = new Tab("Requests", requestsBox);

        tabs.getTabs().addAll(onlineTab, requestsTab);
        VBox.setVgrow(tabs, Priority.ALWAYS);

        VBox sidebar = new VBox(tabs);
        sidebar.setMinWidth(160);
        return sidebar;
    }

    private BorderPane buildChatArea() {
        BorderPane chatArea = new BorderPane();

        chatHeader.setStyle("-fx-font-weight: bold; -fx-padding: 8;");
        chatArea.setTop(chatHeader);

        ScrollPane scrollPane = new ScrollPane(chatLog);
        scrollPane.setFitToWidth(true);
        chatLog.setPadding(new Insets(10));
        chatArea.setCenter(scrollPane);

        inputField.setPromptText("Type a message and press Enter...");
        Button sendBtn = new Button("Send");
        sendBtn.setOnAction(e -> sendCurrentMessage());
        inputField.setOnAction(e -> sendCurrentMessage());

        HBox inputBar = new HBox(8, inputField, sendBtn);
        inputBar.setPadding(new Insets(8));
        HBox.setHgrow(inputField, Priority.ALWAYS); // RESPONSIVENESS: field grows, button stays fixed size
        chatArea.setBottom(inputBar);

        return chatArea;
    }

    private void openConversation(String username) {
        activeChatPartner = username;
        chatHeader.setText("Chat with " + username);
        chatLog.getChildren().clear();
    }

    private void sendCurrentMessage() {
        String text = inputField.getText();
        if (text.isBlank() || activeChatPartner == null) return;
        JSONObject msg = new JSONObject();
        msg.put("type", Protocol.MESSAGE);
        msg.put("to", activeChatPartner);
        msg.put("content", text);
        client.send(msg);
        inputField.clear();
    }

    private void appendBubble(String author, String content, boolean mine) {
        Label bubble = new Label(author + ": " + content);
        bubble.setWrapText(true);
        bubble.setPadding(new Insets(8));
        bubble.setStyle("-fx-background-color: " + (mine ? "#DCF8C6" : "#F1F0F0") + "; -fx-background-radius: 10;");
        bubble.maxWidthProperty().bind(chatLog.widthProperty().multiply(0.75)); // RESPONSIVENESS
        HBox row = new HBox(bubble);
        row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        chatLog.getChildren().add(row);
    }

    @Override
    public void onServerEvent(JSONObject json) {
        String type = json.getString("type");
        switch (type) {
            case Protocol.USER_LIST -> {
                onlineUsers.clear();
                JSONArray arr = json.getJSONArray("users");
                for (int i = 0; i < arr.length(); i++) {
                    String u = arr.getString(i);
                    if (!u.equals(myUsername)) onlineUsers.add(u);
                }
            }
            case Protocol.MESSAGE -> {
                String from = json.getString("from");
                boolean mine = from.equals(myUsername);
                if (activeChatPartner != null && (from.equals(activeChatPartner) || mine)) {
                    appendBubble(from, json.getString("content"), mine);
                }
            }
            case Protocol.FRIEND_REQUEST -> new Alert(Alert.AlertType.INFORMATION,
                    json.getString("fromUsername") + " sent you a friend request!").showAndWait();
            case Protocol.ERROR -> new Alert(Alert.AlertType.WARNING, json.getString("message")).showAndWait();
            default -> { /* ignore unrecognized events */ }
        }
    }

    @Override
    public void onDisconnected() {
        new Alert(Alert.AlertType.ERROR, "Disconnected from server.").showAndWait();
    }
}
