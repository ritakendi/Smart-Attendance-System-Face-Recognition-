# Smart Attendance

An Android attendance app that uses face matching to mark registered students present. It is a local prototype built with Kotlin, Jetpack Compose, CameraX, ML Kit Face Detection, MobileFaceNet/TFLite, Room, and SQLCipher.

## Features

- Register students with a name and class after confirming consent.
- Capture and save multiple face embeddings for each student.
- Scan one or more faces and automatically mark recognized students once per day.
- View daily reports by date and filter by student or class; manually mark or undo attendance.
- View monthly days-present summaries and export daily or monthly reports as CSV or PDF.

## Run the app

Requirements: Android Studio with JDK 17 and an Android device or emulator running API 24 or newer. Camera access is needed for face capture and attendance scanning.

Build the debug APK from the project root:

```bash
./gradlew :app:assembleDebug
```

The APK is created at `app/build/outputs/apk/debug/app-debug.apk`. To install it on a connected device with USB debugging enabled:

```bash
./gradlew :app:installDebug
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

## Typical workflow

1. Choose **Register Student**, read the privacy notice, confirm consent, and enter the student’s name and class.
2. Open **View Students**, choose **Add face**, point the front camera at the student, and tap **Save face sample**. Repeat to add more samples.
3. Choose **Take Attendance** and allow camera access. Recognized students are marked automatically; no attendance save button is needed.
4. Open **View Reports** to review daily attendance, correct a daily entry, switch to a monthly summary, or export the current report as CSV or PDF.

## Face data and privacy

- Face frames are processed to create 128-value embeddings. The app stores embeddings, not the captured face photos.
- Student, face-sample, and attendance records are stored in a local Room database encrypted with SQLCipher. Its encryption key is protected with Android Keystore.
- Consent confirmation is saved with the student record. Students created before this consent record was added must confirm again before their face samples are enrolled or used for matching.
- No account or app server is configured. Android system backup may still apply according to the manifest and device settings.

The face-embedding model is MobileFaceNet from Qualcomm AI Hub, licensed under Apache-2.0. See [`MODEL_NOTICES.txt`](app/src/main/assets/MODEL_NOTICES.txt).

## Current limitations

- The cosine-similarity cutoff (`0.50`) is a starting value and needs testing with the intended students and devices.
- Multi-face scanning is implemented, but classroom accuracy and performance still need on-device verification.
- The app has no liveness check, so a photo or video may be used to spoof a face match.
- Monthly reports show days present; the app does not track scheduled school days or calculate absences.
- Consent revocation controls are not implemented.
