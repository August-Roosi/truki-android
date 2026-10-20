# AGENTS.md — working in Truki Wear

Truki Wear is a fork of Signal-Android (base: upstream `v8.32.1`) that runs
Signal on a Wear OS watch (Galaxy Watch Ultra2, LTE) **without the phone**.
The watch is a Signal *linked device*, like Truki Desktop. All encryption runs
on the watch.

Remotes: `upstream` = signalapp/Signal-Android. The clone is shallow; run
`git fetch --unshallow upstream` before the first upstream merge.

## The prime directive

Same as Truki Desktop: **upstream ships often, and this fork must re-merge
cheaply.** Every line changed in an upstream file can conflict on every merge.

1. New behaviour goes in **new files** under `app/src/wear/` (the `wear`
   flavor's source set). Kotlin package `org.thoughtcrime.securesms.truki.wear`.
2. Edits to upstream files are additive and marked `// Truki:`. Today:
   `app/build.gradle.kts` (the `wear` flavor block, `wearProdDebug`/
   `wearProdRelease` appended to `selectableVariants` because upstream disables
   every variant not on that list, and the `wearImplementation` lines), plus
   generated checksum entries in `gradle/verification-metadata.xml`.
3. Never reformat, reorder or tidy upstream code.
4. Truki-owned files carry a `truki` path segment or `Truki` in the name, and the
   header `Copyright 2026 Truki` / `SPDX-License-Identifier: AGPL-3.0-only`.
5. **No new dependencies without need.** Signal verifies every dependency's
   checksum (`gradle/verification-metadata.xml`). Truki Wear adds exactly the
   Wear libraries (`androidx.wear.compose` material3/foundation/navigation and
   `androidx.wear:wear-input`), declared as `wearImplementation` in
   `app/build.gradle.kts`. Their checksums were added with
   `./gradlew --write-verification-metadata sha256 :Signal-Android:assembleWearProdDebug`;
   on an upstream merge, take upstream's metadata file and re-run that command.

## How it fits together

- `wear` is a value of the `distribution` flavor dimension, app ID
  `ee.truki.wear`. Build: `./gradlew :Signal-Android:assembleWearProdDebug`
  (the `app/` directory's Gradle project is named `:Signal-Android`).
- `app/src/wear/AndroidManifest.xml` disables upstream's phone launcher alias
  (`.RoutingActivity`) and launches `TrukiWearActivity` instead.
- **Linking:** `TrukiWearActivity` extends `PassphraseRequiredActivity`, which
  routes an unlinked install into upstream's registration flow. Its welcome
  screen has "Link this device"; the phone scans the QR. No Truki code.
- **Receiving:** a fork can't receive Signal's FCM pushes (they are bound to
  Signal's own app ID), so the watch turns on upstream's existing
  `forceWebsocketMode` ("stay connected") and keeps a websocket open. This
  costs battery; see the roadmap.
- **Data:** `TrukiWearRepository` is the only place the watch UI touches Signal
  internals (`SignalDatabase`, `MessageSender`, `DatabaseObserver`).
- **Notifications:** upstream's, which already have Wear inline reply
  (`RemoteReplyReceiver`).
- **Redirect:** `TrukiWearInitProvider` (a data-less ContentProvider, so it runs
  at process start without touching `ApplicationContext`) registers
  `TrukiWearRedirect`, which turns upstream's `MainActivity` and
  `ConversationActivity` into the watch UI.

## Roadmap

1. MVP (done in code): link, chat list, thread, quick replies, keyboard/voice reply.
2. (done in code) Notification taps and the post-link landing open the watch UI.
3. Audio calls via upstream's calling stack (RingRTC) with a watch call screen.
4. Battery: replace the always-on websocket with push (UnifiedPush/MollySocket
   or a hub relay). Option later: move encryption to the hub.
5. A watch-shaped link screen.

## Watch UI

Wear Compose Material 3: `AppScaffold` + `SwipeDismissableNavHost` (swipe
right to go back), `ScreenScaffold` + `TransformingLazyColumn` (the curved,
scaling list; rotary scrolling built in). Routes: `chats` (current chat folder,
pinned first, folder button at top), `folders` (pick a chat folder; choice kept
in the `truki_wear` SharedPreferences), `thread/{threadId}` (messages, call
button, `EdgeButton` opening the system reply screen via `RemoteInput`, with
quick replies as its suggestions). Avatars use upstream's `AvatarImage`.
Colours follow the watch's dynamic theme when it has one.

## Testing on the watch

The watch (SM-L715F, 32-bit ARM only: install the `armeabi-v7a` APK) is paired
over wireless debugging with the Windows PC `anteos` (SSH `roosi@100.67.244.52`
over Tailscale), whose Android SDK has adb. Helper scripts in the PC's home
folder: `install.ps1 [-Clear]` (push, install, launch, crash log), `shot.ps1
-Name x` (screenshot to `x.png`), `state.ps1` (link/websocket log). Both must
be on the same Wi-Fi; a locked watch shows the watch face in screenshots.

## Build environment (roosi)

- JDK 21 at `~/.local/jdk/jdk-21`, SDK at `~/Android/Sdk` (`local.properties`).
- `~/.gradle/gradle.properties` sets an 8 GB heap with Kotlin compiled
  in-process (upstream asks for 12 GB; the machine has 15 GB shared). A 3 GB
  Kotlin daemon ran out of memory on the app module. Run `./gradlew --stop`
  first if old daemons are holding memory. A cold build takes over an hour;
  run it in the background.

Never claim a build or test passed without running it and quoting its result.
