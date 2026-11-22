package com.example.audiomonitor.ui.monitor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.audiomonitor.data.DetectedSound
import com.example.audiomonitor.data.MonitoringState
import com.example.audiomonitor.data.SoundHistoryItem

@Composable
fun MonitorScreen(
    state: MonitoringState,
    onToggleService: () -> Unit,
    onClearHistory: () -> Unit = {}
) {
    val isEn = state.language == "EN"
    val topDetected = state.topSounds.firstOrNull()
    val visualAlert = detectVisualAlert(topDetected, isEn)
    var showHistoryDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AnimatedVisibility(
            visible = visualAlert != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            visualAlert?.let { alert ->
                VisualAlertBadge(alert)
            }
        }

        MiniControlButton(
            isRunning = state.isRunning,
            modeLabel = state.mode.label(state.language),
            thresholdDb = state.thresholdDb,
            isEn = isEn,
            onClick = onToggleService
        )

        SpectrumAndLevelCard(
            rmsDb = state.currentMetrics.rmsDb,
            peakDb = state.currentMetrics.peakDb,
            dominantHz = state.currentMetrics.dominantHz,
            spectrum = state.currentMetrics.spectrum,
            isRunning = state.isRunning,
            isEn = isEn
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFF2B2B2B)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161616))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEn) "LIVE RECOGNITION (YAMNET)" else "RECONHECIMENTO AO VIVO (YAMNET)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color(0xFF9E9E9E)
                    )
                    if (state.topSounds.isNotEmpty()) {
                        Text(
                            text = if (isEn) "${state.topSounds.size} active classes" else "${state.topSounds.size} classes ativas",
                            fontSize = 11.sp,
                            color = Color(0xFF00E676)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (state.topSounds.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when {
                                state.isRunning && isEn ->
                                    "Quiet environment (below ${state.thresholdDb.toInt()} dBFS threshold) — waiting for sounds..."
                                state.isRunning ->
                                    "Ambiente silencioso (abaixo do limiar de ${state.thresholdDb.toInt()} dBFS) — aguardando sons..."
                                isEn -> "Tap the button above to Start"
                                else -> "Toque no botão acima para Iniciar"
                            },
                            color = Color(0xFF757575),
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.topSounds.take(3).forEachIndexed { idx, sound ->
                            CompactSoundRow(sound = sound, isTop = idx == 0, isEn = isEn)
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFF282828)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141414))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = Color(0xFF64B5F6),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isEn) "RECENT HISTORY" else "HISTÓRICO RECENTE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color(0xFF888888)
                        )
                    }

                    OutlinedButton(
                        onClick = { showHistoryDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp),
                        border = BorderStroke(1.dp, Color(0xFF424242))
                    ) {
                        Text(
                            text = if (isEn) "View All (${state.soundHistory.size})" else "Ver Todos (${state.soundHistory.size})",
                            fontSize = 11.sp,
                            color = Color(0xFF64B5F6)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (state.soundHistory.isEmpty()) {
                    Text(
                        text = if (isEn) "No sounds logged yet." else "Nenhum som registrado ainda.",
                        fontSize = 11.sp,
                        color = Color(0xFF616161),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        state.soundHistory.take(2).forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${item.timeFormatted} • ${item.name}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFDDDDDD)
                                )
                                Text(
                                    text = "${(item.confidence * 100).toInt()}%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF81C784)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showHistoryDialog) {
        HistoryDialog(
            history = state.soundHistory,
            isEn = isEn,
            onDismiss = { showHistoryDialog = false },
            onClear = onClearHistory
        )
    }
}

@Composable
fun MiniControlButton(
    isRunning: Boolean,
    modeLabel: String,
    thresholdDb: Float,
    isEn: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isRunning) Color(0xFFE53935) else Color(0xFF43A047)
    val bgColor = if (isRunning) Color(0xFF261212) else Color(0xFF112214)

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isRunning) Color(0xFFD32F2F) else Color(0xFF2E7D32)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = when {
                            isRunning && isEn -> "LISTENING ACTIVE"
                            isRunning -> "ESCUTA ATIVA"
                            isEn -> "LISTENING PAUSED"
                            else -> "ESCUTA PAUSADA"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = if (isEn) {
                            "$modeLabel Mode • Threshold: ${thresholdDb.toInt()} dBFS"
                        } else {
                            "Modo $modeLabel • Limiar: ${thresholdDb.toInt()} dBFS"
                        },
                        fontSize = 11.sp,
                        color = Color(0xFFB0B0B0)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isRunning) Color(0xFF4A1818) else Color(0xFF1B3D22),
                border = BorderStroke(1.dp, if (isRunning) Color(0xFFFF5252) else Color(0xFF69F0AE)),
                modifier = Modifier.clickable { onClick() }
            ) {
                Text(
                    text = when {
                        isRunning && isEn -> "STOP"
                        isRunning -> "PARAR"
                        isEn -> "START"
                        else -> "INICIAR"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isRunning) Color(0xFFFF8A80) else Color(0xFFB9F6CA),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
fun SpectrumAndLevelCard(
    rmsDb: Float,
    peakDb: Float,
    dominantHz: Float,
    spectrum: List<Float>,
    isRunning: Boolean,
    isEn: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFF2B2B2B)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161616))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$rmsDb",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = when {
                            rmsDb > -22.0f -> Color(0xFFFF5252)
                            rmsDb > -36.0f -> Color(0xFFFFD740)
                            else -> Color(0xFF00E676)
                        }
                    )
                    Text(
                        text = " dBFS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF888888),
                        modifier = Modifier.padding(bottom = 3.dp, start = 2.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (isEn) "Peak: $peakDb" else "Pico: $peakDb", fontSize = 11.sp, color = Color(0xFFAAAAAA))
                    Text("Freq: ${dominantHz.toInt()} Hz", fontSize = 11.sp, color = Color(0xFF42A5F5))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = if (isEn) "SPECTRUM ANALYZER (16-BAND FFT)" else "ANALISADOR DE ESPECTRO (FFT 16 BANDAS)",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF757575)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F0F0F))
                    .border(BorderStroke(0.5.dp, Color(0xFF222222)), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    val safeSpectrum = if (spectrum.size >= 16) spectrum else List(16) { 0.05f }
                    for (i in 0 until 16) {
                        val targetHeight = if (isRunning) safeSpectrum[i].coerceIn(0.04f, 1.0f) else 0.04f
                        val animHeight by animateFloatAsState(
                            targetValue = targetHeight,
                            animationSpec = tween(durationMillis = 80),
                            label = "spectrum_bar_$i"
                        )

                        SpectrumBar(heightPct = animHeight, index = i)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("30Hz", fontSize = 9.sp, color = Color(0xFF555555))
                Text("250Hz", fontSize = 9.sp, color = Color(0xFF555555))
                Text("1kHz", fontSize = 9.sp, color = Color(0xFF555555))
                Text("4kHz", fontSize = 9.sp, color = Color(0xFF555555))
                Text("8kHz", fontSize = 9.sp, color = Color(0xFF555555))
            }
        }
    }
}

@Composable
fun SpectrumBar(heightPct: Float, index: Int) {
    val barColor = when {
        index < 4 -> Brush.verticalGradient(listOf(Color(0xFF64B5F6), Color(0xFF1E88E5)))
        index < 9 -> Brush.verticalGradient(listOf(Color(0xFF81C784), Color(0xFF2E7D32)))
        index < 13 -> Brush.verticalGradient(listOf(Color(0xFFFFD54F), Color(0xFFF57F17)))
        else -> Brush.verticalGradient(listOf(Color(0xFFFF8A80), Color(0xFFD50000)))
    }

    Box(
        modifier = Modifier
            .width(14.dp)
            .fillMaxHeight(heightPct)
            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
            .background(barColor)
    )
}

@Composable
fun CompactSoundRow(sound: DetectedSound, isTop: Boolean, isEn: Boolean) {
    val pct = (sound.confidence * 100).toInt()

    val borderColor = when {
        sound.isCustom -> Color(0xFFAB47BC)
        sound.isCritical -> Color(0xFFE53935)
        isTop -> Color(0xFF00E676)
        else -> Color(0xFF2E2E2E)
    }

    val bgColor = when {
        sound.isCustom -> Color(0xFF221430)
        sound.isCritical -> Color(0xFF2E1212)
        isTop -> Color(0xFF122819)
        else -> Color(0xFF1A1A1A)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            if (sound.isCritical) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFF5252),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Column {
                Text(
                    text = sound.name,
                    fontWeight = if (isTop) FontWeight.ExtraBold else FontWeight.Medium,
                    fontSize = if (isTop) 14.sp else 12.sp,
                    color = if (isTop) Color.White else Color(0xFFCCCCCC)
                )
                if (isTop) {
                    Text(
                        text = when {
                            sound.isCustom && isEn -> "★ CUSTOM TRAINED SOUND"
                            sound.isCustom -> "★ SOM PERSONALIZADO"
                            isEn -> "TOP MATCH"
                            else -> "MAIS PROVÁVEL"
                        },
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (sound.isCustom) Color(0xFFCE93D8) else Color(0xFF69F0AE)
                    )
                }
            }
        }

        Text(
            text = "$pct%",
            fontWeight = FontWeight.Black,
            fontSize = if (isTop) 16.sp else 13.sp,
            color = if (isTop) Color(0xFF00E676) else Color(0xFFBBBBBB)
        )
    }
}

@Composable
fun HistoryDialog(
    history: List<SoundHistoryItem>,
    isEn: Boolean,
    onDismiss: () -> Unit,
    onClear: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF1C1C1C),
            border = BorderStroke(1.dp, Color(0xFF383838)),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.History, contentDescription = null, tint = Color(0xFF64B5F6))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEn) "Sound History" else "Histórico de Sons",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (history.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (isEn) "No sounds logged yet." else "Nenhum som catalogado ainda.",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(history) { item ->
                            HistoryRow(item)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (history.isNotEmpty()) {
                        OutlinedButton(
                            onClick = onClear,
                            border = BorderStroke(1.dp, Color(0xFFD32F2F)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isEn) "Clear" else "Limpar", color = Color(0xFFFF5252), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryRow(item: SoundHistoryItem) {
    val pct = (item.confidence * 100).toInt()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(0.5.dp, Color(0xFF2F2F2F)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF222222))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = item.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = "${item.timeFormatted} • ${item.rmsDb} dBFS",
                    fontSize = 10.sp,
                    color = Color(0xFFAAAAAA)
                )
            }

            Text(
                text = "$pct%",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (pct >= 50) Color(0xFF00E676) else Color(0xFFFFD54F)
            )
        }
    }
}

data class AlertVisualData(
    val emoji: String,
    val title: String,
    val subtitle: String,
    val color: Color
)

fun detectVisualAlert(sound: DetectedSound?, isEn: Boolean): AlertVisualData? {
    if (sound == null || sound.confidence < 0.28f) return null
    val name = sound.name.lowercase()

    return when {
        name.contains("dog") || name.contains("bark") || name.contains("cão") || name.contains("cachorro") || name.contains("latido") ->
            AlertVisualData("🐕", if (isEn) "BARKING DETECTED" else "LATIDO DETECTADO", sound.name, Color(0xFFFFB74D))

        name.contains("siren") || name.contains("police") || name.contains("ambulance") || name.contains("alarm") ||
            name.contains("sirene") || name.contains("polícia") || name.contains("ambulância") || name.contains("emergência") ->
            AlertVisualData("🚨", if (isEn) "EMERGENCY SIREN / ALARM" else "SIRENE DE EMERGÊNCIA", sound.name, Color(0xFFFF5252))

        name.contains("speech") || name.contains("voice") || name.contains("conversation") ||
            name.contains("fala") || name.contains("voz") || name.contains("conversa") ->
            AlertVisualData("🗣️", if (isEn) "HUMAN VOICE DETECTED" else "VOZ HUMANA DETECTADA", sound.name, Color(0xFF4DD0E1))

        name.contains("glass") || name.contains("shatter") || name.contains("crash") ||
            name.contains("vidro") || name.contains("quebrando") || name.contains("estalo") || name.contains("impacto") ->
            AlertVisualData("💥", if (isEn) "IMPACT / GLASS DETECTED" else "IMPACTO / VIDRO DETECTADO", sound.name, Color(0xFFFF4081))

        sound.isCustom ->
            AlertVisualData("★", if (isEn) "CUSTOM SOUND DETECTED" else "SOM ENSINADO DETECTADO", sound.name, Color(0xFFCE93D8))

        else -> null
    }
}

@Composable
fun VisualAlertBadge(alert: AlertVisualData) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, alert.color.copy(alpha = alphaAnim), RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = alert.color.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = alert.emoji,
                fontSize = 26.sp,
                modifier = Modifier
                    .alpha(alphaAnim)
                    .padding(end = 10.dp)
            )

            Column {
                Text(
                    text = alert.title,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    color = alert.color
                )
                Text(
                    text = alert.subtitle,
                    fontSize = 11.sp,
                    color = Color.White
                )
            }
        }
    }
}
