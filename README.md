[![Continuous Integration](https://github.com/mlocati/android-redialer/actions/workflows/ci.yml/badge.svg)](https://github.com/mlocati/android-redialer/actions/workflows/ci.yml)

# Redialer

An Android application designed to automatically redial phone numbers that are frequently busy. Built with modern Android technologies including Jetpack Compose, Coroutines, and DataStore.

## Features

- **Automatic Redialing**: Continuously redials a number until stopped or a successful connection is detected.
- **Smart Stop Detection**: Automatically stops redialing if a call lasts longer than a user-defined threshold. This helps distinguish between a busy signal and a successful connection (ringing or answered call).
- **Configurable Delay**: Set a custom delay between redial attempts.
- **Contact Integration**: Easily pick numbers directly from your contacts list.
- **Persistent Settings**: Your preferences (delay and stop threshold) are saved automatically using Jetpack DataStore.
- **Multilingual Support**: Available in English, Italian, and French.
- **Clean UI**: Developed using Jetpack Compose with Material 3.

## Screenshots

| Phone (idle) | Phone (calling) | Tablet (idle) | Tablet (calling) |
|:---:|:---:|:---:|:---:|
| [![Phone (idle)](https://github.com/mlocati/android-redialer/blob/main/input_images/screenshot-phone-1.png?raw=true)](https://github.com/mlocati/android-redialer/blob/main/input_images/screenshot-phone-1.png) | [![Phone (calling)](https://github.com/mlocati/android-redialer/blob/main/input_images/screenshot-phone-2.png?raw=true)](https://github.com/mlocati/android-redialer/blob/main/input_images/screenshot-phone-2.png) | [![Tablet (idle)](https://github.com/mlocati/android-redialer/blob/main/input_images/screenshot-tablet7-1.png?raw=true)](https://github.com/mlocati/android-redialer/blob/main/input_images/screenshot-tablet7-1.png) | [![Tablet (calling)](https://github.com/mlocati/android-redialer/blob/main/input_images/screenshot-tablet7-2.png?raw=true)](https://github.com/mlocati/android-redialer/blob/main/input_images/screenshot-tablet7-2.png) |

### Building

You'll need:

- Android Studio
- JDK 17
- Android device or emulator running API level 24 (Android 7.0) or higher

Steps:

1. Clone the repository
2. Open the project in Android Studio
3. Sync Gradle and build the project
4. Run on your device

## CI/CD and Release Security

The project includes [a GitHub Actions workflow](https://github.com/mlocati/android-redialer/actions/workflows/ci.yml) that automatically builds both Debug and Release versions of the APK on every push or pull request to the `main` branch.

When a version tag (e.g., `1.2.0`) is pushed, the workflow automatically creates a GitHub Release and attaches the compiled APKs.

**Release Immutability**: To ensure the integrity of the distributed binaries, this repository has "Release Immutability" enabled. This means that once a release is published by the automated CI/CD pipeline, its tags and associated assets (APKs) cannot be modified or replaced, guaranteeing that the files you download are exactly those produced by the verified build process.

## Technical Details

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material 3)
- **Architecture**: MVVM with ViewModel and StateFlow
- **Concurrency**: Kotlin Coroutines and Flows
- **Storage**: Jetpack DataStore (Preferences)
- **Build System**: Gradle Kotlin DSL

## Permissions

The app requires the following permissions to function:
- `CALL_PHONE`: To initiate the phone calls.
- `READ_PHONE_STATE`: To detect when a call starts and ends.
- `READ_CONTACTS`: (Optional) To pick a phone number from your contact list.

## Installation

| GitHub | Play Store |
|:---:|:---:|
| [![Get It from GitHub Releases](https://github.com/mlocati/android-redialer/blob/main/input_images/get-it-github-releases.svg?raw=true)](https://github.com/mlocati/android-redialer/releases) | [![Get It from Play Store](https://github.com/mlocati/android-redialer/blob/main/input_images/get-it-google-play.png?raw=true)](https://play.google.com/store/apps/details?id=it.locati.michele.redialer) |
