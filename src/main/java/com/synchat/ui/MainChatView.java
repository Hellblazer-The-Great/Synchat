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
import javafx.scene.control.ListCell;
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
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
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
    private final ObservableList<String> friendUsernames = FXCollections.observableArrayList();
    private final ListView<String> friendsListView = new ListView<>(friendUsernames);
    private final VBox chatLog = new VBox(6);
    private final TextField inputField = new TextField();
    private final Label chatHeader = new Label("Select an online user to start chatting");
    private String activeChatPartner = null;
    private final ObservableList<IncomingRequest> pendingRequests = FXCollections.observableArrayList();
    private VBox requestsBox;

    public MainChatView(Stage stage, NetworkClient client, String myUsername) {
        this.client = client;
        this.myUsername = myUsername;
        client.setListener(this);

        // FIX: don't rely solely on the server's live broadcasts, which can
        // land while we were still transitioning screens and get missed.
        // Ask for a guaranteed-fresh snapshot of everything now that we're
        // definitely listening. The same request can be re-sent later via
        // the Refresh button (see buildTop()).
        requestFullRefresh();

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
        quoteLabel.maxWidthProperty().bind(this.widthProperty().subtract(290)); // RESPONSIVENESS
        new QuoteService().fetchDailyQuote(quoteLabel::setText);

        Button refreshBtn = new Button("\u27F3 Refresh");
        refreshBtn.setTooltip(new Tooltip(
                "Re-fetch who's online, your friends, and pending requests"));
        refreshBtn.setOnAction(e -> requestFullRefresh());

        HBox header = new HBox(16, new Label("Logged in as: " + myUsername), quoteLabel, refreshBtn);
        header.setPadding(new Insets(6, 12, 6, 12));
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(quoteLabel, Priority.ALWAYS); // RESPONSIVENESS: quote fills the gap, button stays put

        return new VBox(menuBar, header);
    }

    /**
     * Asks the server for a fresh snapshot of everything the sidebar shows:
     * who's online, your accepted friends, and any pending friend requests.
     * Sent automatically on login (see the constructor) and re-sendable any
     * time via the header's Refresh button - useful if someone logged off
     * in a way the live broadcasts didn't catch (e.g. their app crashed
     * instead of closing cleanly, or a message was in flight during a
     * screen transition).
     */
    private void requestFullRefresh() {
        JSONObject refreshUsers = new JSONObject();
        refreshUsers.put("type", Protocol.REQUEST_USER_LIST);
        client.send(refreshUsers);

        JSONObject refreshFriends = new JSONObject();
        refreshFriends.put("type", Protocol.REQUEST_FRIENDS_LIST);
        client.send(refreshFriends);

        JSONObject refreshRequests = new JSONObject();
        refreshRequests.put("type", Protocol.REQUEST_FRIEND_REQUESTS);
        client.send(refreshRequests);
    }

    private void setupFileMenuActions(Stage stage) {
        MenuBar menuBar = (MenuBar) ((VBox) getTop()).getChildren().get(0);
        Menu fileMenu = menuBar.getMenus().get(0);

        MenuItem addFriend = new MenuItem("Add Friend...");
        addFriend.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setHeaderText("Send a friend request");
            dialog.setContentText("Username:");
            dialog.setResizable(true);
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

        // FRIENDS TAB: a persistent list of accepted friends, independent of
        // who happens to be connected right now. Each row shows a live
        // Online/Offline indicator by cross-referencing the onlineUsers list
        // that USER_LIST broadcasts already keep up to date.
        friendsListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String friendUsername, boolean empty) {
                super.updateItem(friendUsername, empty);
                if (empty || friendUsername == null) {
                    setText(null);
                    setTextFill(Color.BLACK);
                } else {
                    boolean isOnline = onlineUsers.contains(friendUsername);
                    setText(friendUsername + "   \u2022   " + (isOnline ? "Online" : "Offline"));
                    setTextFill(isOnline ? Color.web("#2e7d32") : Color.GRAY);
                }
            }
        });
        friendsListView.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null) openConversation(selected);
        });
        Tab friendsTab = new Tab("Friends", friendsListView);

        VBox requestsBox = new VBox(8);
        requestsBox.setPadding(new Insets(10));
        this.requestsBox = requestsBox;
        rebuildRequestsBox();
        ScrollPane requestsScroll = new ScrollPane(requestsBox);
        requestsScroll.setFitToWidth(true);
        Tab requestsTab = new Tab("Requests", requestsScroll);

        tabs.getTabs().addAll(onlineTab, friendsTab, requestsTab);
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

        JSONObject req = new JSONObject();
        req.put("type", Protocol.REQUEST_HISTORY);
        req.put("with", username);
        client.send(req);
    }

    /** Redraws the Requests tab from the current pendingRequests list. */
    private void rebuildRequestsBox() {
        requestsBox.getChildren().clear();
        if (pendingRequests.isEmpty()) {
            requestsBox.getChildren().add(new Label("No pending friend requests."));
            return;
        }
        for (IncomingRequest req : pendingRequests) {
            Label name = new Label(req.fromUsername());
            HBox.setHgrow(name, Priority.ALWAYS);

            Button accept = new Button("Accept");
            accept.setOnAction(e -> respondToRequest(req, true));

            Button decline = new Button("Decline");
            decline.setOnAction(e -> respondToRequest(req, false));

            HBox row = new HBox(8, name, accept, decline);
            row.setAlignment(Pos.CENTER_LEFT);
            requestsBox.getChildren().add(row);
        }
    }

    private void respondToRequest(IncomingRequest req, boolean accepted) {
        JSONObject resp = new JSONObject();
        resp.put("type", Protocol.FRIEND_RESPONSE);
        resp.put("requestId", req.requestId());
        resp.put("accepted", accepted);
        client.send(resp);

        pendingRequests.removeIf(r -> r.requestId() == req.requestId());
        rebuildRequestsBox();
    }

    /** One incoming friend request waiting on a response: who sent it, and its DB row id. */
    private record IncomingRequest(int requestId, String fromUsername) {}

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
                friendsListView.refresh(); // re-render Online/Offline labels in the Friends tab
            }
            case Protocol.MESSAGE -> {
                String from = json.getString("from");
                boolean mine = from.equals(myUsername);
                if (activeChatPartner != null && (from.equals(activeChatPartner) || mine)) {
                    appendBubble(from, json.getString("content"), mine);
                }
            }
            case Protocol.MESSAGE_HISTORY -> {
                // Guard against a late reply for a conversation the user has
                // already navigated away from by the time it arrives.
                if (json.getString("with").equals(activeChatPartner)) {
                    chatLog.getChildren().clear();
                    JSONArray history = json.getJSONArray("messages");
                    for (int i = 0; i < history.length(); i++) {
                        JSONObject m = history.getJSONObject(i);
                        String from = m.getString("from");
                        appendBubble(from, m.getString("content"), from.equals(myUsername));
                    }
                }
            }
            case Protocol.FRIENDS_LIST -> {
                friendUsernames.clear();
                JSONArray arr = json.getJSONArray("friends");
                for (int i = 0; i < arr.length(); i++) friendUsernames.add(arr.getString(i));
            }
            case Protocol.FRIEND_REQUESTS_LIST -> {
                pendingRequests.clear();
                JSONArray arr = json.getJSONArray("requests");
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    pendingRequests.add(new IncomingRequest(o.getInt("requestId"), o.getString("fromUsername")));
                }
                rebuildRequestsBox();
            }
            case Protocol.FRIEND_REQUEST -> {
                int requestId = json.getInt("requestId");
                String from = json.getString("fromUsername");
                boolean alreadyKnown = pendingRequests.stream().anyMatch(r -> r.requestId() == requestId);
                if (!alreadyKnown) pendingRequests.add(new IncomingRequest(requestId, from));
                rebuildRequestsBox();
                showResizableAlert(Alert.AlertType.INFORMATION, from + " sent you a friend request! Check the Requests tab.");
            }
            case Protocol.FRIEND_RESPONSE_RESULT -> {
                String by = json.getString("byUsername");
                boolean accepted = json.getBoolean("accepted");
                showResizableAlert(Alert.AlertType.INFORMATION,
                        by + (accepted ? " accepted" : " declined") + " your friend request.");
            }
            case Protocol.ERROR -> showResizableAlert(Alert.AlertType.WARNING, json.getString("message"));
            default -> { /* ignore unrecognized events */ }
        }
    }

    /** Small helper so every popup in the app follows the "resizable windows" requirement. */
    private static void showResizableAlert(Alert.AlertType type, String message) {
        Alert alert = new Alert(type, message);
        alert.setResizable(true);
        alert.showAndWait();
    }

    @Override
    public void onDisconnected() {
        showResizableAlert(Alert.AlertType.ERROR, "Disconnected from server.");
    }
}
