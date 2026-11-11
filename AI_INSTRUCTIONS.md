# Extragtam (exteraGram + AyuGram) — Android Development Guide for AI Agent

## 0. PROJECT FACTS (verified)vscode-remote://codespaces%2Bcautious-goggles-q7wqxxpvw6g5c695/workspaces/TgMod/AI_INSTRUCTIONS.md#1%2C1-145%2C1
- Base: Telegram Android, forked as **exteraGram**, with **AyuGram** core merged in.
- Do NOT assume vanilla Telegram layout; the repo has two mod layers:
  - `com.exteragram.messenger.*` — exteraGram features/config (`ExteraConfig`).
  - `com.radolyn.ayugram.*` — AyuGram features/config/database (`AyuConfig`, `AyuMessagesController`).
- Build config is in `TMessagesProj/build.gradle`:
  - `compileSdkVersion 33`, `targetSdkVersion 33`, `minSdkVersion 21`
  - Java `sourceCompatibility/targetCompatibility = VERSION_11`
  - namespace `org.telegram.messenger`
- `org.telegram.ui.Extragtam/` does NOT exist yet. Only create it when you need new isolated UI; existing mods live in extera/ayu packages.

## 1. CORE RULES & TOKEN SAVING
- DO NOT scan/read the whole repository. Files here are up to ~32k lines.
- Work only with the specific files for the task (map below).
- Modify surgically. Do not rewrite `ChatMessageCell`, `ChatActivity`, `Theme`, `PhotoViewer` wholesale.
- Never guess Telegram API names, theme keys, row ids, or resource ids — grep the file first.
- No comments in code unless explicitly requested.

---

## 2. CORRECT ARCHITECTURE MAP

### A. Chat messages / bubbles
`TMessagesProj/src/main/java/org/telegram/ui/Cells/ChatMessageCell.java`
- One View = one message. Extends `BaseCell`.
- Pipeline: `setMessageObject()` → `onMeasure()` → `onLayout()` → `onDraw()`.
- `onDraw()` → `drawBackground` / `drawContent` / `drawNamesLayout` / `drawTime` / `drawOutboundsContent`.
- Bubble background is NOT drawn here: `drawBackground(...)` only picks a `Theme.MessageDrawable` by key (`key_drawable_msgIn/Out...`), calls `setTop/setBounds/drawCached`.
- Events go through `ChatMessageCellDelegate`.
- Ayu hooks: `ayuDeleted` field set in `setMessageObject`, used in `measureTime` and time animation (deleted/edited marks).
- To add: new state field + reset in `setMessageObject`; sizing in `setMessageObject`/`onMeasure`; drawing in `onDraw`/`drawContent`; touch in `onTouchEvent` + a `checkXxxMotionEvent`.
- To remove: delete field, reset, draw, measure and touch branches together, or layout will desync.

### B. Bubbles / theming / OLED
`TMessagesProj/src/main/java/org/telegram/ui/ActionBar/Theme.java`
- Bubble renderer = nested `Theme.MessageDrawable` (path, paints, gradient, shadow).
- `setTop(...)` chooses colors (`key_chat_in/outBubble`, gradient keys).
- `getBackgroundDrawable()` / `draw(...)` build the rounded path.
- Corner radius comes from `SharedConfig.bubbleRadius`; override via `setRoundRadius(int)` / `setRoundingRadius(float)`.
- Types: `TYPE_TEXT`, `TYPE_MEDIA`, `TYPE_PREVIEW`. `ResourcesProvider` supplies per-screen colors.
- Theme data classes: `ThemeInfo`, `ThemeAccent`.
- Rule: change theme keys/values, never hardcode colors/radii inside cells.

### C. Main activity / navigation / drawer
`TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java`
- Activity, holds 3 fragment stacks: main / layers / right, via `INavigationLayout`.
- `onCreate` builds `frameLayout` → `drawerLayoutContainer` → `actionBarLayout`; `setupActionBarLayout()` handles phone/tablet.
- Drawer menu is `org/telegram/ui/Adapters/DrawerLayoutAdapter.java`; clicks are handled in `LaunchActivity` by item id (Ayu ids in `AyuConstants`: `DRAWER_TOGGLE_GHOST`, `DRAWER_KILL_APP`).
- `handleIntent(...)` routes deep links/intents.
- To add a drawer item: `DrawerLayoutAdapter` + id in `AyuConstants`/menu constants + click branch in `LaunchActivity`.

### D. Chats list
`TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java`
- `BaseFragment`. `createActionBar()` + `createView()`.
- `createView` builds action bar menu, search, `ViewPage` per folder, `DialogsRecyclerView`, filters, floating button.
- Click/long-click/action mode are in the same file. Folder tabs: `updateFilterTabs`.
- To add a header action: `menu.addItem(...)` in `createView` + branch in the action bar click listener.

### E. Chat screen
`TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java`
- `BaseFragment`, largest file (~32k lines).
- `createView()` builds: cell cache, action bar menu (`actionBar.createMenu()`), search, call, overflow `headerItem`, list/adapter, enter view, top panel (`createTopPanel`), action mode (`createActionMode`).
- Message context menu: `createMenu(...)`; items are kept in THREE parallel lists `items` / `options` / `icons` (Ayu appends its options there).
- Ayu message-loading hook is inside the message load path (`AyuHistoryHook`).
- To add a chat menu action: `menu.addItem(id)` + handle in the action bar click listener.
- To add a context-menu action: append to all three lists + handle its `OPTION_*` id.
- Any adapter change must be followed by `notify*`.

### F. Profile / settings screen
`TMessagesProj/src/main/java/org/telegram/ui/ProfileActivity.java`
- `BaseFragment`. `createActionBar()`, `createView()`, `updateRowsIds()`, `ListAdapter`, `createActionBarMenu()`.
- `updateRowsIds()` is the single source of truth for list rows: reset all `xxxRow = -1`, then assign via `newRow()`.
- `ListAdapter` maps row ids to view types/holders.
- Ayu settings entry opens `AyuGramPreferencesActivity`; extera entry opens `MainPreferencesActivity`.
- To add a row: field → reset in `updateRowsIds` → `newRow()` → adapter branches → click handler.
- To add an overflow menu item: `otherItem.addSubItem(...)` + id handler.

### G. Music player
`TMessagesProj/src/main/java/org/telegram/ui/Components/AudioPlayerAlert.java`
- `BottomSheet`. Constructor registers many `NotificationCenter` observers and builds container/player/list/blur/action bar.
- Update paths: `updateLayout`, `updateTitle`, `updateCover`, `updatePlaybackButton`, `didReceivedNotification`, `ListAdapter`.
- To add UI: constructor/`updateLayout`. To add menu: action bar menu + `onSubItemClick`.

### H. Media viewer
`TMessagesProj/src/main/java/org/telegram/ui/PhotoViewer.java`
- Singleton overlay. `setParentActivity(...)` creates window/container/action bar.
- `openPhoto(...)` validates provider, attaches `windowView` to `WindowManager`, sets provider and observers.
- `closePhoto(...)`, `destroyPhotoViewer(...)` clean up. `isShowingImage(...)` is used by cells.
- To add an overlay element: create in `setParentActivity`, update in `openPhoto`, clean in `closePhoto`/`destroyPhotoViewer`.
- Must not leak `WindowManager` views or observers.

### I. AyuGram core (CORRECT PATHS)
- `TMessagesProj/src/main/java/com/radolyn/ayugram/messages/AyuMessagesController.java`
  - Saves deleted/edited messages. Singleton + DAOs from `AyuData`.
  - `onMessageEdited`/`onMessageEditedForce`, `onMessageDeleted`, revisions getters, `delete`, `clean`.
  - Notifies via `AyuConstants.MESSAGE_EDITED_NOTIFICATION` / `MESSAGES_DELETED_NOTIFICATION`.
- `TMessagesProj/src/main/java/com/radolyn/ayugram/utils/AyuGhostUtils.java`
  - Manual read helpers: `markReadOnServer` (raw `TL_messages_readHistory`/`TL_channels_readHistory`), `markReadLocally`, dialog/request id helpers.
  - Ghost mode itself is flags in `AyuConfig` + `AyuState` checks in network hooks.
- Supporting Ayu files:
  - `com/radolyn/ayugram/AyuConfig.java` — flags + `SharedPreferences "ayuconfig"`.
  - `com/radolyn/ayugram/AyuConstants.java` — menu ids, notification ids, app constants.
  - `com/radolyn/ayugram/proprietary/AyuMessageUtils.java`, `AyuHistoryHook.java` — mapping/history hooks.
  - `com/radolyn/ayugram/messages/AyuSavePreferences.java`, `utils/AyuState.java` — state for save/ghost.
  - `com/radolyn/ayugram/database/AyuData.java` + `database/dao/*` + `database/entities/*` — Room/SQLite.
  - `com/radolyn/ayugram/ui/AyuMessageHistory.java`, `AyuMessageCell.java` — deleted/edited history UI.
  - `com/radolyn/ayugram/ui/preferences/*` — Ayu settings screens.

### J. exteraGram config/settings
- `TMessagesProj/src/main/java/com/exteragram/messenger/ExteraConfig.java` — flags + prefs.
- `com/exteragram/messenger/preferences/MainPreferencesActivity.java` (+ Appearance/Chats/General/Other, `BasePreferencesActivity`).
- Do not mix extera and Ayu configs; add a flag to the layer that owns the feature.

---

## 3. WHERE TO PUT NEW CODE
- New isolated UI/feature: `TMessagesProj/src/main/java/org/telegram/ui/Extragtam/` (create on demand).
- Ayu-specific logic: `com/radolyn/ayugram/...` next to the matching subpackage.
- extera-specific logic: `com/exteragram/messenger/...`.
- Keep new code out of core files where possible; core files should only get small hook points.

## 4. RECIPE: ADD A FEATURE
1. Add a flag: `AyuConfig` or `ExteraConfig` (`public static` + `getBoolean` in `loadConfig` + `editor.put...apply()`).
2. Add data layer if needed: entity + DAO in `com/radolyn/ayugram/database` + method in `AyuMessagesController`.
3. Hook the event: network/message hook (`AyuHistoryHook`, `AyuMessageUtils`) or UI lifecycle.
4. Add UI at the correct point per the map above.
5. Add settings entry: `AyuGramPreferencesActivity` (`updateRowsId` → `onItemClick` → `ListAdapter`) or extera preferences.
6. Add strings via `LocaleController.getString` + `R.string.*`; icons via `R.drawable.*`.
7. Post a `NotificationCenter` notification if other screens must refresh.

## 5. RECIPE: REMOVE A FEATURE
- Remove config flag + its settings row/branch.
- Remove all UI branches (draw, measure, touch, menu add + handling) in the same commit.
- Remove hooks and notifications; remove data methods only if no other caller.
- Check for parallel-list desync in `ChatActivity.createMenu` and row-id desync in `ProfileActivity.updateRowsIds`.

## 6. TROUBLESHOOTING
- Do not hallucinate Telegram API/theme keys/row ids. Grep an existing usage in this repo first.
- `ChatMessageCell` height changes must happen in `setMessageObject`; `onMeasure` only applies the precomputed height.
- Bubble radius: `SharedConfig.bubbleRadius` or `Theme.MessageDrawable.setRoundRadius/setRoundingRadius`, not constants in `ChatMessageCell`.
- Keep Java 11 compatibility and SDK 33 as configured.
- Preserve credit/copyright headers when editing Ayu/extera files.
