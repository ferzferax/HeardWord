package com.example.audiomonitor.ml

import android.content.Context
import android.util.Log
import com.example.audiomonitor.data.CustomSound
import com.example.audiomonitor.data.DetectedSound
import org.json.JSONArray
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.sqrt

class YamnetClassifier(private val context: Context) {

    private var interpreter: Interpreter? = null
    private val classNamesEn = mutableListOf<String>()
    private val classNamesPt = mutableListOf<String>()
    private val customSounds = mutableListOf<CustomSound>()
    private val customSoundsFile = File(context.filesDir, "custom_sounds.json")

    @Volatile
    var language: String = "EN"

    private val emaScores = FloatArray(521)
    private var silenceClassIndex: Int = 494
    private val criticalIndices = HashSet<Int>()

    // Índices base-0 do AudioSet/YAMNet propensos a alucinação por ruído térmico/ambiente
    private val blockedClassIndices: Set<Int> = buildSet {
        add(77) // Purr
        add(79) // Hiss
        for (i in 81..105) add(i)  // Livestock, farm animals, wild animals, roar
        for (i in 118..120) add(i) // Rodents, mouse, patter
        for (i in 127..131) add(i) // Frog, Croak, Snake, Rattle, Whale
        for (i in 205..209) add(i) // Bagpipes, Didgeridoo, Shofar, Theremin, Singing bowl
        add(279) // Wind noise (microphone)
        add(290) // Steam
        add(451) // Sonar
        add(452) // Arrow
        for (i in 494..516) add(i) // Silence, test tones, room tags, static, white/pink noise
    }

    private val criticalKeywords = setOf(
        // English
        "siren", "alarm", "glass", "shatter", "gunshot", "explosion",
        "bark", "dog", "horn", "scream", "cry", "crash", "emergency",
        "police", "ambulance", "fire engine", "smoke detector",
        // Portuguese
        "sirene", "alarme", "vidro", "quebrando", "tiro", "explosão",
        "latido", "cão", "cachorro", "buzina", "grito", "choro", "madeira",
        "batida", "impacto", "veículo de emergência", "polícia", "ambulância", "bombeiros"
    )

    init {
        loadModel()
        loadClasses()
        loadCustomSounds()
    }

    private fun loadModel() {
        try {
            val assetFd = context.assets.openFd("yamnet.tflite")
            val inputStream = FileInputStream(assetFd.fileDescriptor)
            val fileChannel = inputStream.channel
            val modelBuffer = fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                assetFd.startOffset,
                assetFd.declaredLength
            )
            val options = Interpreter.Options().apply {
                setNumThreads(2)
            }
            interpreter = Interpreter(modelBuffer, options)
            Log.d("YamnetClassifier", "YAMNet loaded successfully via mmap.")
        } catch (e: Exception) {
            Log.e("YamnetClassifier", "Failed to load YAMNet model", e)
        }
    }

    private fun loadClasses() {
        try {
            val enJson = context.assets.open("yamnet_classes_en.json").bufferedReader().use { it.readText() }
            val enArr = JSONArray(enJson)
            classNamesEn.clear()
            for (i in 0 until enArr.length()) {
                classNamesEn.add(enArr.getString(i))
            }
        } catch (e: Exception) {
            Log.e("YamnetClassifier", "Error loading EN classes", e)
            classNamesEn.clear()
            for (i in 0 until 521) classNamesEn.add("Sound #$i")
        }

        try {
            val ptJson = context.assets.open("yamnet_classes_pt.json").bufferedReader().use { it.readText() }
            val ptArr = JSONArray(ptJson)
            classNamesPt.clear()
            for (i in 0 until ptArr.length()) {
                classNamesPt.add(ptArr.getString(i))
            }
        } catch (e: Exception) {
            Log.e("YamnetClassifier", "Error loading PT classes", e)
            classNamesPt.clear()
            for (i in 0 until 521) classNamesPt.add("Som #$i")
        }

        silenceClassIndex = classNamesEn.indexOfFirst { it.equals("Silence", ignoreCase = true) }
            .let { if (it >= 0) it else 494 }

        criticalIndices.clear()
        for (i in 0 until 521) {
            if (i in blockedClassIndices) continue
            val enName = classNamesEn.getOrNull(i) ?: ""
            val ptName = classNamesPt.getOrNull(i) ?: ""
            if (criticalKeywords.any { kw ->
                    enName.contains(kw, ignoreCase = true) || ptName.contains(kw, ignoreCase = true)
                }
            ) {
                criticalIndices.add(i)
            }
        }
    }

    fun resetSmoothing() {
        emaScores.fill(0.0f)
    }

    private fun decayEma(factor: Float = 0.4f) {
        for (i in emaScores.indices) {
            emaScores[i] *= factor
        }
    }

    fun classify(
        samples: FloatArray,
        minConfidence: Float = 0.22f,
        rmsDb: Float = 0.0f,
        thresholdDb: Float = -60.0f,
        isStudioCapture: Boolean = false
    ): Pair<List<DetectedSound>, FloatArray?> {
        val interp = interpreter ?: run {
            Log.w("YamnetClassifier", "TFLite interpreter is null")
            return Pair(emptyList(), null)
        }

        val inputLen = 15600
        val inputBuffer = ByteBuffer.allocateDirect(inputLen * 4).apply {
            order(ByteOrder.nativeOrder())
            rewind()
            for (i in 0 until inputLen) {
                val s = if (i < samples.size) samples[i] else 0.0f
                putFloat(s)
            }
        }

        val outputScores = Array(1) { FloatArray(521) }

        try {
            interp.run(inputBuffer, outputScores)
        } catch (e: Exception) {
            Log.e("YamnetClassifier", "YAMNet inference failure", e)
            return Pair(emptyList(), null)
        }

        val rawScores = outputScores[0]
        if (isStudioCapture) {
            return Pair(emptyList(), rawScores)
        }

        val results = mutableListOf<DetectedSound>()

        // 1. Custom learned acoustic profiles (521-dim cosine similarity)
        if (rmsDb >= thresholdDb - 4.0f) {
            synchronized(customSounds) {
                for (custom in customSounds) {
                    val sim = cosineSimilarity(rawScores, custom.embedding)
                    if (sim >= 0.76f) {
                        results.add(
                            DetectedSound(
                                name = custom.name,
                                confidence = sim,
                                isCritical = true,
                                isCustom = true
                            )
                        )
                    }
                }
            }
        }

        // 2. Acoustic Noise Gate: se abaixo do limiar de dB configurado, decair EMA e silenciar palpites
        if (rmsDb < thresholdDb) {
            decayEma(0.4f)
            return Pair(results.sortedByDescending { it.confidence }, rawScores)
        }

        // 3. Neural Silence Gate: se YAMNet vê silêncio dominante e nenhum som crítico/custom forte surgiu
        val silenceScore = if (silenceClassIndex in rawScores.indices) rawScores[silenceClassIndex] else 0.0f
        val hasStrongCriticalRaw = criticalIndices.any { idx -> rawScores[idx] >= 0.35f }
        if (silenceScore >= 0.45f && !hasStrongCriticalRaw && results.isEmpty()) {
            decayEma(0.4f)
            return Pair(emptyList(), rawScores)
        }

        // 4. Suavização temporal (EMA) com resposta rápida para eventos críticos impulsivos
        for (i in 0 until 521) {
            if (i in blockedClassIndices) {
                emaScores[i] = 0.0f
            } else {
                val smoothed = 0.55f * rawScores[i] + 0.45f * emaScores[i]
                emaScores[i] = if (i in criticalIndices) {
                    maxOf(rawScores[i] * 0.85f, smoothed)
                } else {
                    smoothed
                }
            }
        }

        // 5. Top YAMNet classes filtradas no idioma ativo
        val activeLabels = if (language == "PT") classNamesPt else classNamesEn
        val indexed = emaScores.indices
            .filter { it !in blockedClassIndices }
            .sortedByDescending { emaScores[it] }
            .take(6)

        for (idx in indexed) {
            val score = emaScores[idx]
            val isCrit = idx in criticalIndices
            val requiredConf = if (isCrit) minOf(minConfidence, 0.18f) else minConfidence
            if (idx < activeLabels.size && score >= requiredConf) {
                val label = activeLabels[idx]
                results.add(
                    DetectedSound(
                        name = label,
                        confidence = score,
                        isCritical = isCrit,
                        isCustom = false
                    )
                )
            }
        }

        val sortedDesc = results.distinctBy { it.name }.sortedByDescending { it.confidence }
        return Pair(sortedDesc, rawScores)
    }

    fun addCustomSound(name: String, embedding: FloatArray, sampleCountOverride: Int? = null) {
        val list = embedding.toList()
        synchronized(customSounds) {
            val existingIdx = customSounds.indexOfFirst { it.name.equals(name, ignoreCase = true) }
            if (existingIdx >= 0) {
                val old = customSounds[existingIdx]
                val merged = old.embedding.zip(list) { a, b -> (a + b) / 2.0f }
                customSounds[existingIdx] = old.copy(
                    embedding = merged,
                    sampleCount = sampleCountOverride ?: (old.sampleCount + 1)
                )
            } else {
                customSounds.add(
                    CustomSound(
                        name = name,
                        embedding = list,
                        sampleCount = sampleCountOverride ?: 1
                    )
                )
            }
            saveCustomSounds()
        }
    }

    fun getCustomSounds(): List<CustomSound> = synchronized(customSounds) { customSounds.toList() }

    fun removeCustomSound(name: String) {
        synchronized(customSounds) {
            customSounds.removeAll { it.name.equals(name, ignoreCase = true) }
            saveCustomSounds()
        }
    }

    fun exportCustomSoundsJson(): String {
        val root = JSONObject()
        root.put("format", "heardword-sound-pack/v1")
        val arr = JSONArray()
        synchronized(customSounds) {
            for (cs in customSounds) {
                val obj = JSONObject()
                obj.put("name", cs.name)
                obj.put("samples", cs.sampleCount)
                val embArr = JSONArray()
                for (v in cs.embedding) embArr.put(v.toDouble())
                obj.put("embedding", embArr)
                arr.put(obj)
            }
        }
        root.put("custom_sounds", arr)
        return root.toString(2)
    }

    fun importCustomSoundsJson(jsonStr: String, preserveSamples: Boolean = false): Int {
        return try {
            val root = JSONObject(jsonStr)
            val arr = root.getJSONArray("custom_sounds")
            var count = 0
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val name = obj.getString("name").trim()
                if (name.isEmpty()) continue
                val samplesCount = if (preserveSamples && obj.has("samples")) obj.getInt("samples") else null
                val embArr = obj.getJSONArray("embedding")
                if (embArr.length() != 521) continue
                val emb = FloatArray(embArr.length()) { idx -> embArr.getDouble(idx).toFloat() }
                addCustomSound(name, emb, samplesCount)
                count++
            }
            count
        } catch (e: Exception) {
            Log.e("YamnetClassifier", "Error importing custom sounds", e)
            0
        }
    }

    private fun loadCustomSounds() {
        if (!customSoundsFile.exists()) return
        try {
            val jsonStr = customSoundsFile.readText()
            importCustomSoundsJson(jsonStr, preserveSamples = true)
        } catch (e: Exception) {
            Log.e("YamnetClassifier", "Error loading saved sounds", e)
        }
    }

    private fun saveCustomSounds() {
        try {
            customSoundsFile.writeText(exportCustomSoundsJson())
        } catch (e: Exception) {
            Log.e("YamnetClassifier", "Error saving sounds", e)
        }
    }

    private fun cosineSimilarity(a: FloatArray, b: List<Float>): Float {
        if (a.size != b.size || a.isEmpty()) return 0.0f
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            val av = a[i].toDouble()
            val bv = b[i].toDouble()
            dot += av * bv
            normA += av * av
            normB += bv * bv
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom > 0) (dot / denom).toFloat() else 0.0f
    }
}
