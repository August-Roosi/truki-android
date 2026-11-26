# Truki Phone Base Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A `phone` build of the Truki Android fork (`ee.truki.phone`) that self-updates from GitHub releases, stays connected without Google push, and becomes the user's primary Signal on their Samsung, with history restored from an on-device backup.

**Architecture:**
- A new `phone` value of the `distribution` flavor dimension, beside `wear`, built on upstream's `website` updater configuration.
- Truki code lives in `app/src/phone/`. A data-less init provider registers a lifecycle hook that turns on upstream's "stay connected" once, after app init.
- Releases are built and signed locally by a Truki script that writes upstream's update manifest and publishes a GitHub release.
- The account move is a guided manual procedure with checks before and after.

**Tech Stack:** Kotlin, Gradle (Kotlin DSL), Android SDK, JUnit 4, bash + Python 3 (release script), `gh` CLI, adb.

**Spec:** `docs/truki/specs/2026-11-26-truki-phone-base-design.md`. Repo rules: `AGENTS.md`.

## Global Constraints

- **Upstream edits:** additive only, each marked `// Truki:`. The only upstream file edited is `app/build.gradle.kts`:
  - the `phone` flavor block;
  - two `selectableVariants` entries;
  - `phoneImplementation` lines, only if needed (none expected).
- **No new dependencies.** `gradle/verification-metadata.xml` is unchanged.
- **Truki files:** a `truki` path segment or `Truki` in the name; header `Copyright 2026 Truki` / `SPDX-License-Identifier: AGPL-3.0-only`. Kotlin package `org.thoughtcrime.securesms.truki.phone`.
- **Identity:**
  - app ID `ee.truki.phone`, label `Truki`;
  - `BUILD_DISTRIBUTION_TYPE "phone"`, `MANAGES_APP_UPDATES true`;
  - `APK_UPDATE_MANIFEST_URL "https://github.com/August-Roosi/truki-android/releases/latest/download/latest.json"`;
  - `LINK_DEVICE_UX_ENABLED true`.
- **Versions:**
  - `versionName = "$canonicalVersionName-truki$trukiPhoneRevision"`;
  - `versionCode = canonicalVersionCode * maxHotfixVersions + trukiPhoneRevision`;
  - `trukiPhoneRevision` starts at 1, must stay in 1–99, and is reset to 1 when upstream's `canonicalVersionCode` changes.
- **Signing:**
  - key `~/.config/truki/truki-android-release.jks`, alias `truki`;
  - passwords in the hub `.env` as `TRUKI_ANDROID_KEYSTORE_PASSWORD` / `TRUKI_ANDROID_KEY_PASSWORD`;
  - backup copy `~/varukoopiad/truki-android-signing/truki-android-release.jks`;
  - none of these are ever committed.
- **Builds:** cold builds take over an hour. Run Gradle in the background with `./gradlew --stop` first. Quote the literal `BUILD SUCCESSFUL` / `BUILD FAILED` line and test totals.
- **The account:** nothing touches the real account before Task 6. Task 7 is done with the user present, step by step, and stops at the first failed check.

## Review Focus

1. **"Stay connected" default runs before SignalStore exists.** The init provider runs before `ApplicationContext.onCreate`, so the hook must act only once an activity is created (after app init) and only once per install. Pinned in Task 2, `applies the websocket default once`.
2. **The update manifest must point at the arm64 APK the user installed.** A universal or wrong-ABI URL makes Android reject the update. Pinned in Task 4, `manifest points at the arm64 asset`.
3. **Version code ordering across upstream bumps.** `truki2` < `truki3` < the next upstream canonical with `truki1`. Pinned in Task 1, `phone version codes increase across revisions and upstream bumps`.
4. **A lost or wrong key.** A release signed with a different key never installs over the old one. The release script refuses to run without the key and its backup copy matching. Pinned in Task 4, `refuses to release when the key backup differs`.
5. **Registration not offering a local-backup restore.** If Task 6's dry run finds no such path, stop and re-plan; do not improvise with the real account.

---

### Task 1: Phone flavor, identity, version codes

**Files:**
- Modify: `app/build.gradle.kts`
  - after the `canonicalVersionName` / `maxHotfixVersions` vals: `// Truki:` `val trukiPhoneRevision = 1`
  - `selectableVariants`: `phoneProdDebug`, `phoneProdRelease`
  - a `create("phone")` block after `create("wear")`
- Create: `app/src/phone/AndroidManifest.xml` (label override), `app/src/phone/res/values/truki_phone.xml` (`truki_phone_app_name` = `Truki`), `app/src/phone/res/mipmap-anydpi-v26/` plus foreground/background drawables for a Truki-coloured launcher icon (overriding upstream's `ic_launcher` names in the phone source set)
- Create: `app/src/phone/java/org/thoughtcrime/securesms/truki/phone/TrukiPhoneVersion.kt`, holding `fun trukiPhoneVersionCode(canonical: Int, maxHotfix: Int, revision: Int): Int`. It requires `revision in 1..(maxHotfix - 1)`. Pure, so it's testable. The Gradle block keeps the same formula inline, with a comment pointing at this file.
- Test: `app/src/testPhone/java/org/thoughtcrime/securesms/truki/phone/TrukiPhoneBuildConfigTest.kt`

**Interfaces:**
- Produces: the `phone` flavor and `BuildConfig` values (Global Constraints); `trukiPhoneVersionCode(...)`.

- [ ] **Step 1: Write the failing tests:**
  - `build config identifies the phone flavor`:
    - `BuildConfig.APPLICATION_ID == "ee.truki.phone"`
    - `BUILD_DISTRIBUTION_TYPE == "phone"`
    - `MANAGES_APP_UPDATES`
    - the manifest URL equals the constant
    - `LINK_DEVICE_UX_ENABLED`
  - `phone version codes increase across revisions and upstream bumps`:
    - `trukiPhoneVersionCode(1768, 100, 2) > trukiPhoneVersionCode(1768, 100, 1)`
    - `trukiPhoneVersionCode(1769, 100, 1) > trukiPhoneVersionCode(1768, 100, 99)`
    - `trukiPhoneVersionCode(1768, 100, 0)` and `(1768, 100, 100)` throw
  - `versionName carries the truki revision`: `BuildConfig.VERSION_NAME.endsWith("-truki1")`.
- [ ] **Step 2: Run and verify it fails.** `./gradlew :Signal-Android:testPhoneProdDebugUnitTest --tests '*TrukiPhoneBuildConfigTest*'`. Expected: it fails to configure, with no such variant/task.
- [ ] **Step 3: Implement** the flavor block, resources, manifest and `TrukiPhoneVersion.kt`.
- [ ] **Step 4: Run and verify it passes.** The same command, in the background (cold build). Expected: `BUILD SUCCESSFUL`, 3 tests passed.
- [ ] **Step 5: Baseline the full phone unit suite.** `./gradlew :Signal-Android:testPhoneProdDebugUnitTest`. Record the literal totals and any failing test names in the ledger as the baseline.
- [ ] **Step 6: Watch still builds.** `./gradlew :Signal-Android:assembleWearProdDebug`. Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 7: Commit.** `truki-phone: phone flavor, identity and version codes`

### Task 2: Stay connected by default

**Files:**
- Create: `app/src/phone/java/org/thoughtcrime/securesms/truki/phone/TrukiPhoneInitProvider.kt`, a data-less `ContentProvider` like the watch's `TrukiWearInitProvider`. `onCreate` registers `TrukiPhoneLifecycle` on the `Application`.
- Create: `app/src/phone/java/org/thoughtcrime/securesms/truki/phone/TrukiPhoneLifecycle.kt` (`Application.ActivityLifecycleCallbacks`). On the first `onActivityCreated`, it calls `TrukiPhoneWebsocketDefault.apply(context)` once.
- Create: `app/src/phone/java/org/thoughtcrime/securesms/truki/phone/TrukiPhoneWebsocketDefault.kt`:
  - `fun decide(alreadyApplied: Boolean, enabled: Boolean): Boolean` (pure: true when it should enable now);
  - `fun apply(context: Context)`:
    - reads and writes the flag `websocket_default_applied` in SharedPreferences `truki_phone`;
    - when `decide(...)` is true: `SignalStore.settings.forceWebsocketMode = ForceWebsocketMode.ENABLED_BY_USER`, `AppDependencies.resetNetwork()`, `AppDependencies.startNetwork()`;
    - always sets the flag, so a user who later turns "stay connected" off is respected.
- Modify: `app/src/phone/AndroidManifest.xml` (register the provider, authority `${applicationId}.trukiphoneinit`).
- Test: `app/src/testPhone/java/org/thoughtcrime/securesms/truki/phone/TrukiPhoneWebsocketDefaultTest.kt`

**Interfaces:**
- Consumes: upstream `SignalStore.settings.forceWebsocketMode`, `AppDependencies` (as in `TrukiWearRepository.ensureWebsocketMode`).

- [ ] **Step 1: Write the failing tests:**
  - `applies the websocket default once`:
    - `decide(alreadyApplied = false, enabled = false) == true`
    - `decide(true, false) == false` (the user turned it off later)
    - `decide(false, true) == false` (already on)
- [ ] **Step 2: Run and verify it fails.** `--tests '*TrukiPhoneWebsocketDefaultTest*'`. Expected: compilation fails, unresolved reference.
- [ ] **Step 3: Implement** the three files and the manifest entry.
- [ ] **Step 4: Run and verify it passes.** Expected: `BUILD SUCCESSFUL`, 1 test passed. Then the full phone suite: no failures beyond the Task 1 baseline.
- [ ] **Step 5: Commit.** `truki-phone: stay connected by default (no Google push for a fork)`

### Task 3: Release key and its backup (hub + local)

**Files:**
- Create (not committed): `~/.config/truki/truki-android-release.jks` via `keytool -genkeypair -alias truki -keyalg RSA -keysize 4096 -validity 36500`. The passwords are generated (`openssl rand -base64 24`) and appended to `~/Dokumendid/tehisintellekti-kodu/.env`.
- Modify (hub repo `~/Dokumendid/tehisintellekti-kodu`): `scripts/kalender_backup.py`. Turn the single `SIGNING_KEY`/`SIGNING_COPY` pair into a list `SIGNING_KEYS = [(kalender key, copy), (truki key, copy)]` checked by the existing `check_signing_key`. Missing copy → copy it, then verify. Differing copy → report, as for kalender.
- Test (hub): extend `scripts/test_kalender_backup.py`.

- [ ] **Step 1: Write the failing hub test.** `test_checks_every_signing_key`: with two temp key/copy pairs where the second copy differs, the check reports the second key's problem, and the first passes.
- [ ] **Step 2: Run and verify it fails.** `python3 scripts/test_kalender_backup.py`. Expected: a FAIL on the new test.
- [ ] **Step 3: Implement** the list form in `kalender_backup.py`.
- [ ] **Step 4: Run and verify it passes.** Expected: `OK`.
- [ ] **Step 5: Create the key and passwords, run the backup check.** Then `ls -la` the copy. Run `cmp` on key and copy → identical. `git -C ~/Dokumendid/tehisintellekti-kodu status --short` shows no `.env`.
- [ ] **Step 6: Commit (hub).** `kalender backup: also guard the Truki Android signing key`, then push per the hub git-workflow skill.

### Task 4: Release script and update manifest

**Files:**
- Create: `scripts/truki_android_manifest.py`:
  - `def build_manifest(version_code: int, version_name: str, apk_path: Path, repo: str, tag: str) -> dict`. It returns `{"versionCode", "versionName", "url": f"https://github.com/{repo}/releases/download/{tag}/{apk_path.name}", "sha256sum": <hex sha256 of the file>}`.
  - `def check_key_backup(key: Path, copy: Path) -> None`. Raises `SystemExit` with a message if the copy is missing or differs.
- Create: `scripts/truki-android-release` (bash). It:
  1. sources the hub `.env` for the passwords;
  2. runs `check_key_backup`;
  3. runs `./gradlew :Signal-Android:assemblePhoneProdRelease` with signing injected through upstream's existing signing properties, or `-Pandroid.injected.signing.*`. Read how upstream signs releases first, and use that;
  4. picks the `arm64-v8a` APK;
  5. writes `latest.json`;
  6. runs `gh release create v<versionName> <apk> latest.json --repo August-Roosi/truki-android --title "Truki <versionName>"`.
- Test: `scripts/truki_android_manifest_test.py` (`unittest`)

- [ ] **Step 1: Write the failing tests:**
  - `manifest points at the arm64 asset`: given a temp file `Truki-phone-arm64-v8a-8.32.1-truki1.apk`, the URL ends with that name and contains `/releases/download/v8.32.1-truki1/`, and `sha256sum` equals `hashlib.sha256` of its bytes.
  - `refuses to release when the key backup differs`: `check_key_backup` raises on a missing copy and on a different copy, and returns on an identical one.
- [ ] **Step 2: Run and verify it fails.** `python3 scripts/truki_android_manifest_test.py`. Expected: `ModuleNotFoundError`.
- [ ] **Step 3: Implement** the module and the script. The script also prints the APK's signing certificate SHA-256 (`apksigner verify --print-certs`) so a key mix-up is visible.
- [ ] **Step 4: Run and verify it passes.** Expected: `OK`.
- [ ] **Step 5: Commit.** `truki-phone: release script and update manifest`

### Task 5: GitHub repo, first release, install, update test

The spec approves this outward-facing step. A public repo is required by the updater.

- [ ] **Step 1: Create and push the repo.** `gh repo create August-Roosi/truki-android --public --description "Truki for Android: Signal fork (phone + Wear OS)"`, then `git remote add origin …` and `git push -u origin main`. The repo's README states it is a Signal fork and links upstream.
- [ ] **Step 2: First release.** `scripts/truki-android-release` with `trukiPhoneRevision = 1`. Expected: `BUILD SUCCESSFUL`, then a release `v8.32.1-truki1` with the arm64 APK and `latest.json`. Fetch `https://github.com/August-Roosi/truki-android/releases/latest/download/latest.json` with `curl -sL` and check the JSON matches.
- [ ] **Step 3: Install on the Samsung, unregistered.** The user opens the release's APK link on the phone and installs it, allowing installs from the browser. Truki shows the registration welcome screen. **Do not register.**
- [ ] **Step 4: Update test.** Set `trukiPhoneRevision = 2`, commit (`truki-phone: 8.32.1-truki2 (update test)`), and run the release script. The user keeps Truki installed. Within the updater's check interval (or by opening Truki), it offers or installs `truki2`. Confirm on the phone: Settings → app info shows `8.32.1-truki2`. If Truki is still unregistered and the updater is gated on registration, record that, and verify the update by manually installing `truki2` over `truki1` (same key) instead. The automatic path is then re-checked after Task 7.
- [ ] **Step 5: Ledger** the release URLs and the observed update behaviour.

### Task 6: Dry run of the restore path (emulator)

- [ ] **Step 1:** Start an x86_64 emulator, then `adb install` the debug phone APK (`assemblePhoneProdDebug`, x86_64).
- [ ] **Step 2:** Walk registration to "Transfer or restore account" and look for an on-device backup option. Take screenshots with `adb exec-out screencap -p`. Point it at a dummy file, and confirm it reaches the passphrase prompt or a clear format error, without entering a phone number.
- [ ] **Step 3:** If the option exists, ledger the exact path, with the button labels as they appear. If it does not, **stop the plan** and report to the user with the screenshots (Review Focus 5).

### Task 7: The move (with the user, on the Samsung)

Follow spec §2, steps 4–11, exactly. After each step, confirm with the user before going on. Stop at the first failure and ask.

- [ ] **Step 1:** Screenshot the linked devices in official Signal (spec step 4). Confirm the key backup and `.env` (Task 3).
- [ ] **Step 2:** The user makes an on-device backup in official Signal and notes the passphrase (step 5).
- [ ] **Step 3:** The user restores in Truki along the Task 6 path, then number → SMS → PIN (step 6).
- [ ] **Step 4:** Samsung: "Never sleeping apps" plus battery optimisation off for Truki (step 8).
- [ ] **Step 5: Linked devices** (step 9).
  - Check in Truki → Linked devices.
  - For each one missing, re-link it with Truki as the scanner: desktop on anteos, the watch, and the hub's account A (`signal-cli link` per the `sending-signal-messages` skill's re-link section).
  - Verify account A by running `python3 scripts/signal_triage.py --dry-run` in the hub after the user sends a test message.
- [ ] **Step 6: Checklist** (step 10).
  - Each item is confirmed by the user, or by logs (the hub inbox log for agent messages).
  - The screen-off test: the user locks the phone, waits 30 minutes, then the hub sends `signal_notify`. The user reports the notification time.
- [ ] **Step 7: Two-week task.** Create it with hubi `create_task`: category `varia`, title "Truki telefonis: eemalda ametlik Signal või võta tagasi", deadline today + 14 days, goal from spec §2 step 11. Then `regenerate_calendar`, and commit in the hub.
- [ ] **Step 8:** Ledger the outcome. Re-check the automatic update path now that Truki is registered (Task 5 Step 4 note).
