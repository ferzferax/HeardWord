package com.example.audiomonitor.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.audiomonitor.MainActivity
import com.example.audiomonitor.R
import com.example.audiomonitor.audio.AudioProcessor
import com.example.audiomonitor.data.AudioMonitorRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AudioMonitorService : Service() {

    private val serviceJob = Job()
    private val scope = CoroutineScope(Dispatchers.Default + serviceJob)
    private var isRecording = false

    private lateinit var repository: AudioMonitorRepository
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        repository = AudioMonitorRepository.getInstance(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.d("AudioMonitorService", "onStartCommand action=$action")

        if (action == ACTION_STOP) {
            stopMonitoring()
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundServiceNotification()
        startMonitoring()
        return START_STICKY
    }

    private fun isEn(): Boolean = repository.state.value.language == "EN"

    private fun startForegroundServiceNotification() {
        val msg = if (isEn()) "Starting acoustic monitoring..." else "Iniciando monitoramento acústico..."
        val notification = buildNotification(msg, "- dBFS")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, AudioMonitorService::class.java).apply { action = ACTION_STOP }
        val pendingStop = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notifTitle = if (isEn()) "HeardWord Listening" else "HeardWord Ativo"
        val stopLabel = if (isEn()) "Stop" else "Parar"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(notifTitle)
            .setContentText("$title | $content")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingOpen)
            .addAction(0, stopLabel, pendingStop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createAudioRecord(sampleRate: Int, channelConfig: Int, audioFormat: Int, bufferSize: Int): AudioRecord? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val rec = AudioRecord(
                    MediaRecorder.AudioSource.UNPROCESSED,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
                if (rec.state == AudioRecord.STATE_INITIALIZED) {
                    return rec
                }
                rec.release()
            } catch (e: Exception) {
                Log.w("AudioMonitorService", "UNPROCESSED failed, falling back to MIC", e)
            }
        }

        return try {
            val rec = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            if (rec.state == AudioRecord.STATE_INITIALIZED) rec else null
        } catch (e: Exception) {
            Log.e("AudioMonitorService", "Failed to create AudioRecord", e)
            null
        }
    }

    private fun startMonitoring() {
        if (isRecording) return
        isRecording = true
        repository.setRunning(true, if (isEn()) "Listening continuously" else "Monitorando áudio contínuo")

        scope.launch {
            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = maxOf(minBuf, sampleRate * 2)

            val recorder = createAudioRecord(sampleRate, channelConfig, audioFormat, bufferSize)
            if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                repository.setRunning(
                    false,
                    if (isEn()) "Microphone unavailable or permission missing" else "Microfone inacessível ou sem permissão"
                )
                return@launch
            }

            try {
                recorder.startRecording()
                val windowSize = 15600
                val stepSize = 8000
                val ringBuffer = ShortArray(windowSize)
                var filled = 0

                val readChunk = ShortArray(1024)

                while (isActive && isRecording) {
                    val read = recorder.read(readChunk, 0, readChunk.size)
                    if (read > 0) {
                        for (i in 0 until read) {
                            ringBuffer[filled++] = readChunk[i]
                            if (filled >= windowSize) {
                                processWindow(ringBuffer, sampleRate)
                                System.arraycopy(ringBuffer, stepSize, ringBuffer, 0, windowSize - stepSize)
                                filled = windowSize - stepSize
                            }
                        }
                    } else {
                        delay(10)
                    }
                }
            } catch (e: Exception) {
                Log.e("AudioMonitorService", "Recording error", e)
                repository.setRunning(false, "Error: ${e.message}")
            } finally {
                try {
                    recorder.stop()
                    recorder.release()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun processWindow(pcm: ShortArray, sampleRate: Int) {
        val (metrics, floats) = AudioProcessor.computeMetrics(pcm, sampleRate)
        val (sounds, _) = repository.classifier.classify(floats)
        repository.updateMetrics(metrics, sounds)

        val fallback = if (isEn()) "Ambient" else "Ambiente"
        val topName = sounds.firstOrNull()?.name ?: fallback
        val topConf = ((sounds.firstOrNull()?.confidence ?: 0.0f) * 100).toInt()
        val notif = buildNotification(topName, "${metrics.rmsDb} dBFS ($topConf%)")
        notificationManager.notify(NOTIFICATION_ID, notif)
    }

    private fun stopMonitoring() {
        isRecording = false
        repository.setRunning(false, if (isEn()) "Monitor stopped" else "Monitor parado")
    }

    override fun onDestroy() {
        stopMonitoring()
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Acoustic Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Continuous acoustic recognition service for HeardWord"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "heardword_monitor_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.ferzferax.heardword.action.START"
        const val ACTION_STOP = "com.ferzferax.heardword.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, AudioMonitorService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AudioMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
