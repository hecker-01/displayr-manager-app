# Displayr

A lightweight Android app that turns any device into a managed web display. Point it at a URL and it loads it in a full-screen WebView — ideal for dashboards, signage, kiosks, or any always-on screen.

## Features

- **Full-screen WebView** — loads any URL with JavaScript, DOM storage, and zoom support
- **Guided setup** — first-launch wizard walks through an update check and URL configuration
- **QR code scanning** — scan a QR code to set the display URL (uses CameraX + ML Kit)
- **Deep linking** — configure devices remotely with `displayr://setup?url=https://example.com`
- **In-app updates** — checks GitHub Releases for new versions, downloads and installs APKs directly
- **Settings** — change URL, check for updates, clear cache
- **Material You** — follows the system dynamic color theme

## Requirements

- Android 12+ (API 31)
- Camera permission (optional, for QR scanning)

## Deep Links

Set or change the display URL from outside the app:

```
displayr://setup?url=https://your-dashboard.example.com
```

- If the app hasn't been set up yet, it opens the setup wizard with the URL pre-filled.
- If the app is already running, it shows a confirmation dialog and reloads with the new URL.

This also works as a QR code — encode the URI above and scan it with any camera app.

## Building

```bash
# Debug build
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Release build
./gradlew assembleRelease
```

The debug build uses application ID `app.displayr.manager.dev` so it can be installed alongside the release version.

## Project Structure

```
app/src/main/
├── java/app/displayr/manager/
│   ├── MainActivity.kt          # WebView host + settings FAB
│   ├── SetupActivity.kt         # First-launch setup wizard
│   ├── SettingsActivity.kt      # URL, updates, cache settings
│   ├── QrScannerActivity.kt     # CameraX barcode scanner
│   └── updater/
│       ├── UpdateChecker.kt     # GitHub Releases API client
│       └── AppUpdater.kt        # Download + install state machine
└── res/
    ├── layout/                  # All screen layouts
    ├── drawable/                # Vector icons
    └── anim/                    # Page transition animations
```

## License

All rights reserved.