# Truki for Android

Truki is a fork of [Signal for Android](https://github.com/signalapp/Signal-Android),
built for one person's own use. It is not affiliated with or endorsed by Signal
Messenger, LLC. Upstream's own README is at [`/README.md`](../README.md).

This repository builds two apps from upstream's code, each as its own product flavor:

- **`phone`** (`ee.truki.phone`): the phone app, which updates itself from this repository's
  [releases](https://github.com/August-Roosi/truki-android/releases).
- **`wear`** (`ee.truki.wear`): a Wear OS app that links to the phone as a secondary device.

Truki's own code lives in `app/src/phone/` and `app/src/wear/`. Edits to upstream files are
kept small and marked `// Truki:`, so new upstream releases merge cheaply.

Licensed under the GNU AGPLv3, like upstream (see [`LICENSE`](../LICENSE)).
