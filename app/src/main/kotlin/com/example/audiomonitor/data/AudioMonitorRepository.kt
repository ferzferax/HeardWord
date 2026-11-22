package com.example.audiomonitor.data

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.audiomonitor.ml.YamnetClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AudioMonitorRepository private constructor(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val classifier = YamnetClassifier(context)

    private val initialLang = prefs.getString(KEY_LANGUAGE, "EN") ?: "EN"

    private val _state = MutableStateFlow(
        MonitoringState(
            language = initialLang,
            customSoundsCount = classifier.getCustomSounds().size,
            statusMessage = if (initialLang == "PT") "Pronto para iniciar" else "Ready to start"
        )
    )
    val state: StateFlow<MonitoringState> = _state.asStateFlow()

    private var lastVibrationTime = 0L
    private var lastSoundLoggedName = ""
    private var lastSoundLoggedTime = 0L
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    init {
        classifier.language = initialLang
    }

    fun setLanguage(lang: String) {
        val normalized = if (lang == "PT") "PT" else "EN"
        prefs.edit().putString(KEY_LANGUAGE, normalized).apply()
        classifier.language = normalized
        _state.update { current ->
            val msg = when {
                current.isRunning && normalized == "PT" -> "Monitorando áudio contínuo"
                current.isRunning -> "Monitoring continuous audio"
                normalized == "PT" -> "Pronto para iniciar"
                else -> "Ready to start"
            }
            current.copy(language = normalized, statusMessage = msg)
        }
    }

    fun toggleLanguage() {
        val next = if (_state.value.language == "EN") "PT" else "EN"
        setLanguage(next)
    }

    fun setRunning(running: Boolean, status: String? = null) {
        val lang = _state.value.language
        val resolvedStatus = status ?: if (running) {
            if (lang == "PT") "Monitorando áudio" else "Monitoring audio"
        } else {
            if (lang == "PT") "Parado" else "Stopped"
        }
        _state.update { it.copy(isRunning = running, statusMessage = resolvedStatus) }
    }

    fun setMode(mode: MonitoringMode) {
        _state.update { it.copy(mode = mode, thresholdDb = mode.defaultThreshold) }
    }

    fun setThreshold(threshold: Float) {
        _state.update { it.copy(thresholdDb = threshold) }
    }

    fun setVibrate(vibrate: Boolean) {
        _state.update { it.copy(vibrateOnAlert = vibrate) }
    }

    fun clearHistory() {
        _state.update { it.copy(soundHistory = emptyList(), recentAlerts = emptyList()) }
    }

    fun updateMetrics(metrics: AcousticMetrics, sounds: List<DetectedSound>) {
        val currentState = _state.value
        val topSound = sounds.firstOrNull()
        val hasCritical = sounds.any { it.isCritical && it.confidence > 0.30f }
        val isOverThreshold = metrics.rmsDb >= currentState.thresholdDb

        var updatedHistory = currentState.soundHistory
        val now = System.currentTimeMillis()

        if (topSound != null && (isOverThreshold || hasCritical || topSound.confidence >= 0.25f)) {
            val isDuplicate = (topSound.name == lastSoundLoggedName && (now - lastSoundLoggedTime) < 2500)
            val isSilence = topSound.name.equals("Silence", ignoreCase = true) ||
                topSound.name.equals("Silêncio", ignoreCase = true)

            if (!isDuplicate && !isSilence) {
                lastSoundLoggedName = topSound.name
                lastSoundLoggedTime = now

                val newItem = SoundHistoryItem(
                    id = now,
                    timeFormatted = timeFormat.format(Date(now)),
                    name = topSound.name,
                    confidence = topSound.confidence,
                    rmsDb = metrics.rmsDb,
                    isCritical = topSound.isCritical,
                    isCustom = topSound.isCustom
                )
                updatedHistory = (listOf(newItem) + updatedHistory).take(100)
            }

            if (currentState.vibrateOnAlert && hasCritical) {
                triggerVibration()
            }
        }

        _state.update {
            it.copy(
                currentMetrics = metrics,
                topSounds = sounds,
                soundHistory = updatedHistory
            )
        }
    }

    private fun triggerVibration() {
        val now = System.currentTimeMillis()
        if (now - lastVibrationTime < 4000) return
        lastVibrationTime = now

        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(120)
                }
            }
        } catch (_: Exception) {
        }
    }

    fun teachSound(name: String, embedding: FloatArray) {
        classifier.addCustomSound(name, embedding)
        _state.update { it.copy(customSoundsCount = classifier.getCustomSounds().size) }
    }

    fun removeCustomSound(name: String) {
        classifier.removeCustomSound(name)
        _state.update { it.copy(customSoundsCount = classifier.getCustomSounds().size) }
    }

    fun refreshCustomSoundsCount() {
        _state.update { it.copy(customSoundsCount = classifier.getCustomSounds().size) }
    }

    suspend fun syncFromGithubVault(vaultUrl: String = GITHUB_VAULT_RAW_URL): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                val conn = (URL(vaultUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("User-Agent", "HeardWord-Android")
                }
                val code = conn.responseCode
                if (code !in 200..299) {
                    conn.disconnect()
                    return@withContext Result.failure(IllegalStateException("HTTP $code"))
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val imported = classifier.importCustomSoundsJson(body, preserveSamples = true)
                refreshCustomSoundsCount()
                Result.success(imported)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    companion object {
        private const val PREFS_NAME = "heardword_prefs"
        private const val KEY_LANGUAGE = "app_language"
        const val GITHUB_REPO_URL = "https://github.com/ferzferax/HeardWord"
        const val GITHUB_VAULT_RAW_URL =
            "https://raw.githubusercontent.com/ferzferax/HeardWord/main/community_sounds/community_sound_vault.json"

        @Volatile
        private var INSTANCE: AudioMonitorRepository? = null

        fun getInstance(context: Context): AudioMonitorRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AudioMonitorRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
