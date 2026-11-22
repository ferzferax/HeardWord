package com.example.audiomonitor.data

enum class MonitoringMode(val labelEn: String, val labelPt: String, val defaultThreshold: Float) {
    ADAPTATIVO("Adaptive", "Adaptativo", -36.0f),
    NOTURNO("Night", "Noturno", -48.0f),
    NORMAL("Normal", "Normal", -32.0f),
    RUIDOSO("Noisy", "Ruidoso", -24.0f);

    fun label(language: String): String = if (language == "PT") labelPt else labelEn
}

data class DetectedSound(
    val name: String,
    val confidence: Float,
    val isCritical: Boolean = false,
    val isCustom: Boolean = false
)

data class SoundHistoryItem(
    val id: Long = System.currentTimeMillis(),
    val timeFormatted: String,
    val name: String,
    val confidence: Float,
    val rmsDb: Float,
    val isCritical: Boolean = false,
    val isCustom: Boolean = false
)

data class AcousticMetrics(
    val rmsDb: Float = -100.0f,
    val peakDb: Float = -100.0f,
    val dominantHz: Float = 0.0f,
    val centroidHz: Float = 0.0f,
    val bandSubGravePct: Int = 0,
    val bandMedioPct: Int = 0,
    val bandAgudoPct: Int = 0,
    val crestFactor: Float = 1.0f,
    val spectrum: List<Float> = List(16) { 0.0f }
)

data class MonitoringState(
    val language: String = "EN",
    val isRunning: Boolean = false,
    val mode: MonitoringMode = MonitoringMode.ADAPTATIVO,
    val thresholdDb: Float = -36.0f,
    val vibrateOnAlert: Boolean = true,
    val currentMetrics: AcousticMetrics = AcousticMetrics(),
    val topSounds: List<DetectedSound> = emptyList(),
    val recentAlerts: List<String> = emptyList(),
    val soundHistory: List<SoundHistoryItem> = emptyList(),
    val customSoundsCount: Int = 0,
    val statusMessage: String = "Ready to start"
)

data class CustomSound(
    val name: String,
    val embedding: List<Float>,
    val sampleCount: Int = 1,
    val createdAt: Long = System.currentTimeMillis()
)
