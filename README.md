# SynChat

A local-network instant messenger built with JavaFX, a multi-threaded Java
server, and SQLite. This document maps every requirement in the project
rubric to the exact place it's implemented, and explains how to run and
demo it.

## Requirements

- JDK 17 or newer
- Maven 3.8+
- An internet connection **the first time you build** (Maven needs to
  download the JavaFX, SQLite JDBC, and org.json dependencies listed in
  `pom.xml`). No internet is required to chat afterwards, except for the
  optional "quote of the day" banner.

## How to run

```bash
mvn clean javafx:run
```

Each time you run this command you get one instance of the app, starting
on the **Welcome** screen.

### Demonstrating multiple users on ONE machine (local)

1. Run `mvn clean javafx:run`, click **Host on this Wi-Fi Network**. This
   starts the server on port 5000 and logs you in as a client too.
2. Open a second terminal and run `mvn clean javafx:run` again. In the new
   window, type `127.0.0.1` in the IP box and click **Join a Server**.
3. Register a second account and log in. You now have two independent
   client windows, each on its own socket, both served by the same
   server — talk between them to see the thread pool and message routing
   working in real time. Repeat for a third, fourth, etc.

### Demonstrating communication over the current Wi-Fi network

1. On the "host" computer, run `mvn clean javafx:run` and click
   **Host on this Wi-Fi Network**. A dialog shows your machine's current
   Wi-Fi IP address (e.g. `192.168.1.23`) — this comes from
   `NetworkUtils.getLocalWifiAddress()`, which reads the OS network
   interfaces at runtime.
2. Make sure both machines are on the **same Wi-Fi network** and that your
   firewall allows inbound TCP connections on port 5000.
3. On a second computer, run the app, type the host's IP address into the
   **Server IP** field, and click **Join a Server**.
4. Register/log in on both machines and chat — messages now travel over
   the Wi-Fi network's TCP/IP stack between the two physical devices.

## Rubric coverage

| Requirement | Where it lives |
|---|---|
| **Advanced OOP — Interfaces** | `model/JsonConvertible.java`, `repository/CrudRepository.java` (generic interface), `net/MessageListener.java` (Observer-style callback) |
| **Advanced OOP — Abstract classes** | `model/ChatParticipant.java` (abstract base of `User`), `repository/AbstractRepository.java` (Template Method pattern — shared JDBC logic, subclasses only supply table name + row mapping) |
| **Advanced OOP — Polymorphism / inheritance** | `User extends ChatParticipant`; `UserRepository`, `MessageRepository`, `FriendRepository` all `extends AbstractRepository<T, ID>` and override its abstract hooks |
| **JavaFX layout panes & controls** | `BorderPane` (`MainChatView` root), `StackPane` (`WelcomeView`, `LoginView`, `RegisterView`), `SplitPane`, `TabPane`, `GridPane` (login/register forms), `ListView`, `MenuBar`/`Menu`/`MenuItem`, `PasswordField`, `TextField`, `TextInputDialog`, `ScrollPane`, `Alert` |
| **Layout responsiveness** | `MainChatView`: sidebar divider position is recalculated from `stage.widthProperty()`; quote banner `maxWidthProperty()` bound to window width; chat bubble `maxWidthProperty()` bound to `chatLog.widthProperty()`; `HBox.setHgrow`/`VBox.setVgrow` used so controls grow/shrink with the window instead of using fixed pixel sizes |
| **Concurrency / multi-threading** | `server/ChatServer.java` uses `Executors.newCachedThreadPool()` — every accepted socket becomes a `ClientHandler` task run on its own pooled thread (see `server/ClientHandler.java`); the accept loop itself runs on a dedicated daemon thread; `net/NetworkClient.java` reads from its socket on a background `ExecutorService` thread and marshals results back to the JavaFX Application Thread with `Platform.runLater` |
| **Database integration (SQLite)** | `db/Database.java`: singleton connection to `synchat.db`, schema for `users`, `friends`, `messages` with `FOREIGN KEY ... REFERENCES users(id) ON DELETE CASCADE` establishing the relationships |
| **Data manipulation — full CRUD** | `repository/UserRepository.java`, `repository/FriendRepository.java`, `repository/MessageRepository.java` each implement **C**reate, **R**ead, **U**pdate and **D**elete against their table (Delete is inherited from `AbstractRepository`) |
| **Networking & data parsing (HTTP + JSON)** | `service/QuoteService.java` performs a real `HttpClient` GET request to a public quotes API and parses the JSON response with `org.json`; separately, the entire client/server chat protocol (`net/Protocol.java`) is JSON-over-TCP, parsed with `org.json.JSONObject` on both ends |

## Project layout

```
src/main/java/com/synchat/
  Main.java                 JavaFX entry point
  model/                    User, Message, FriendRequest, enums, JsonConvertible
  repository/               CrudRepository, AbstractRepository, *Repository (CRUD/SQLite)
  db/                       Database singleton + schema
  net/                      Protocol, MessageListener, NetworkClient (client socket)
  server/                   ChatServer (thread pool), ClientHandler (per-client thread)
  service/                  QuoteService (HTTP + JSON)
  util/                     NetworkUtils (Wi-Fi IP lookup)
  ui/                       WelcomeView, LoginView, RegisterView, MainChatView
```

## Notes

- Passwords are hashed with SHA-256 before storage (`UserRepository.hashPassword`);
  this is fine for a class project but is not a production-grade password
  scheme (no salt/adaptive hashing).
- The chat protocol is a simple newline-delimited JSON format for
  readability; it is not encrypted, so only use it on a trusted local
  network.
- If port 5000 is already in use, change the port number in
  `ui/WelcomeView.java` (`PORT` constant).
