# LumeTime

Ultra-lightweight (`< 1 MB` release APK), zero-background-drain Android Countdown, Year Pulse & Liquid Glass Widget app.

## Highlights
- **Year Pulse & Living Dot-Matrix:** Track Days Left, Hours Left, Weeks Left, and `% Completed` with a 365/366-day glowing mosaic.
- **Unlimited Countdowns & Count-Up Milestones:** Yearly/Monthly auto-repeat, Quick Templates, Category Filters, Total Hours mode, and Undo Delete.
- **Odometer Rolling Digits & Zen Focus Mode:** Smooth `DD : HH : MM : SS` digit transitions, circular progress ring, Landscape Desk Clock, and Keep-Screen-Awake toggle.
- **6 Liquid Glass Themes:** Midnight Obsidian, Apple Frost, Aurora Mint, Sunset Rose, Deep Ocean, Lavender Mist — with Auto / Dark / Light glass modes.
- **3 Zero-Drain Home Screen Widgets + Widget Studio:**
  - Year Pulse Mini (`4×1`)
  - Hero Glass Countdown (`2×2` / `4×1`) with optional native `Chronometer` live tick
  - Multi-Countdown Scrollable Glass List (`4×2` / `4×4`)
- **Shareable Glass Poster & Local Backup:** 1-tap `1080×1350` PNG poster sharing (`FileProvider`), optional `AlarmManager` local reminders, and SAF JSON Backup/Restore.

## Zero-Drain Architecture
- In-app animations run strictly between `onStart()` and `onStop()` — **0% background CPU**.
- Widgets refresh only on top-of-hour / midnight boundaries via inexact `AlarmManager.setWindow` and immediately when edited in-app.
- Pure AndroidX + Kotlin Coroutines (`minSdk 24`, `targetSdk 35`, R8 minification & resource shrinking).

## Cloud Builds
Every push and `v*` tag runs `testDebugUnitTest`, `assembleRelease`, and `assembleDebug` via GitHub Actions.
