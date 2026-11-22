package com.example.audiomonitor.ui.help

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HelpScreen(language: String = "EN") {
    val isEn = language == "EN"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = if (isEn) "HEARDWORD ACOUSTIC GUIDE" else "MANUAL E GUIA ACÚSTICO",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color.White
            )
            Text(
                text = if (isEn) {
                    "Early-stage open-source acoustic monitor powered by YAMNet & GitHub community sound vaults."
                } else {
                    "Projeto open-source inicial de reconhecimento acústico com YAMNet e acervo comunitário no GitHub."
                },
                fontSize = 12.sp,
                color = Color(0xFFAAAAAA)
            )
        }

        item {
            HelpCard(
                title = if (isEn) "🎙️ How HeardWord Recognizes Audio" else "🎙️ Como o HeardWord Reconhece Áudio",
                description = if (isEn) {
                    "HeardWord captures 16 kHz mono audio in real time using a sliding 0.975-second window and computes a 16-band Radix-2 FFT spectrum alongside TensorFlow Lite inference.\n\n" +
                        "All neural processing runs locally on your phone with zero cloud audio streaming."
                } else {
                    "O HeardWord captura áudio a 16 kHz em tempo real usando janelas deslizantes de 0,975s, calculando um espectro FFT Radix-2 de 16 bandas junto com inferência TensorFlow Lite.\n\n" +
                        "Todo o processamento neural ocorre localmente no seu aparelho, sem enviar gravações para a nuvem."
                }
            )
        }

        item {
            HelpCard(
                title = if (isEn) "🌐 How Community GitHub Sound Sharing Works" else "🌐 Como Funciona o Acervo Comunitário no GitHub",
                description = if (isEn) {
                    "When you record a custom sound in the Studio tab, HeardWord does NOT store or share raw audio recordings.\n\n" +
                        "Instead, it extracts a compact 521-float neural signature (embedding vector). Using 'Submit Sound Pack to GitHub' or 'Sync Vault from GitHub', the community can grow a shared, privacy-safe repository of custom sounds!"
                } else {
                    "Quando você grava um som personalizado na aba Estúdio, o HeardWord NÃO salva nem envia o áudio bruto da sua voz ou casa.\n\n" +
                        "Ele extrai apenas um vetor matemático anônimo de 521 dimensões (assinatura neural). Com os botões 'Enviar Banco para o GitHub' e 'Sincronizar Banco do GitHub', qualquer pessoa pode contribuir e ampliar o catálogo!"
                }
            )
        }

        item {
            HelpCard(
                title = if (isEn) "🔊 What is dBFS (Digital Scale)?" else "🔊 O que é dBFS (Escala Digital)?",
                description = if (isEn) {
                    "dBFS stands for 'Decibels relative to Full Scale':\n\n" +
                        "• 0 dBFS: Maximum microphone ceiling before clipping.\n" +
                        "• -15 dBFS: Very loud sounds (clapping, shouting nearby).\n" +
                        "• -30 dBFS: Normal conversation or TV at 2 meters.\n" +
                        "• -50 dBFS: Quiet room at night."
                } else {
                    "dBFS significa 'Decibels relative to Full Scale'. É a escala oficial do áudio digital:\n\n" +
                        "• 0 dBFS: Limite máximo do microfone (acima disso o som distorce).\n" +
                        "• -15 dBFS: Som muito forte (gritos, palmas próximas).\n" +
                        "• -30 dBFS: Conversa normal ou TV ligada a 2 metros.\n" +
                        "• -50 dBFS: Sala silenciosa à noite."
                }
            )
        }

        item {
            HelpCard(
                title = if (isEn) "🧠 About the YAMNet Model" else "🧠 Como o YAMNet funciona?",
                description = if (isEn) {
                    "YAMNet is a pre-trained convolutional neural network created by Google and trained on AudioSet (521 official audio event classes).\n\n" +
                        "You can switch class labels instantly between English and Portuguese (PT-BR) using the 🌐 button in the top bar."
                } else {
                    "O YAMNet é uma rede neural convolucional desenvolvida pelo Google e treinada com 521 classes oficiais de eventos sonoros.\n\n" +
                        "Você pode alternar o idioma de todas as 521 classes e telas entre Inglês e Português a qualquer momento pelo botão 🌐 no topo."
                }
            )
        }
    }
}

@Composable
fun HelpCard(title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFF2C2C2C)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF191919))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF80CBC4))
            Spacer(modifier = Modifier.height(6.dp))
            Text(description, fontSize = 12.sp, color = Color(0xFFE0E0E0), lineHeight = 18.sp)
        }
    }
}
