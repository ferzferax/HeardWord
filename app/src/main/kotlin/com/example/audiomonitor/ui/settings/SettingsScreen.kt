package com.example.audiomonitor.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audiomonitor.data.AudioMonitorRepository
import com.example.audiomonitor.data.MonitoringMode
import com.example.audiomonitor.data.MonitoringState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(state: MonitoringState, repository: AudioMonitorRepository) {
    val isEn = state.language == "EN"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = if (isEn) "MONITOR SETTINGS" else "CONFIGURAÇÕES DO MONITOR",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color.White
            )
            Text(
                text = if (isEn) {
                    "Adjust language, acoustic sensitivity profiles, and alert triggers."
                } else {
                    "Ajuste idioma, perfis de sensibilidade e alertas do sistema."
                },
                fontSize = 13.sp,
                color = Color(0xFFAAAAAA)
            )
        }

        // Language Selection Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1F1F))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isEn) "APP & CLASSIFIER LANGUAGE" else "IDIOMA DO APP & CLASSES IA",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = Color(0xFF38BDF8)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = state.language == "EN",
                            onClick = { repository.setLanguage("EN") },
                            label = { Text("English (Default)") }
                        )
                        FilterChip(
                            selected = state.language == "PT",
                            onClick = { repository.setLanguage("PT") },
                            label = { Text("Português (BR)") }
                        )
                    }
                }
            }
        }

        // Operation Profile
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1F1F))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isEn) "OPERATION PROFILE" else "PERFIL DE OPERAÇÃO",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = Color(0xFF81D4FA)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MonitoringMode.values().forEach { mode ->
                            FilterChip(
                                selected = state.mode == mode,
                                onClick = { repository.setMode(mode) },
                                label = { Text(mode.label(state.language)) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (state.mode) {
                            MonitoringMode.ADAPTATIVO -> if (isEn) {
                                "Balanced threshold (-36 dBFS) for everyday rooms and ambient monitoring."
                            } else {
                                "Equilíbrio adaptativo (-36 dBFS) para uso diário em ambientes internos."
                            }
                            MonitoringMode.NOTURNO -> if (isEn) {
                                "Ultra-high sensitivity (-48 dBFS) to catch whispers, creaks, and footsteps at night."
                            } else {
                                "Sensibilidade altíssima (-48 dBFS) para pegar murmúrios e passos na madrugada."
                            }
                            MonitoringMode.NORMAL -> if (isEn) {
                                "Standard sensitivity (-32 dBFS) for home and office environments."
                            } else {
                                "Equilíbrio padrão para ambientes residenciais (-32 dBFS)."
                            }
                            MonitoringMode.RUIDOSO -> if (isEn) {
                                "Filters background chatter and focuses on impacts, glass, and sirens (-24 dBFS)."
                            } else {
                                "Filtra conversas e foca em impactos, vidros e sirenes (-24 dBFS)."
                            }
                        },
                        fontSize = 12.sp,
                        color = Color(0xFFCCCCCC)
                    )
                }
            }
        }

        // Manual Threshold dBFS
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1F1F))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isEn) "VOLUME SENSITIVITY" else "SENSIBILIDADE DE VOLUME",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = Color(0xFFFFD54F)
                        )
                        Text("${state.thresholdDb.toInt()} dBFS", fontWeight = FontWeight.Bold, color = Color(0xFFFFD54F))
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Slider(
                        value = state.thresholdDb,
                        onValueChange = { repository.setThreshold((it * 10).toInt() / 10.0f) },
                        valueRange = -60.0f..-15.0f,
                        steps = 44
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            if (isEn) "-60 dBFS (Ultra-sensitive)" else "-60 dBFS (Ultra-sensível)",
                            fontSize = 11.sp,
                            color = Color(0xFF888888)
                        )
                        Text(
                            if (isEn) "-15 dBFS (Loud sounds only)" else "-15 dBFS (Apenas sons altos)",
                            fontSize = 11.sp,
                            color = Color(0xFF888888)
                        )
                    }
                }
            }
        }

        // Vibration Alert
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1F1F))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEn) "Vibrate on Critical Sounds" else "Vibrar em Sons Críticos",
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = if (isEn) {
                                "Triggers haptic feedback when alarms, glass shatter, sirens, or custom sounds are detected."
                            } else {
                                "Dispara pulso tátil ao detectar alarmes, vidros, sirenes ou sons ensinados."
                            },
                            fontSize = 12.sp,
                            color = Color(0xFFAAAAAA)
                        )
                    }
                    Switch(
                        checked = state.vibrateOnAlert,
                        onCheckedChange = { repository.setVibrate(it) }
                    )
                }
            }
        }
    }
}
