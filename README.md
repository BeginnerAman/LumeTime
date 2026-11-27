# LumeTime v2.0.0

Ultra-lightweight (`< 1 MB` release APK), zero-background-drain Android Countdown, Year Pulse & Apple Liquid Glass Widget app.

## V2 Highlights
- **True Multi-Layer Specular Liquid Glass UI:** Diagonal frosted sheen, crisp top-edge specular rims, GPU-blurred multi-orb ambient mesh (Android 12+), split-glass horology odometer tiles, and slide-up Apple Glass bottom sheets.
- **Metronome-Steady 1s Precision Clock:** Midpoint-aligned `PrecisionClockTicker` eliminates second-boundary jitter and double jumps.
- **Crisp SF-Style Vector Icon System:** 10 custom monochrome vector badges inside circular progress rings (zero cartoonish emojis).
- **Clean Hierarchy & Productivity:** Collapsible Year Pulse Dot-Matrix (`365/366` days), instant search across titles/notes/categories, optional event notes, and 4 time formats (`Days+Hrs`, `Total Hours`, `Weeks+Days`, `Months+Days`).
- **3 Specular Liquid Glass Home Screen Widgets + Widget Studio:**
  - Year Pulse Mini (`4×1`)
  - Hero Glass Countdown (`2×2` / `4×1`) with optional native `Chronometer` live tick
  - Multi-Countdown Scrollable Glass List (`4×2` / `4×4`)
- **Shareable Glass Poster, Local Reminders & Backup:** 1-tap `1080×1350` PNG poster sharing (`FileProvider`), optional `AlarmManager` local reminders, and zero-permission SAF JSON Backup/Restore.

## Zero-Drain Architecture
- In-app animations run strictly between `onStart()` and `onStop()` — **0% background CPU**.
- Widgets refresh only on top-of-hour / midnight boundaries via inexact `AlarmManager.setWindow` and immediately when edited in-app.
- Pure AndroidX + Kotlin Coroutines (`minSdk 24`, `targetSdk 35`, R8 minification & resource shrinking).

## Cloud Builds
Every push and `v*` tag runs `testDebugUnitTest`, `assembleRelease`, and `assembleDebug` via GitHub Actions.
