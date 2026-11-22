# Contributing to HeardWord

Thank you for checking out **HeardWord**! Because this is an **early-stage open-source project** with plenty of room for improvement, community feedback, testing, PRs, and custom sound packs are warmly welcomed!

---

## 1. Contributing New Sounds to the Community Sound Vault (No Code Required!)

HeardWord lets anyone teach the app new sounds and share them via Git/GitHub **without ever uploading raw voice or household audio recordings**:

1. Open **HeardWord** -> go to the **Studio** tab.
2. Enter a clear sound name (e.g., `Doorbell`, `Coffee Grinder`, `Kettle Whistle`) and tap **Record & Learn Sound**.
3. Tap **Submit Sound Pack to GitHub** — HeardWord copies your `heardword-sound-pack/v1` JSON (containing only the 521-dimensional float embedding vector) and opens the [Sound Pack Submission Issue](https://github.com/ferzferax/HeardWord/issues/new?template=sound_pack_submission.md) form.
4. Or open a **Pull Request** adding your JSON entry into `community_sounds/community_sound_vault.json`.

Once merged into `main`, every user can tap **Sync Vault from GitHub** inside the app to recognize the newly added sounds immediately!

---

## 2. Contributing Code & Bugfixes

1. Fork `https://github.com/ferzferax/HeardWord`.
2. Ensure **JDK 17** and **Android SDK 36** are configured (`local.properties` -> `sdk.dir=...`).
3. Build and test on a real device:
   ```bash
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
4. Submit a Pull Request describing the improvement clearly in English.
