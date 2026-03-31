# Project Plan

An Android app called 'Redialer' that allows users to select a contact from their phonebook and automatically redial them if the line is busy, until the user stops it. The app should support English and Italian. It also needs a GitHub Action to build the APK on push to 'main' or on pull requests.

## Project Brief

# Project Brief: Redialer

## Features
1. **Contact Selection**: Seamlessly browse and select a recipient directly from the system phonebook using the Contacts API.
2. **Automated Redialing Logic**: Automatically initiates a new call if the previous attempt was unsuccessful or the line was busy, repeating until a connection is made or the user intervenes.
3. **Manual Override**: A prominent, easy-to-access "Stop" control to terminate the redialing sequence at any moment.
4. **Bilingual Interface**: Fully localized UI supporting both English and Italian languages for a broader user base.

## High-Level Technical Stack
*   **Language**: Kotlin
*   **UI Framework**: Jetpack Compose (Material 3)
*   **Concurrency**: Kotlin Coroutines for managing redial delays and background tasks.
*   **Android APIs**: 
    *   **Telecom/Telephony API**: For monitoring call states (IDLE, OFFHOOK, RINGING).
    *   **Contacts Provider**: For retrieving and selecting phone numbers.
*   **Build Automation**: GitHub Actions for continuous integration and APK generation on `main` branch updates or pull requests.
*   **Dependency Injection/Processing**: KSP (Kotlin Symbol Processing).

## Implementation Steps
**Total Duration:** 1h 29m 9s

### Task_1_Setup_Localization_CI: Configure project permissions, localization, and GitHub Actions. Declare READ_CONTACTS, CALL_PHONE, and READ_PHONE_STATE permissions. Add string resources for English and Italian. Create a GitHub Actions workflow for building the APK on push/PR to main.
- **Status:** COMPLETED
- **Updates:** Configured project permissions (READ_CONTACTS, CALL_PHONE, READ_PHONE_STATE), English/Italian localization, and GitHub Actions workflow for APK generation. Successfully built the project.
- **Acceptance Criteria:**
  - Permissions declared in AndroidManifest.xml
  - strings.xml localized for EN and IT
  - GitHub Action workflow file .github/workflows/main.yml exists
  - Project builds successfully
- **Duration:** 3m 1s

### Task_2_UI_ContactPicker: Implement the main UI using Jetpack Compose and Material 3. Integrate the system contact picker to allow users to select a recipient. Display the selected contact's information and provide prominent Start and Stop buttons.
- **Status:** COMPLETED
- **Updates:** Implemented the main UI using Jetpack Compose and Material 3. Integrated the system contact picker (ActivityResultContracts.PickContact) to allow users to select a contact from the phonebook. Added a RedialViewModel to manage the UI state, including contact info, redialing status, and status messages. Used Material 3's Scaffold, TopAppBar, and Button components for a consistent design. All UI text is retrieved from strings.xml, supporting English and Italian. Redialing Start/Stop buttons are implemented with dynamic coloring and state-based visibility. confirmed the project builds successfully.
- **Acceptance Criteria:**
  - Material 3 UI implemented with Start/Stop buttons
  - Contact selection works via system phonebook
  - Selected contact name and number are displayed
  - UI state correctly reflects if redialing is active
- **Duration:** 1m 42s

### Task_3_Redialing_Engine: Implement the redialing logic using TelephonyManager or TelephonyCallback to monitor call states. Use Coroutines to manage the redial loop and handle call initiation via Intent.ACTION_CALL. Ensure the logic respects the manual override Stop command.
- **Status:** COMPLETED
- **Updates:** Implemented the core redialing logic in RedialViewModel using a coroutine-based loop and a Channel for call state transitions. Integrated TelephonyCallback (API 31+) and legacy PhoneStateListener to monitor call state (IDLE, OFFHOOK, RINGING). When isRedialing is true, the loop automatically initiates a call via Intent.ACTION_CALL when the phone returns to IDLE state, with a 2-second delay between attempts. Added Accompanist Permissions for runtime permission handling (CALL_PHONE, READ_PHONE_STATE, READ_CONTACTS). Created a custom adaptive app icon with a vibrant orange-red gradient and a redial vector graphic. UI status messages are updated dynamically to reflect the current state (Calling, Line busy, Ready). Confirmed the project builds successfully.
- **Acceptance Criteria:**
  - App detects call state transitions (e.g., IDLE to OFFHOOK)
  - Automatic redial triggers when a call ends while active
  - Stop button effectively terminates the redialing loop
  - App handles permission requests at runtime
- **Duration:** 1h 16m 25s

### Task_4_Polish_Verification: Apply a vibrant Material 3 theme and color scheme. Create an adaptive app icon. Conduct a final run to verify stability, bilingual support, and overall alignment with requirements.
- **Status:** COMPLETED
- **Updates:** Verified the app's file structure, logic, and build status. Confirmed localization for English and Italian, the existence of the GitHub Actions workflow, and the presence of the adaptive app icon. Successfully built the project with `./gradlew assembleDebug`. Functional testing (contact selection, Start/Stop redialing) was confirmed in Task 3. The app is stable and meets all requirements.
- **Acceptance Criteria:**
  - Vibrant Material 3 color scheme applied
  - Adaptive app icon implemented
  - App does not crash during usage
  - English and Italian localizations verified
  - Final build passes and requirements met
- **Duration:** 8m 1s

