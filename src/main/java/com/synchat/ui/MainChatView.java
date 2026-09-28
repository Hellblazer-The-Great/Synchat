package com.synchat.ui;

import com.synchat.net.MessageListener;
import com.synchat.net.NetworkClient;
import com.synchat.net.Protocol;
import com.synchat.service.QuoteService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
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
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

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
    /** Deterministic per-user avatar colors - same username always gets the same chip color. */
    private static final String[] AVATAR_PALETTE = {
            "#5B5FEF", "#E4574C", "#2E9E5B", "#D98C22", "#1FA9C0", "#9B51E0", "#C2185B", "#3F51B5"
    };

    private final NetworkClient client;
    private final String myUsername;
    private final ObservableList<String> onlineUsers = FXCollections.observableArrayList();
    private final ListView<String> userList = new ListView<>(onlineUsers);
    private final ObservableList<String> friendUsernames = FXCollections.observableArrayList();
    private final ListView<String> friendsListView = new ListView<>(friendUsernames);
    private final VBox chatLog = new VBox(8);
    private final TextField inputField = new TextField();
    private final Label chatHeader = new Label("Select an online user to start chatting");
    private String activeChatPartner = null;
    /** Live bubbles in the currently-open chat log, keyed by message id, so a later
     *  MESSAGE_EDITED/MESSAGE_DELETED push can find and update/remove the right one. */
    private final Map<Integer, BubbleRefs> bubblesById = new HashMap<>();
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
        menuBar.getStyleClass().add("menu-bar");
        Menu fileMenu = new Menu("File");
        menuBar.getMenus().add(fileMenu);
        fileMenu.setId("fileMenu");

        // SETTINGS MENU - minor, purely client-side preferences. None of
        // these touch the network protocol: dark mode and compact mode are
        // just style-class toggles, and About is a static info dialog.
        Menu settingsMenu = new Menu("Settings");
        menuBar.getMenus().add(settingsMenu);

        CheckMenuItem darkModeItem = new CheckMenuItem("Dark Mode");
        CheckMenuItem compactItem = new CheckMenuItem("Compact Messages");
        MenuItem aboutItem = new MenuItem("About SynChat...");
        settingsMenu.getItems().addAll(darkModeItem, compactItem, new SeparatorMenuItem(), aboutItem);

        compactItem.selectedProperty().addListener((obs, was, isCompact) -> setCompactMode(isCompact));
        aboutItem.setOnAction(e -> showAboutDialog());

        Label quoteLabel = new Label("Loading today's quote...");
        quoteLabel.getStyleClass().add("quote-label");
        quoteLabel.setWrapText(true);
        quoteLabel.maxWidthProperty().bind(this.widthProperty().subtract(360)); // RESPONSIVENESS
        new QuoteService().fetchDailyQuote(quoteLabel::setText);

        Button refreshBtn = new Button("\u27F3 Refresh");
        refreshBtn.getStyleClass().add("button-pill");
        refreshBtn.setTooltip(new Tooltip(
                "Re-fetch who's online, your friends, and pending requests"));
        refreshBtn.setOnAction(e -> requestFullRefresh());

        // Quick-access dark mode toggle, kept in sync with the Settings menu
        // checkbox so either one can flip it.
        ToggleButton darkModeToggle = new ToggleButton("\uD83C\uDF19");
        darkModeToggle.getStyleClass().add("icon-toggle-button");
        darkModeToggle.setTooltip(new Tooltip("Toggle dark mode"));
        darkModeToggle.selectedProperty().bindBidirectional(darkModeItem.selectedProperty());
        darkModeToggle.selectedProperty().addListener((obs, was, isDark) -> applyDarkMode(isDark));

        Label loggedInLabel = new Label("Logged in as: " + myUsername);
        loggedInLabel.getStyleClass().add("logged-in-label");

        HBox header = new HBox(14, loggedInLabel, quoteLabel, darkModeToggle, refreshBtn);
        header.getStyleClass().add("top-bar");
        header.setPadding(new Insets(8, 14, 8, 14));
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(quoteLabel, Priority.ALWAYS); // RESPONSIVENESS: quote fills the gap, buttons stay put

        return new VBox(menuBar, header);
    }

    /** Toggles the "app-dark" style class on the root - see styles.css for the light/dark tokens it swaps. */
    private void applyDarkMode(boolean dark) {
        if (dark) {
            if (!getStyleClass().contains("app-dark")) getStyleClass().add("app-dark");
        } else {
            getStyleClass().remove("app-dark");
        }
    }

    /** Tightens bubble padding/spacing for people who'd rather see more history at once. */
    private void setCompactMode(boolean compact) {
        chatLog.setSpacing(compact ? 3 : 8);
        if (compact) {
            if (!chatLog.getStyleClass().contains("chat-log-compact")) chatLog.getStyleClass().add("chat-log-compact");
        } else {
            chatLog.getStyleClass().remove("chat-log-compact");
        }
    }

    private void showAboutDialog() {
        Alert about = new Alert(Alert.AlertType.INFORMATION);
        about.setTitle("About SynChat");
        about.setHeaderText("SynChat");
        about.setContentText("A local network instant messenger.\n\nLogged in as: " + myUsername
                + "\nBuilt with JavaFX, a multi-threaded Java socket server, and SQLite.");
        about.setResizable(true);
        about.showAndWait();
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
                if (uname.equals(myUsername)) {
                    showResizableAlert(Alert.AlertType.WARNING, "You can't send a friend request to yourself.");
                    return;
                }
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

        userList.setCellFactory(lv -> onlineUserCell());
        Tab onlineTab = new Tab("Online", userList);
        userList.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null) openConversation(selected);
        });

        // FRIENDS TAB: a persistent list of accepted friends, independent of
        // who happens to be connected right now. Each row shows a live
        // Online/Offline indicator by cross-referencing the onlineUsers list
        // that USER_LIST broadcasts already keep up to date.
        friendsListView.setCellFactory(lv -> new ListCell<>() {
            private final Label nameLabel = new Label();
            private final Label statusLabel = new Label();
            private final VBox textBox = new VBox(1, nameLabel, statusLabel);
            private final Region spacer = new Region();
            private final Button unfriendBtn = new Button("Unfriend");
            private final HBox row = new HBox(10);
            {
                row.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(spacer, Priority.ALWAYS);
                unfriendBtn.getStyleClass().add("button-pill-danger");
                // getItem() is safe here: this handler only ever fires from a
                // fully-populated (non-empty) cell the user is looking at.
                unfriendBtn.setOnAction(e -> confirmAndUnfriend(getItem()));
            }

            @Override
            protected void updateItem(String friendUsername, boolean empty) {
                super.updateItem(friendUsername, empty);
                if (empty || friendUsername == null) {
                    setGraphic(null);
                } else {
                    boolean isOnline = onlineUsers.contains(friendUsername);
                    nameLabel.setText(friendUsername);
                    statusLabel.setText(isOnline ? "Online" : "Offline");
                    statusLabel.getStyleClass().setAll(isOnline ? "status-dot-online" : "status-dot-offline");
                    row.getChildren().setAll(avatarChip(friendUsername), textBox, spacer, unfriendBtn);
                    setGraphic(row);
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
        sidebar.getStyleClass().add("sidebar");
        sidebar.setMinWidth(160);
        return sidebar;
    }

    /** Plain rows for the Online tab: avatar chip + username, no actions. */
    private ListCell<String> onlineUserCell() {
        return new ListCell<>() {
            private final Label nameLabel = new Label();
            private final HBox row = new HBox(10, nameLabel);
            {
                row.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(String username, boolean empty) {
                super.updateItem(username, empty);
                if (empty || username == null) {
                    setGraphic(null);
                } else {
                    nameLabel.setText(username);
                    row.getChildren().setAll(avatarChip(username), nameLabel);
                    setGraphic(row);
                }
            }
        };
    }

    /** A small colored circle with the user's first initial - a deterministic color per username. */
    private static Node avatarChip(String username) {
        Circle circle = new Circle(14);
        String initial = username.isEmpty() ? "?" : username.substring(0, 1).toUpperCase();
        circle.setFill(Color.web(AVATAR_PALETTE[Math.floorMod(username.hashCode(), AVATAR_PALETTE.length)]));
        Label initialLabel = new Label(initial);
        initialLabel.getStyleClass().add("avatar-chip-label");
        StackPane chip = new StackPane(circle, initialLabel);
        chip.getStyleClass().add("avatar-chip");
        return chip;
    }

    private BorderPane buildChatArea() {
        BorderPane chatArea = new BorderPane();
        chatArea.getStyleClass().add("chat-area");

        chatHeader.getStyleClass().add("chat-header-label");

        Button clearViewBtn = new Button("Clear view");
        clearViewBtn.getStyleClass().add("button-pill");
        clearViewBtn.setTooltip(new Tooltip(
                "Clears this window only - your saved history is untouched and reloads next time you open this chat"));
        clearViewBtn.setOnAction(e -> {
            chatLog.getChildren().clear();
            bubblesById.clear();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox chatHeaderBar = new HBox(10, chatHeader, spacer, clearViewBtn);
        chatHeaderBar.getStyleClass().add("chat-header-bar");
        chatHeaderBar.setAlignment(Pos.CENTER_LEFT);
        chatHeaderBar.setPadding(new Insets(10, 14, 10, 14));
        chatArea.setTop(chatHeaderBar);

        ScrollPane scrollPane = new ScrollPane(chatLog);
        scrollPane.getStyleClass().add("chat-scroll");
        scrollPane.setFitToWidth(true);
        chatLog.setPadding(new Insets(14));
        chatArea.setCenter(scrollPane);

        inputField.setPromptText("Type a message and press Enter...");
        Button sendBtn = new Button("Send");
        sendBtn.getStyleClass().add("button-primary");
        sendBtn.setOnAction(e -> sendCurrentMessage());
        inputField.setOnAction(e -> sendCurrentMessage());

        HBox inputBar = new HBox(8, inputField, sendBtn);
        inputBar.getStyleClass().add("input-bar");
        inputBar.setPadding(new Insets(10, 14, 10, 14));
        HBox.setHgrow(inputField, Priority.ALWAYS); // RESPONSIVENESS: field grows, button stays fixed size
        chatArea.setBottom(inputBar);

        return chatArea;
    }

    private void openConversation(String username) {
        activeChatPartner = username;
        chatHeader.setText("Chat with " + username);
        chatLog.getChildren().clear();
        bubblesById.clear();

        JSONObject req = new JSONObject();
        req.put("type", Protocol.REQUEST_HISTORY);
        req.put("with", username);
        client.send(req);
    }

    /** Redraws the Requests tab from the current pendingRequests list. */
    private void rebuildRequestsBox() {
        requestsBox.getChildren().clear();
        if (pendingRequests.isEmpty()) {
            Label empty = new Label("No pending friend requests.");
            empty.getStyleClass().add("requests-empty");
            requestsBox.getChildren().add(empty);
            return;
        }
        for (IncomingRequest req : pendingRequests) {
            Label name = new Label(req.fromUsername());
            HBox.setHgrow(name, Priority.ALWAYS);

            Button accept = new Button("Accept");
            accept.getStyleClass().add("button-pill-accept");
            accept.setOnAction(e -> respondToRequest(req, true));

            Button decline = new Button("Decline");
            decline.getStyleClass().add("button-pill-danger");
            decline.setOnAction(e -> respondToRequest(req, false));

            HBox row = new HBox(8, avatarChip(req.fromUsername()), name, accept, decline);
            row.getStyleClass().add("request-row");
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

    /**
     * Confirms before removing a friend - this is destructive (their
     * saved chat history stays put via MessageRepository, but the
     * relationship itself is gone and would need a new friend request to
     * restore) so it shouldn't fire from a stray click.
     */
    private void confirmAndUnfriend(String friendUsername) {
        if (friendUsername == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Remove " + friendUsername + " from your friends list?");
        confirm.setResizable(true);
        confirm.showAndWait()
                .filter(response -> response == ButtonType.OK)
                .ifPresent(response -> {
                    JSONObject req = new JSONObject();
                    req.put("type", Protocol.UNFRIEND);
                    req.put("username", friendUsername);
                    client.send(req);

                    // The server's refreshed FRIENDS_LIST will drop them from
                    // the sidebar; also back out of an open conversation with
                    // them so the chat pane doesn't keep pointing at someone
                    // who's no longer a friend.
                    if (friendUsername.equals(activeChatPartner)) {
                        activeChatPartner = null;
                        chatHeader.setText("Select an online user to start chatting");
                        chatLog.getChildren().clear();
                        bubblesById.clear();
                    }
                });
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

    /** Handles to the pieces of a rendered bubble that MESSAGE_EDITED/MESSAGE_DELETED need to touch later. */
    private record BubbleRefs(HBox row, Label contentLabel, Label editedTag) {}

    private void appendBubble(int id, String author, String content, boolean mine, boolean edited) {
        VBox bubble = new VBox(2);
        bubble.getStyleClass().addAll(mine ? "bubble-mine" : "bubble-theirs", "bubble-wrapper");
        bubble.maxWidthProperty().bind(chatLog.widthProperty().multiply(0.72)); // RESPONSIVENESS

        if (!mine) {
            Label authorLabel = new Label(author);
            authorLabel.getStyleClass().add("bubble-author");
            bubble.getChildren().add(authorLabel);
        }
        Label contentLabel = new Label(content);
        contentLabel.getStyleClass().add("bubble-text");
        contentLabel.setWrapText(true);
        bubble.getChildren().add(contentLabel);

        Label editedTag = new Label("(edited)");
        editedTag.getStyleClass().add("bubble-edited-tag");
        editedTag.setVisible(edited);
        editedTag.setManaged(edited); // collapsed instead of leaving blank space when not edited
        bubble.getChildren().add(editedTag);

        // EDIT/DELETE: only your own messages get the right-click menu - the
        // server enforces the same ownership rule independently, this just
        // keeps the option from being offered on messages it would reject.
        if (mine) {
            ContextMenu menu = new ContextMenu();
            MenuItem editItem = new MenuItem("Edit");
            editItem.setOnAction(e -> beginEdit(id, contentLabel.getText()));
            MenuItem deleteItem = new MenuItem("Delete");
            deleteItem.setOnAction(e -> confirmAndDeleteMessage(id));
            menu.getItems().addAll(editItem, deleteItem);

            contentLabel.setContextMenu(menu); // right-click on the text itself
            bubble.setOnContextMenuRequested(ev -> { // right-click anywhere else in the bubble (padding, edited tag)
                menu.show(bubble, ev.getScreenX(), ev.getScreenY());
                ev.consume();
            });
        }

        HBox row = new HBox(bubble);
        row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        bubblesById.put(id, new BubbleRefs(row, contentLabel, editedTag));
        chatLog.getChildren().add(row);
    }

    /** Opens a pre-filled prompt (same pattern as "Add Friend...") and sends the new text for the server to confirm. */
    private void beginEdit(int id, String currentContent) {
        TextInputDialog dialog = new TextInputDialog(currentContent);
        dialog.setHeaderText("Edit message");
        dialog.setContentText("Message:");
        dialog.setResizable(true);
        dialog.showAndWait().ifPresent(newContent -> {
            if (newContent.isBlank() || newContent.equals(currentContent)) return;
            JSONObject req = new JSONObject();
            req.put("type", Protocol.MESSAGE_EDIT);
            req.put("id", id);
            req.put("content", newContent);
            client.send(req);
        });
    }

    /** Confirms before deleting - same destructive-action pattern as confirmAndUnfriend(). */
    private void confirmAndDeleteMessage(int id) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete this message? This can't be undone.");
        confirm.setResizable(true);
        confirm.showAndWait()
                .filter(response -> response == ButtonType.OK)
                .ifPresent(response -> {
                    JSONObject req = new JSONObject();
                    req.put("type", Protocol.MESSAGE_DELETE);
                    req.put("id", id);
                    client.send(req);
                });
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
                    appendBubble(json.getInt("id"), from, json.getString("content"), mine, json.getBoolean("edited"));
                }
            }
            case Protocol.MESSAGE_HISTORY -> {
                // Guard against a late reply for a conversation the user has
                // already navigated away from by the time it arrives.
                if (json.getString("with").equals(activeChatPartner)) {
                    chatLog.getChildren().clear();
                    bubblesById.clear();
                    JSONArray history = json.getJSONArray("messages");
                    for (int i = 0; i < history.length(); i++) {
                        JSONObject m = history.getJSONObject(i);
                        String from = m.getString("from");
                        appendBubble(m.getInt("id"), from, m.getString("content"), from.equals(myUsername), m.getBoolean("edited"));
                    }
                }
            }
            case Protocol.MESSAGE_EDITED -> {
                BubbleRefs refs = bubblesById.get(json.getInt("id"));
                if (refs != null) {
                    refs.contentLabel().setText(json.getString("content"));
                    refs.editedTag().setVisible(true);
                    refs.editedTag().setManaged(true);
                }
            }
            case Protocol.MESSAGE_DELETED -> {
                BubbleRefs refs = bubblesById.remove(json.getInt("id"));
                if (refs != null) chatLog.getChildren().remove(refs.row());
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
