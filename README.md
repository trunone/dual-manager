# Samsung Dual Messenger App Manager

A specialized Android application for managing apps within Samsung's Dual Messenger profile using the Shizuku API. This tool allows users to clone apps, manage special permissions, and maintain full visibility over the Dual Messenger environment.

## 🚀 Features

- **Profile Management**: Full support for Samsung's Dual Messenger profile (User 95).
- **App Cloning**: Easily clone any installed app from the main user profile to the Dual Messenger profile.
- **Search & Filter**: Quickly find any app by name or package name across both main and dual lists.
- **Special Permissions**: Toggle "Special Access" permissions (AppOps) like **All Files Access**, **Display over other apps**, and **Install unknown apps** for Dual Messenger apps.
- **Shizuku Integration**: Leverages the privileged Shizuku API for seamless shell operations without root.
- **Status Monitoring**: Integrated warning system if Shizuku is not running or lacks authorization.
- **Premium UI**: Modern Jetpack Compose interface with dark mode support and adaptive icons.

## 🛠 Prerequisites

- **Samsung Device**: Requires a Samsung Galaxy device with Dual Messenger support.
- **Shizuku**: Must have the [Shizuku](https://shizuku.rikka.app/) app installed and running.

## 📦 Setup & Build

1. **Clone the repository**:
   ```bash
   git clone <your-repo-url>
   cd dual-manager
   ```
2. **Build the APK**:
   ```bash
   ./gradlew assembleDebug
   ```
3. **Install**:
   Install the generated APK at `app/build/outputs/apk/debug/app-debug.apk`.

## 📖 How to Use

1. Ensure **Shizuku** is running and authorized for this app.
2. In the **Main Apps** tab, find an app and click **Clone** to add it to Dual Messenger.
3. In the **Dual Messenger** tab:
   - Click **Uninstall** to remove an app.
   - **Click the app item** itself to manage its **Special Permissions** (AppOps).

## 📄 License

This project is released into the public domain under the [Unlicense](LICENSE).
