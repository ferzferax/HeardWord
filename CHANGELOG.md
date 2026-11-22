# Changelog

All notable changes to **HeardWord** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.2.0-alpha] - 2026-11-22

### Added
- **Dual Acoustic & Neural Noise Gate:** Added strict dBFS threshold gating (`rmsDb < thresholdDb`) and YAMNet `"Silence"` class dominance gating (`>= 45%`) to eliminate false-positive labels during quiet ambient periods ([YamnetClassifier.kt](app/src/main/kotlin/com/example/audiomonitor/ml/YamnetClassifier.kt)).
- **Temporal EMA Smoothing & Fast Impulse Bypass:** Added Exponential Moving Average smoothing (`0.55` raw / `0.45` history) across all 521 AudioSet classes with a fast-attack bypass (`0.85`) for critical impulsive alerts (glass shatter, alarm, bark, gunfire).
- **Contextual AudioSet Hallucination Filter:** Filtered out non-urban / microphone-noise artifact classes frequently triggered by thermal hiss (`Snake`, `Rattle`, `Hiss`, `Purr`, `Frog`, `Livestock`, `Wild animals`, `Didgeridoo`, `Arrow`, `Steam`, `Wind noise`, and internal room/noise tags).
- **Smart Peak-Energy Window Slicing (Studio):** When recording a 2-second sample in Acoustic Learning Studio, HeardWord now scans the buffer and extracts the 1-second (`15,600` samples) window with the highest RMS acoustic energy ([StudioScreen.kt](app/src/main/kotlin/com/example/audiomonitor/ui/studio/StudioScreen.kt)).
- **Quiet State Live Indicator:** Monitor card now clearly displays when the environment is below the configured `dBFS` threshold ([MonitorScreen.kt](app/src/main/kotlin/com/example/audiomonitor/ui/monitor/MonitorScreen.kt)).

### Changed
- **Microphone Front-End Calibration (`VOICE_RECOGNITION` + DC Offset Removal):** Switched continuous capture from raw `AudioSource.UNPROCESSED` to `AudioSource.VOICE_RECOGNITION` (with fallback to `MIC`) and added per-window DC offset subtraction ([AudioMonitorService.kt](app/src/main/kotlin/com/example/audiomonitor/service/AudioMonitorService.kt), [AudioProcessor.kt](app/src/main/kotlin/com/example/audiomonitor/audio/AudioProcessor.kt)).
- **Removed Aggressive Low-Volume Gain Boost:** Removed the blind `2.5x` low-amplitude multiplier that amplified capsule thermal noise into false `"Snake"` / `"Hiss"` predictions; replaced with mild `1.35x` conditioning only above `-35 dBFS` RMS.
- **Raised Minimum Confidence Thresholds:** Increased baseline live detection minimum from `8%` to `25%` (`18%` for critical sounds above threshold, `78%` cosine similarity for custom sounds, and `28%` for flashing visual alert banners) ([AudioMonitorRepository.kt](app/src/main/kotlin/com/example/audiomonitor/data/AudioMonitorRepository.kt)).
- **Non-Debuggable Signed Release APK & Android 16 Packaging:** Built as a signed release APK (`versionCode = 2`), added `androidx.graphics:graphics-path:1.0.1` and `jniLibs.useLegacyPackaging = true`, eliminating the Android 15/16 debuggable ELF compatibility warning popup.

### Fixed
- Cleared stale `topSounds` and reset EMA smoothing buffers immediately when monitoring is stopped.
- Foreground notification now cleanly displays `"Quiet / Ambient"` (`"Silêncio / Ambiente"`) instead of retaining stale low-confidence guesses below the threshold.

---

## [0.1.0-alpha] - 2026-11-22

### Added
- Initial standalone separation of **HeardWord** from the multi-sensor `AudioMonitor` prototype.
- On-device **Google YAMNet TFLite** classifier (`521` AudioSet classes) with live **16-band Radix-2 FFT** spectrum analyzer.
- Instant **English (`EN`) / Portuguese (`PT-BR`)** toggle across UI and all 521 YAMNet labels.
- **Acoustic Learning Studio** with 521-dimensional neural embedding extraction, JSON export/import (`heardword-sound-pack/v1`), and direct GitHub **Community Sound Vault** submission & sync.
- Custom adaptive vector launcher icon (`ic_launcher_foreground.xml` / `ic_launcher_background.xml`).
