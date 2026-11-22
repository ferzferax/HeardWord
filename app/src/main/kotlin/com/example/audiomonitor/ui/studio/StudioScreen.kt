package com.example.audiomonitor.ui.studio

import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audiomonitor.data.AudioMonitorRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun StudioScreen(repository: AudioMonitorRepository, language: String = "EN") {
    val isEn = language == "EN"
    var soundName by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf("") }
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var exportedJson by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var customSoundsList by remember { mutableStateOf(repository.classifier.getCustomSounds()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = if (isEn) "ACOUSTIC LEARNING STUDIO" else "ESTÚDIO DE APRENDIZADO",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color.White
            )
            Text(
                text = if (isEn) {
                    "Teach HeardWord new sounds by extracting anonymous 521-dim neural signatures."
                } else {
                    "Ensine novos sons ao HeardWord extraindo assinaturas acústicas neurais anônimas."
                },
                fontSize = 13.sp,
                color = Color(0xFFAAAAAA)
            )
        }

        // Record & Teach Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color(0xFF2C2C2C)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF171717))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isEn) "TEACH NEW SOUND" else "ENSINAR NOVO SOM",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = Color(0xFFCE93D8)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = soundName,
                        onValueChange = { soundName = it },
                        label = {
                            Text(
                                if (isEn) "Sound Name (e.g., Keys, Doorbell, Dog Bark)"
                                else "Nome do Som (ex: Chave, Campainha, Latido)"
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isRecording
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            if (soundName.isNotBlank() && !isRecording) {
                                isRecording = true
                                feedbackMessage = if (isEn) {
                                    "Listening... make the sound now!"
                                } else {
                                    "Ouvindo... produza o som agora!"
                                }
                                scope.launch {
                                    val embedding = recordSampleAndGetEmbedding(repository)
                                    if (embedding != null) {
                                        val cleanName = soundName.trim()
                                        repository.teachSound(cleanName, embedding)
                                        customSoundsList = repository.classifier.getCustomSounds()
                                        feedbackMessage = if (isEn) {
                                            "Sound '$cleanName' learned successfully!"
                                        } else {
                                            "Som '$cleanName' aprendido com sucesso!"
                                        }
                                        soundName = ""
                                    } else {
                                        feedbackMessage = if (isEn) {
                                            "Failed to capture microphone audio."
                                        } else {
                                            "Falha ao gravar áudio do microfone."
                                        }
                                    }
                                    isRecording = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecording) Color(0xFFE91E63) else Color(0xFF7B1FA2)
                        ),
                        enabled = soundName.isNotBlank() && !isRecording
                    ) {
                        if (isRecording) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(if (isEn) "Recording 2 seconds..." else "Gravando 2 segundos...")
                        } else {
                            Icon(Icons.Default.Mic, contentDescription = null)
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(if (isEn) "Record & Learn Sound" else "Gravar e Aprender Som")
                        }
                    }

                    if (feedbackMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = feedbackMessage,
                            fontSize = 13.sp,
                            color = if (feedbackMessage.contains("success", true) ||
                                feedbackMessage.contains("sucesso", true) ||
                                feedbackMessage.contains("imported", true) ||
                                feedbackMessage.contains("importado", true) ||
                                feedbackMessage.contains("copied", true) ||
                                feedbackMessage.contains("copiado", true)
                            ) Color(0xFF69F0AE) else Color(0xFFFFD54F)
                        )
                    }
                }
            }
        }

        // Export & Import JSON Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        exportedJson = repository.classifier.exportCustomSoundsJson()
                        showExportDialog = true
                    },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, Color(0xFF333333))
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(if (isEn) "Export Bank" else "Exportar Banco")
                }

                OutlinedButton(
                    onClick = {
                        importText = ""
                        showImportDialog = true
                    },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, Color(0xFF333333))
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(if (isEn) "Import Bank" else "Importar Banco")
                }
            }
        }

        // GitHub Community Sound Vault Integration Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF1E3A5F)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1726))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isEn) "GITHUB COMMUNITY SOUND VAULT" else "ACERVO COMUNITÁRIO NO GITHUB",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFF38BDF8)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isEn) {
                            "Share anonymous neural embeddings via Git/GitHub or download community-contributed sounds."
                        } else {
                            "Compartilhe assinaturas neurais anônimas via Git/GitHub ou baixe sons contribuídos pela comunidade."
                        },
                        fontSize = 12.sp,
                        color = Color(0xFFB0BEC5)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val json = repository.classifier.exportCustomSoundsJson()
                            clipboardManager.setText(AnnotatedString(json))
                            feedbackMessage = if (isEn) {
                                "Sound bank JSON copied! Paste it into the GitHub issue form."
                            } else {
                                "JSON do banco copiado! Cole no formulário do GitHub que abriu."
                            }
                            try {
                                val url = "${AudioMonitorRepository.GITHUB_REPO_URL}/issues/new" +
                                    "?template=sound_pack_submission.md" +
                                    "&title=%5BSound+Pack%5D+Community+Sound+Contribution"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F6FEB)),
                        enabled = customSoundsList.isNotEmpty()
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            if (isEn) "Submit Sound Pack to GitHub" else "Enviar Banco para o GitHub"
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            if (!isSyncing) {
                                isSyncing = true
                                feedbackMessage = if (isEn) "Syncing community vault..." else "Sincronizando acervo GitHub..."
                                scope.launch {
                                    val result = repository.syncFromGithubVault()
                                    result.onSuccess { count ->
                                        customSoundsList = repository.classifier.getCustomSounds()
                                        feedbackMessage = if (isEn) {
                                            "Synced $count sound(s) from GitHub Vault!"
                                        } else {
                                            "$count som(ns) sincronizado(s) do GitHub!"
                                        }
                                    }.onFailure { err ->
                                        feedbackMessage = if (isEn) {
                                            "Sync failed: ${err.message}"
                                        } else {
                                            "Falha na sincronização: ${err.message}"
                                        }
                                    }
                                    isSyncing = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                        enabled = !isSyncing
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(if (isEn) "Downloading..." else "Baixando...")
                        } else {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = if (isEn) "Sync Vault from GitHub" else "Sincronizar Banco do GitHub",
                                color = Color(0xFF38BDF8)
                            )
                        }
                    }
                }
            }
        }

        // Learned Sounds List
        item {
            Text(
                text = if (isEn) {
                    "SAVED SOUNDS ON THIS DEVICE (${customSoundsList.size})"
                } else {
                    "SONS SALVOS NESTE DISPOSITIVO (${customSoundsList.size})"
                },
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = Color(0xFF888888)
            )
        }

        if (customSoundsList.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF262626)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141414))
                ) {
                    Text(
                        text = if (isEn) "No custom sounds learned yet." else "Nenhum som personalizado aprendido ainda.",
                        modifier = Modifier.padding(16.dp),
                        color = Color(0xFF777777)
                    )
                }
            }
        } else {
            items(customSoundsList) { custom ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF3B2A52)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF20162B))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = custom.name,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE1BEE7),
                                fontSize = 16.sp
                            )
                            Text(
                                text = if (isEn) {
                                    "${custom.sampleCount} sample(s) | Neural profile active"
                                } else {
                                    "${custom.sampleCount} amostra(s) | Perfil neural ativo"
                                },
                                fontSize = 12.sp,
                                color = Color(0xFFB39DDB)
                            )
                        }

                        IconButton(onClick = {
                            repository.removeCustomSound(custom.name)
                            customSoundsList = repository.classifier.getCustomSounds()
                        }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = if (isEn) "Remove" else "Remover",
                                tint = Color(0xFFFF8A80)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text(if (isEn) "Export Sound Bank" else "Exportar Banco de Sons") },
            text = {
                Column {
                    Text(
                        if (isEn) {
                            "Copy the JSON below to share your learned acoustic signatures or open a PR on GitHub:"
                        } else {
                            "Copie o texto JSON abaixo para compartilhar seus sons ou enviar via GitHub:"
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = exportedJson.take(300) + if (exportedJson.length > 300) "..." else "",
                        fontSize = 11.sp,
                        color = Color(0xFFAAAAAA)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    clipboardManager.setText(AnnotatedString(exportedJson))
                    showExportDialog = false
                    feedbackMessage = if (isEn) {
                        "Sound bank copied to clipboard!"
                    } else {
                        "Banco copiado para a área de transferência!"
                    }
                }) {
                    Text(if (isEn) "Copy All" else "Copiar Tudo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text(if (isEn) "Close" else "Fechar")
                }
            }
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text(if (isEn) "Import Sound Bank" else "Importar Banco de Sons") },
            text = {
                Column {
                    Text(
                        if (isEn) {
                            "Paste a HeardWord JSON sound pack below:"
                        } else {
                            "Cole o JSON com os sons enviados pela comunidade:"
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        modifier = Modifier.fillMaxWidth().height(140.dp)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (importText.isNotBlank()) {
                        val added = repository.classifier.importCustomSoundsJson(importText.trim())
                        repository.refreshCustomSoundsCount()
                        customSoundsList = repository.classifier.getCustomSounds()
                        feedbackMessage = if (isEn) {
                            "$added sound(s) imported successfully!"
                        } else {
                            "$added som(ns) importado(s) com sucesso!"
                        }
                        showImportDialog = false
                    }
                }) {
                    Text(if (isEn) "Import" else "Importar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text(if (isEn) "Cancel" else "Cancelar")
                }
            }
        )
    }
}

private suspend fun recordSampleAndGetEmbedding(repository: AudioMonitorRepository): FloatArray? =
    withContext(Dispatchers.IO) {
        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = 16000 * 2 // 2 seconds
        val targetLen = 15600

        var recorder: AudioRecord? = null
        try {
            val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val sources = listOf(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.MIC
            )
            for (source in sources) {
                val candidate = AudioRecord(
                    source,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    maxOf(minBuf, bufferSize * 2)
                )
                if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                    recorder = candidate
                    break
                }
                candidate.release()
            }

            val activeRecorder = recorder ?: return@withContext null

            val pcm = ShortArray(bufferSize)
            activeRecorder.startRecording()
            var readTotal = 0
            while (readTotal < bufferSize) {
                val r = activeRecorder.read(pcm, readTotal, bufferSize - readTotal)
                if (r > 0) readTotal += r else break
            }

            if (readTotal < targetLen) return@withContext null

            // Select the 15600-sample (~1s) window with highest RMS energy inside the 2s recording
            var bestStart = 0
            var bestEnergy = -1.0
            val step = 400
            var start = 0
            while (start + targetLen <= readTotal) {
                var energy = 0.0
                for (i in start until start + targetLen) {
                    val v = pcm[i].toDouble()
                    energy += v * v
                }
                if (energy > bestEnergy) {
                    bestEnergy = energy
                    bestStart = start
                }
                start += step
            }

            val floats = FloatArray(targetLen)
            var mean = 0.0
            for (i in 0 until targetLen) {
                val s = pcm[bestStart + i] / 32768.0f
                floats[i] = s
                mean += s.toDouble()
            }
            val dc = (mean / targetLen).toFloat()
            for (i in 0 until targetLen) {
                floats[i] = (floats[i] - dc).coerceIn(-1.0f, 1.0f)
            }

            val (_, embedding) = repository.classifier.classify(floats, isStudioCapture = true)
            embedding
        } catch (_: Exception) {
            null
        } finally {
            try {
                recorder?.stop()
                recorder?.release()
            } catch (_: Exception) {
            }
        }
    }
