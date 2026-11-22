package com.example.audiomonitor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.audiomonitor.data.AudioMonitorRepository
import com.example.audiomonitor.service.AudioMonitorService
import com.example.audiomonitor.ui.help.HelpScreen
import com.example.audiomonitor.ui.monitor.MonitorScreen
import com.example.audiomonitor.ui.settings.SettingsScreen
import com.example.audiomonitor.ui.studio.StudioScreen
import com.example.audiomonitor.ui.theme.AudioMonitorTheme

class MainActivity : ComponentActivity() {

    private lateinit var repository: AudioMonitorRepository

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val isEn = repository.state.value.language == "EN"
        if (audioGranted) {
            repository.setRunning(
                false,
                if (isEn) "Permission granted. Ready to start." else "Permissão concedida. Pronto para iniciar."
            )
        } else {
            repository.setRunning(
                false,
                if (isEn) "Microphone permission is required." else "Permissão de microfone necessária."
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = AudioMonitorRepository.getInstance(this)

        checkAndRequestPermissions()
        enableEdgeToEdge()

        setContent {
            AudioMonitorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainApp(
                        repository = repository,
                        onToggleService = {
                            val isRunning = repository.state.value.isRunning
                            if (isRunning) {
                                AudioMonitorService.stop(this)
                            } else {
                                AudioMonitorService.start(this)
                            }
                        }
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}

@Composable
fun MainApp(
    repository: AudioMonitorRepository,
    onToggleService: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val state by repository.state.collectAsState()
    val isEn = state.language == "EN"

    Scaffold(
        topBar = {
            Surface(
                color = Color(0xFF121622),
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "HeardWord",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0x2238BDF8),
                                    border = BorderStroke(1.dp, Color(0x5538BDF8))
                                ) {
                                    Text(
                                        text = if (isEn) "Early Alpha" else "Versão Inicial",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF38BDF8),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (isEn) "Neural Acoustic Monitor • YAMNet" else "Monitor Acústico Neural • YAMNet",
                                fontSize = 11.sp,
                                color = Color(0xFF8B949E)
                            )
                        }
                    }

                    // Instant Language Toggle Button (EN <-> PT)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF1C2333),
                        border = BorderStroke(1.dp, Color(0xFF30363D)),
                        modifier = Modifier.clickable { repository.toggleLanguage() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "Language",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isEn) "EN → PT" else "PT → EN",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF161B22)) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.GraphicEq, contentDescription = "Monitor") },
                    label = { Text("Monitor") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Mic, contentDescription = "Studio") },
                    label = { Text(if (isEn) "Studio" else "Estúdio") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Modes") },
                    label = { Text(if (isEn) "Modes" else "Modos") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Guide") },
                    label = { Text(if (isEn) "Guide" else "Guia") }
                )
            }
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = Color(0xFF0D1117)
        ) {
            when (selectedTab) {
                0 -> MonitorScreen(
                    state = state,
                    onToggleService = onToggleService,
                    onClearHistory = { repository.clearHistory() }
                )
                1 -> StudioScreen(repository = repository, language = state.language)
                2 -> SettingsScreen(state = state, repository = repository)
                3 -> HelpScreen(language = state.language)
            }
        }
    }
}
