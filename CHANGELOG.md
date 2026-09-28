# SynChat — Changelog

All notable changes to the project since the initial build, in order. Each
entry lists exactly which files were touched and what changed in them, so
you can point to this if you need to explain your development history.

---

## v1.4.0 — Manual refresh button

**Why:** live server broadcasts keep the Online/Friends/Requests lists
up to date automatically, but a manual refresh is a useful safety net —
e.g. if someone's app crashed instead of closing cleanly and the server
hasn't noticed the disconnect yet, or you just want to force an
up-to-the-second check.

- **`ui/MainChatView.java`**
  - Extracted the three "ask the server for a fresh snapshot" sends
    (`REQUEST_USER_LIST`, `REQUEST_FRIENDS_LIST`, `REQUEST_FRIEND_REQUESTS`)
    out of the constructor and into a new `requestFullRefresh()` method.
  - Added a **"⟳ Refresh"** `Button` to the top header bar (next to the
    quote-of-the-day label), wired to call `requestFullRefresh()`.
  - The constructor now calls `requestFullRefresh()` once on load (same
    behavior as before, just refactored into a reusable method).
  - Quote label's responsive `maxWidthProperty()` binding was widened
    slightly (from `-220` to `-290`) to leave room for the new button
    without it getting squeezed off-screen at smaller window sizes.

---

## v1.3.0 — Dedicated Friends tab

**Why:** the sidebar only showed "who's online right now," with no way to
see your actual accepted friends as a persistent list, or tell who among
them is currently online.

- **`net/Protocol.java`**
  - Added `REQUEST_FRIENDS_LIST` (client → server) and `FRIENDS_LIST`
    (server → client) message type constants.

- **`server/ClientHandler.java`**
  - Added `buildFriendsListPayload(forUserId, friends, users)` — resolves
    a user's `ACCEPTED` rows in the `friends` table into actual usernames
    (the table doesn't record which side is "you," so this picks whichever
    of the two linked ids isn't `forUserId`).
  - Added `sendFriendsListToSelf()` and a `REQUEST_FRIENDS_LIST` case in
    the `handle()` switch, so a client can ask for its friends list on
    demand.
  - `handleFriendResponse()` now pushes a fresh `FRIENDS_LIST` to **both**
    people the instant a request is accepted, instead of requiring a relog.

- **`ui/MainChatView.java`**
  - Added `friendUsernames` (a second `ObservableList<String>`) and a
    second `ListView<String> friendsListView`, shown in a new **"Friends"**
    `Tab` alongside "Online" and "Requests."
  - Added a custom `setCellFactory(...)` on `friendsListView` that
    color-codes each row green ("Online") or gray ("Offline") by checking
    membership in the existing `onlineUsers` list — no extra data needed
    from the server for this.
  - The `USER_LIST` case in `onServerEvent(...)` now also calls
    `friendsListView.refresh()`, forcing the Friends tab's Online/Offline
    labels to redraw whenever the online roster changes (a `ListView`
    doesn't auto-redraw cells just because an *unrelated* list changed).
  - Added a `FRIENDS_LIST` case in `onServerEvent(...)` to populate
    `friendUsernames` from the server's payload.
  - Selecting a name in the new Friends tab opens a conversation the same
    way the Online tab already did.

---

## v1.2.0 — Working friend request Accept/Decline

**Why:** the "Requests" tab was a static placeholder label — incoming
friend requests only ever showed up as a one-off pop-up notification, with
no way to actually accept or decline them.

- **`net/Protocol.java`**
  - Added `FRIEND_RESPONSE_RESULT`, `REQUEST_FRIEND_REQUESTS`, and
    `FRIEND_REQUESTS_LIST` message type constants.

- **`server/ClientHandler.java`**
  - `handleFriendRequest(...)` now captures the created `FriendRequest`'s
    database id and includes it (`requestId`) in the live push notice —
    previously the notice only had the sender's username, which wasn't
    enough for the recipient to later respond to a *specific* request.
  - `handleFriendResponse(...)` now looks up the original requester and, if
    they're online, sends them a `FRIEND_RESPONSE_RESULT` telling them
    whether their request was accepted or declined.
  - Added `sendPendingFriendRequestsToSelf()` and a
    `REQUEST_FRIEND_REQUESTS` case, so a client can fetch every request
    still awaiting their response (including ones that arrived while they
    were offline) instead of relying only on live pushes.

- **`ui/MainChatView.java`**
  - Added a private `record IncomingRequest(int requestId, String fromUsername)`.
  - Added `pendingRequests` (an `ObservableList<IncomingRequest>`) and a
    `requestsBox` field; replaced the static placeholder `Label` with
    `rebuildRequestsBox()`, which draws one row per pending request, each
    with its own **Accept** / **Decline** `Button`.
  - Added `respondToRequest(req, accepted)`, which sends `FRIEND_RESPONSE`
    to the server and optimistically removes the row locally.
  - `onServerEvent(...)` gained `FRIEND_REQUESTS_LIST` (populate the whole
    list, used on load) and `FRIEND_RESPONSE_RESULT` (show the outcome)
    cases; the existing `FRIEND_REQUEST` case now also adds the request to
    `pendingRequests` instead of only showing a pop-up.
  - Added a `showResizableAlert(...)` helper used by every `Alert` in the
    class.
  - The "Add Friend..." `TextInputDialog` now calls `setResizable(true)`.

- **`ui/WelcomeView.java`**
  - The "hosting started" and "could not start server" `Alert`s now call
    `setResizable(true)`.

- **`Main.java`**
  - Added an explicit `stage.setResizable(true)` (this was already the
    JavaFX default, but made explicit for clarity).

---

## v1.1.0 — Fixed the online-user-count race condition

**Why:** two client windows could show inconsistent online counts — one
showing users who'd already left, another showing nobody online at all.
Root cause: right after login, the server sends `AUTH_RESULT` immediately
followed by a `USER_LIST` broadcast. The client's login-screen listener
handles `AUTH_RESULT` by queuing (`Platform.runLater`) a switch to
`MainChatView` — but that swap isn't instant. If `USER_LIST` arrived before
the swap ran, it was handed to the *old* listener, which silently ignored
it (it only understood `AUTH_RESULT`), so the new client missed its first
roster update entirely.

- **`net/Protocol.java`**
  - Added the `REQUEST_USER_LIST` message type constant.

- **`server/ClientHandler.java`**
  - Added `sendUserListToSelf()` — sends a private, one-off roster snapshot
    to just the requesting client (as opposed to `broadcastUserList()`,
    which pushes to everyone).
  - Extracted `buildUserListPayload()` as a shared helper used by both
    `broadcastUserList()` and `sendUserListToSelf()`.
  - Added a `REQUEST_USER_LIST` case in the `handle()` switch.
  - `handleRegister(...)`: `broadcastUserList()` now only fires when
    registration actually succeeded (previously it fired unconditionally,
    even after a rejected "username taken" attempt).

- **`ui/MainChatView.java`**
  - The constructor now sends a `REQUEST_USER_LIST` message immediately
    after calling `client.setListener(this)` — guaranteeing a correct
    roster on load regardless of any broadcast that might have been missed
    during the screen transition, rather than waiting passively for the
    next broadcast to happen to fire.

---

## v1.0.0 — Initial build

The original project delivery: full Maven/JavaFX client, multi-threaded
server (`ExecutorService` thread pool, one thread per client), SQLite
persistence (`users` / `friends` / `messages` tables with foreign keys),
CRUD repositories for each table, a JSON-over-TCP wire protocol, an HTTP
"quote of the day" integration, and four JavaFX screens (Welcome, Login,
Register, Main Chat). See `CODE_GUIDE.md` for the full file-by-file
breakdown of this initial architecture.
