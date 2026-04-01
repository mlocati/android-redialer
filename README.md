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

## Getting Started

### Prerequisites

- Android Studio Ladybug or newer.
- JDK 17.
- Android device or emulator running API level 24 (Android 7.0) or higher.

### Building

1. Clone the repository:
2. Open the project in Android Studio.
3. Sync Gradle and build the project.
4. Run on your device.

## CI/CD

The project includes [a GitHub Actions workflow](https://github.com/mlocati/android-redialer/actions/workflows/ci.yml) that automatically builds both Debug and Release versions of the APK on every push or pull request to the `main` branch.

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
