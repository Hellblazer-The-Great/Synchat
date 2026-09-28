# SynChat — Changelog

All notable changes to the project since the initial build, in order. Each
entry lists exactly which files were touched and what changed in them, so
you can point to this if you need to explain your development history.

---

## v1.9.0 — Per-user Add Friend, single Settings menu, Requests dark-mode fix

**Why:** "Add Friend..." required knowing someone's exact username and
digging into a File menu to type it in, even when they were sitting right
there in the Online tab. Compact Messages turned out not to pull its
weight as a setting. And the Requests tab's plain `ScrollPane` (unlike the
Online/Friends tabs' `ListView`s) never got a dark-mode background of its
own, so it showed up as a bright panel once the rest of the app went dark.

- **`ui/MainChatView.java`**
  - `onlineUserCell()` now renders an **Add Friend** button (styled like
    the Accept button, same row layout pattern as the Friends tab's
    Unfriend button) beside each online user, hidden once you're already
    friends with them (checked against `friendUsernames`). The `FRIENDS_LIST`
    case in `onServerEvent(...)` now calls `userList.refresh()` so that
    button disappears the moment a request is accepted, without needing a
    manual refresh.
  - Removed the **File** menu entirely - `MenuItem addFriend` is gone
    (superseded by the per-row button above), and **Log Out**/**Exit** now
    live at the bottom of the **Settings** menu, which is the app's only
    menu now. `buildTop()` takes `Stage stage` directly so it can wire
    those two without the old cast-and-lookup `setupFileMenuActions(...)`
    helper, which is also gone.
  - Removed **Compact Messages** - the `CheckMenuItem`, `setCompactMode(...)`,
    and the `chat-log-compact` style class toggle are all gone; `chatLog`
    just keeps its normal spacing.
  - `requestsScroll` (the Requests tab's `ScrollPane`) now carries a
    `requests-scroll` style class instead of no styling at all.

- **`resources/com/synchat/styles.css`**
  - Added `.requests-scroll`/`.requests-scroll .viewport` (transparent, same
    treatment `.chat-scroll` already used) and `.tab-pane .tab-content-area`
    (pinned to `-fx-surface`) so every sidebar tab's content area is themed
    consistently instead of falling back to JavaFX's default light
    background - this is what was making the Requests tab look broken in
    dark mode specifically.
  - Removed the now-unused `.chat-log-compact` rule.

---

## v1.8.1 — Can't friend-request yourself

- **`server/ClientHandler.java`** — `handleFriendRequest(...)` now rejects
  a request where the target resolves to the caller's own `userId`,
  mirroring the existing "no such user" guard right above it.
- **`ui/MainChatView.java`** — "Add Friend..." checks the typed username
  against `myUsername` first, so self-requests get an instant local
  warning instead of a round trip to the server just to be told no.

---

## v1.8.0 — Message edit/delete + dark mode text-visibility fix

**Why:** the messages table had supported UPDATE/DELETE at the repository
layer since v1.0.0 (see `MessageRepository`'s class doc), but nothing ever
exposed it over the protocol or the UI - once a message was sent, it was
sent forever, typos included. Separately, dark mode (v1.7.0) left the top
`MenuBar`'s "File"/"Settings" labels and the sidebar's tab labels
unreadable: Modena gives those specific labels their own text-fill derived
from the (never-overridden) default `-fx-base`, so they didn't follow the
`.app-dark` token swap the rest of the app uses and ended up dark-on-dark.

- **`db/Database.java`**
  - Added an `edited INTEGER NOT NULL DEFAULT 0` column to `messages`, plus
    a startup `ALTER TABLE ... ADD COLUMN` (swallowing the "already exists"
    error) so a database from before this version picks the column up too.

- **`model/Message.java`**
  - Added an `edited` field/getter and included it in `toJson()`.

- **`repository/MessageRepository.java`**
  - Added `updateContent(id, newContent)` - a second UPDATE alongside the
    existing read-status one, setting the new text and flipping `edited`
    to true. Ownership (only the sender may call this) is enforced by the
    caller, same division of responsibility as everywhere else in this
    class.

- **`net/Protocol.java`**
  - Added `MESSAGE_EDIT`/`MESSAGE_DELETE` (client → server) and
    `MESSAGE_EDITED`/`MESSAGE_DELETED` (server → client) message type
    constants.

- **`server/ClientHandler.java`**
  - Added `handleMessageEdit(...)` and `handleMessageDelete(...)`, plus
    their cases in the `handle()` switch. Both look the message up by id,
    reject anything not owned by the caller, then push a live
    `MESSAGE_EDITED`/`MESSAGE_DELETED` notice to whichever of the two
    participants is online - including the sender, since (same as sending)
    their own view only ever reflects what the server confirms.
  - `handleMessage(...)` and `handleHistoryRequest(...)` now include each
    message's `id` and `edited` flag in the payload - previously omitted
    since nothing needed to reference a specific message afterwards.

- **`ui/MainChatView.java`**
  - `appendBubble(...)` now takes the message's id and edited flag, tags
    the bubble with an `(edited)` label when appropriate, and - for your
    own messages only - attaches a right-click `ContextMenu` with **Edit**
    and **Delete**. Edit reuses the same `TextInputDialog` pattern as "Add
    Friend...", pre-filled with the current text; Delete reuses the same
    confirm-first `Alert` pattern as `confirmAndUnfriend(...)`. Neither
    updates the bubble directly - both just send the request and wait for
    the server's `MESSAGE_EDITED`/`MESSAGE_DELETED` echo, same as a normal
    send.
  - Added `bubblesById`, a `Map<Integer, BubbleRefs>` of the currently
    open conversation's on-screen bubbles, so a later edit/delete event can
    find the right one. Cleared everywhere the chat log itself is cleared
    (`openConversation`, "Clear view", and unfriending the open partner).
  - `onServerEvent(...)` gained `MESSAGE_EDITED` (update the bubble's text
    in place, show the "(edited)" tag) and `MESSAGE_DELETED` (remove the
    bubble) cases.

- **`resources/com/synchat/styles.css`**
  - Pinned `.menu-bar .label` and `.menu-item .label` to `-fx-text-main`,
    and gave `.context-menu` an explicit `-fx-surface` background, fixing
    the invisible "File"/"Settings" text (and every dropdown item under
    them) in dark mode.
  - Gave the sidebar's `.tab-pane` tabs an explicit `-fx-surface`/`-fx-bg`
    background (selected vs. unselected) and pinned `.tab-label` to
    `-fx-text-main` (`-fx-accent` + bold when selected) for the same
    reason - "Online"/"Friends"/"Requests" were going dark-on-dark too.
  - Added `.bubble-edited-tag` for the new "(edited)" indicator.

---

## v1.7.0 — Visual revamp + Settings menu

**Why:** the whole app was styled with scattered inline `-fx-style` strings
and the default Modena look — flat white lists, plain text rows, and no way
to personalize anything. This pass adds a real stylesheet and a small set
of genuinely useful client-side settings, without touching any networking
code.

- **`src/main/resources/com/synchat/styles.css`** *(new file)*
  - A single app-wide stylesheet. Colors are declared as `-fx-*` custom
    properties on `.root` (light theme) and re-declared on `.app-dark`
    (dark theme) — every other rule references a token
    (`-fx-bg`, `-fx-surface`, `-fx-accent`, etc.) instead of a literal
    color, so flipping one style class re-themes the entire app.
  - Adds real classes for cards (`card`), primary/outline/pill buttons,
    text fields, list rows, chat bubbles, avatar chips, and the top/chat
    header bars — replacing the ad hoc `.setStyle("-fx-...")` calls that
    used to be sprinkled through the UI classes.

- **`Main.java`**
  - The `Scene` now loads `styles.css` once
    (`scene.getStylesheets().add(...)`). Because the stylesheet lives on
    the `Scene` rather than any one root node, it automatically applies to
    every screen (`WelcomeView`, `LoginView`, `RegisterView`,
    `MainChatView`) as `stage.getScene().setRoot(...)` swaps between them.

- **`ui/WelcomeView.java`, `ui/LoginView.java`, `ui/RegisterView.java`**
  - Replaced inline `-fx-style` strings with style classes (`auth-root`,
    `card`, `app-title`/`screen-title`, `subtitle`, `status-error`,
    `button-primary`/`button-outline`) from the new stylesheet - same
    layout and behavior, just restyled.

- **`ui/MainChatView.java`**
  - **Settings menu** (new `Menu` next to File): a `CheckMenuItem` **Dark
    Mode** that toggles the `app-dark` style class on the root (see
    `applyDarkMode(...)`); a `CheckMenuItem` **Compact Messages** that
    tightens bubble spacing/padding via a `chat-log-compact` style class
    (see `setCompactMode(...)`); and an **About SynChat...** `MenuItem`
    showing a static info `Alert`.
  - A quick-access **🌙** `ToggleButton` was added to the header, next to
    Refresh, `bindBidirectional`'d to the Dark Mode checkbox so either
    control flips the other.
  - Added a **"Clear view"** `Button` to the chat header bar - clears only
    the currently displayed `chatLog`, not the saved history (a tooltip
    says so explicitly); reopening the conversation reloads it from the
    server as normal.
  - Added `avatarChip(username)`: a small colored circle with the user's
    first initial, colored deterministically from
    `username.hashCode()` against a fixed palette, so the same person
    always gets the same color. Used in the Online tab, the Friends tab,
    and each pending-request row.
  - The Online tab (`userList`) now has a custom cell factory
    (`onlineUserCell()`) instead of showing plain text - avatar chip +
    username.
  - The Friends tab's cell factory (added in v1.6.0 for the Unfriend
    button) now also shows an avatar chip and splits the name/status into
    two stacked labels (`status-dot-online` / `status-dot-offline` style
    classes) instead of one color-coded line of text.
  - `appendBubble(...)` was rewritten from a single `Label` with an inline
    background color to a small `VBox` (`bubble-mine` / `bubble-theirs`
    style classes) with a sender-name line for the other person's messages
    and a separate content label - closer to how a normal chat app renders
    a bubble, and it's what `chat-log-compact` targets for the Compact
    Messages setting.

**What this does NOT do:** dark mode and compact mode are per-session
(`MainChatView`) preferences, not saved anywhere - they reset to light/
comfortable on next login. `WelcomeView`/`LoginView`/`RegisterView` don't
carry the dark-mode class since they're separate scene roots created
before a `MainChatView` exists; persisting a theme choice across the whole
app would need a small settings store (e.g. a properties file or a new
`users` column), which felt like more than a "minor settings" pass called for.

---

## v1.6.0 — Unfriend

**Why:** the Friends tab could only ever grow — accepting a request added
someone permanently, with no way to undo it short of editing the database
by hand.

- **`net/Protocol.java`**
  - Added the `UNFRIEND` (client → server) message type constant.

- **`repository/FriendRepository.java`**
  - Added `findBetween(userId, otherUserId)` — looks up the single row
    linking two specific users regardless of which of them originally sent
    the request (`user_id`/`friend_id` can be in either order). Needed
    because the server only knows "me" and "the friend I clicked," not who
    happened to send the original `FRIEND_REQUEST`.
  - No change to `delete(id)` — it's the same inherited
    `AbstractRepository` method already used elsewhere; unfriending is just
    a new caller for it.

- **`server/ClientHandler.java`**
  - Added `handleUnfriend(json)` and an `UNFRIEND` case in the `handle()`
    switch. Resolves the target username, calls
    `FriendRepository.findBetween(...)`, rejects the request if the two
    users aren't an `ACCEPTED` friendship, then deletes that row.
  - Pushes a refreshed `FRIENDS_LIST` to both people immediately afterward
    (same live-update pattern `handleFriendResponse()` already uses on
    acceptance), so the removed friend disappears from both sidebars
    without either person needing to relog or hit Refresh.

- **`ui/MainChatView.java`**
  - `friendsListView`'s cell factory now renders each row as a
    `Label` + "Unfriend" `Button` (`HBox`) instead of plain text, so the
    Online/Offline styling still applies to the label while the button
    stays available in every row.
  - Added `confirmAndUnfriend(friendUsername)` — shows a resizable
    confirmation `Alert` before doing anything, since removing a friend is
    destructive; on confirmation it sends `UNFRIEND` to the server and, if
    the removed friend was the currently-open conversation, resets the
    chat pane back to its empty/no-selection state.
  - No change to `onServerEvent(...)`'s existing `FRIENDS_LIST` case — it
    already fully replaces `friendUsernames` from the server's payload, so
    the removed friend drops out of the list automatically once the
    server's push arrives.

---

## v1.5.0 — Persistent chat history

**Why:** messages were always being saved to `synchat.db` via
`MessageRepository.create()`, but nothing ever asked for them back —
opening a conversation, or logging back in after closing the app, always
started with a blank chat log even though the history was sitting right
there in SQLite the whole time.

- **`net/Protocol.java`**
  - Added `REQUEST_HISTORY` (client → server) and `HISTORY` (server →
    client) message type constants.

- **`server/ClientHandler.java`**
  - Added `handleHistoryRequest(json)` and a `REQUEST_HISTORY` case in the
    `handle()` switch. It calls the already-existing
    `MessageRepository.findConversation(userId, otherId)` (this method
    existed since v1.0.0 but was never actually wired to anything) and
    sends the full saved thread back to just the requester as a `HISTORY`
    payload: `{"type":"HISTORY","with":"bob","messages":[{"from":...,"content":...,"timestamp":...}, ...]}`.

- **`ui/MainChatView.java`**
  - `openConversation(username)` now sends a `REQUEST_HISTORY` message the
    moment a conversation is opened (from either the Online or Friends
    tab), in addition to clearing the chat log.
  - `onServerEvent(...)` gained a `HISTORY` case that repopulates the chat
    log with every saved message, in order, using the same `appendBubble(...)`
    used for live messages - and checks the payload's `with` field against
    the currently-open conversation before rendering, in case the reply
    arrives after the user has already switched to a different chat.

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
