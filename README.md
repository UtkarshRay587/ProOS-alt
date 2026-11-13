# Pro OS Launcher

A real Android home-screen launcher with a dot-matrix, monochrome, glass look.

## What it does

- Dot-matrix clock (follows your 12/24 hour setting) with the date and your next alarm
- Battery ring made of dots, filled from the real battery level
- Today tile with the date in dots
- Home grid of 8 apps and a 4-app dock, picked automatically (Settings, Gallery, Clock, Calculator, Calendar, Music, Maps, Email, Phone, Messages, Browser, Camera) and editable
- App drawer with search. Swipe up on the home screen, or tap the search bar
- Long press an app: pin to home / remove from home, app info, uninstall
- Long press empty space on the home screen: change wallpaper, monochrome icons on/off, next accent colour (red, orange, blue), default home app settings
- Frosted glass cards. The drawer blurs your real wallpaper on Android 12 and newer when the device supports window blur
- Back never closes the launcher. Pressing Home closes the drawer

## What a launcher cannot change

The notification shade, quick settings, lock screen and the recents screen belong to the system. A launcher app cannot restyle them. Those need a custom ROM.

## Build the APK with GitHub Actions

1. Create a new GitHub repository and upload everything in this folder (keep the `.github` folder).
2. Open the **Actions** tab, choose **Build APK** and run it (it also runs on every push to main).
3. When it finishes, download the **ProOS-debug-apk** artifact and unzip it to get `app-debug.apk`.

## Build with Android Studio

Open this folder as a project and let it sync (Android Studio 2023.3 or newer, JDK 17). Use **Build > Build APK(s)**, or run it on a phone.

## Install and set as your launcher

1. Copy `app-debug.apk` to the phone and install it (allow installs from your file manager if asked).
2. Press the Home button. Android asks which home app to use. Choose **Pro OS** and tap **Always**.
3. Or go to Settings > Apps > Default apps > Home app and select Pro OS.

To go back to your old launcher, use the same setting and pick the old one.

## Files

- `app/src/main/java/com/pro444yt/proos/LauncherActivity.kt` the launcher screen, drawer, pins, menus
- `DotMatrixView.kt` the dot-matrix text renderer
- `DotRingView.kt` the dotted battery ring
- `Glass.kt` the frosted glass backgrounds
- `app/src/main/AndroidManifest.xml` declares the app as a Home launcher
- `.github/workflows/build.yml` builds the APK

Package name: `com.pro444yt.proos`. Minimum Android 8.0 (API 26).
