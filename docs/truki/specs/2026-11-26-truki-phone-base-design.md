# Truki on the phone, sub-project 1: phone base — design

Date: 2026-11-26
Status: approved in conversation, awaiting written-spec review
Repo: `truki-wear` (Signal-Android fork, upstream `v8.32.1`). Read `AGENTS.md`
first: the same re-merge rules apply to the phone flavor as to the watch.

## Goal

Truki becomes the user's **primary** Signal app on their Samsung phone,
replacing official Signal, with chat history carried over. This sub-project
builds the base every later phone feature stands on: a phone build of the fork,
its signing and self-updates, and the account move.

## The phone programme

The user asked for five things on the phone. Each one is its own sub-project
with its own spec, plan and build, in this order:

1. **Phone base**: this document.
2. **Battery-friendly push**: UnifiedPush through a MollySocket relay on the
   home PC, instead of an always-on connection.
3. **Rich agent cards**: Markdown, option buttons and notification answer
   buttons. This is build-order step 3 of
   `tehisintellekti-kodu/docs/superpowers/specs/2026-11-16-rich-agent-messages-design.md`.
4. **Spaces**: Truki's chat folders, as on desktop and the watch.
5. **Bridged chats**: WhatsApp, Messenger and Instagram, as on desktop.

**Principle for all five: the PC can make the phone better, never
necessary.** The phone talks to Signal's servers itself. If the home PC is
down, the phone keeps sending and receiving:
- In sub-project 2 the relay only wakes the phone. Truki must notice missing
  wake-ups and fall back to its own connection automatically.
- In sub-project 5, whatever runs on the PC must degrade to "bridged chats
  unavailable", never to "Signal unavailable".
- What legitimately stops without the PC: the agents themselves (hub,
  orchestrator), and so agent messages and cards.

## Constraints

- **Re-merge cost** (`AGENTS.md`):
  - phone code goes in new files under `app/src/phone/`, Kotlin package
    `org.thoughtcrime.securesms.truki.phone`;
  - upstream edits are additive and marked `// Truki:`;
  - no upstream phone UI changes in this sub-project.
- **No new dependencies.**
- **The account must never be at risk without a way back.** Official Signal
  stays installed, inactive, for at least two weeks after the move.

## 1. Identity, signing, updates

### Identity
- **Flavor:** a `phone` value of the `distribution` dimension in
  `app/build.gradle.kts`, in a `// Truki:` block next to `wear`.
  - App ID `ee.truki.phone`.
  - `phoneProdDebug` and `phoneProdRelease` are appended to
    `selectableVariants`. Upstream disables every variant not on that list.
- **Name and icon:** the app name is `Truki`, ASCII only. Truki Desktop learned
  that a non-ASCII name breaks Electron's privileged protocols; Android has no
  such problem, but the name stays consistent. The icon is its own, so the two
  apps are distinguishable while both are installed.
- **Google push:** Signal's FCM is bound to Signal's app ID and cannot work for
  `ee.truki.phone`. The phone flavor does not initialise Firebase. It turns on
  upstream's existing `forceWebsocketMode` ("stay connected") by default, as
  the watch does, until sub-project 2 replaces it.

### Releases and updates
- **Repo:** a new **public** GitHub repo `August-Roosi/truki-android` holds the
  code and the releases. The fork is AGPL. Also, the updater must download
  without authentication, the same reason truki-desktop's releases are public.
  The local `truki-wear` clone gets it as `origin`.
- **Updater:** upstream's `website` updater. Build config:
  - `MANAGES_APP_UPDATES = true`
  - `APK_UPDATE_MANIFEST_URL = "https://github.com/August-Roosi/truki-android/releases/latest/download/latest.json"`
  - `BUILD_DISTRIBUTION_TYPE = "phone"`

  The manifest uses upstream's format. Android installs an update only when it
  is signed with the same key as the installed app.
- **Versions:** `versionName` is upstream's version plus `-trukiN`, e.g.
  `8.32.1-truki1`. `versionCode` must increase across every Truki release,
  including two Truki releases of the same upstream version; the plan derives
  it from upstream's code plus N.

### Signing
- **Key:** a new release key at `~/.config/truki/truki-android-release.jks`.
  Its passwords live in the hub's `.env` (`TRUKI_ANDROID_KEYSTORE_PASSWORD`,
  `TRUKI_ANDROID_KEY_PASSWORD`). Neither is ever committed.
- **Backup:** the hub's `scripts/kalender_backup.py` key check (today:
  kalender's `release.jks`) is extended to keep and verify a copy at
  `~/varukoopiad/truki-android-signing/truki-android-release.jks`. Losing the
  key means no update can ever install, so the user would have to reinstall
  and move the account again.

### Release script
`scripts/truki-android-release` (Truki-owned):
1. builds `phoneProdRelease` for `arm64-v8a`;
2. signs it with the key;
3. writes `latest.json` (version, sha256, download URL);
4. creates the GitHub release with the APK and `latest.json`.

## 2. Moving the account (Samsung)

### Before anything changes
1. **Dry run.** On an emulator, confirm that the phone build's registration
   offers "restore from on-device backup" and accepts a backup file up to the
   passphrase step, without registering. If upstream v8.32.1 offers only
   another restore path, stop and re-plan this section.
2. **Install Truki unregistered.** Install `8.32.1-truki1` next to official
   Signal, but don't register it yet. The user has their Signal PIN at hand,
   because re-registration asks for it if Registration Lock is on.
3. **Update test.** Publish `8.32.1-truki2`, which differs only in version.
   Truki must find, download and install it by itself. This proves the update
   path before anything depends on it.
4. **Record what exists.** Screenshot official Signal's linked devices (Truki
   Desktop on anteos, the watch, the hub's read-only account A). Confirm the key
   backup copy and `.env` entries exist.

### The move (user at the phone, about 15 minutes)
5. **Back up.** In official Signal: Settings → Backups → On-device backup. Note
   the passphrase, then back up now.
6. **Restore.** In Truki: "Transfer or restore account" → on-device backup →
   choose the file → passphrase → number → SMS code → PIN.
7. **Old app inactive.** Official Signal on the Samsung now reports the number
   is registered elsewhere. It stays installed and inactive for at least two
   weeks.
8. **Stay connected.** Samsung settings: put Truki under "Never sleeping apps"
   and turn off battery optimisation for it.

### Afterwards
9. **Linked devices.** Whether they survive re-registration is checked, not
   assumed. Any that dropped are re-linked with the Truki phone as the scanner:
   - Truki Desktop on anteos;
   - the watch ("Link this device");
   - the hub's account A (`signal-cli link`, per the `sending-signal-messages`
     skill). That skill allows re-linking only when the user asks; approving
     this spec is that request.

   The agents' account B is a separate number and is unaffected.
10. **Checklist:**
    - 1:1 and group messages both ways, an attachment each way, one call;
    - a message sent after 30 minutes with the screen off arrives as a
      notification within a minute;
    - an agent message arrives;
    - an agent card can be answered by typing its number.
11. **Two-week window.** A hub todo task with a deadline two weeks out:
    "uninstall official Signal, or roll back".

### Going back (within the two weeks)
- Re-register official Signal, restoring its last backup, or first a backup
  made from Truki so later messages come along. Then remove Truki.
- Linked devices may need re-linking again (step 9).

## 3. Testing and done

- **Build:** `phoneProdRelease` (arm64) builds and is signed. `wearProdDebug`
  still builds. Cold builds take over an hour and run in the background.
- **Unit tests:** upstream's unit tests for the phone variant fail no more than
  a baseline measured before the change. JVM tests for Truki code:
  - the flavor's settings: app ID, update URL, distribution type, Firebase
    off, `forceWebsocketMode` default on;
  - the release script's `latest.json` generation.
- **Update test** (§2 step 3) passes.
- **Done when** the move (§2 steps 5–10) is complete, its checklist passes and
  the update test has passed. The two-week window is tracked by the todo task,
  not by this sub-project.

## Out of scope

Push via relay (2), rich cards (3), Spaces (4), bridged chats (5), any change
to the upstream phone UI, iOS, and a Play Store listing.
