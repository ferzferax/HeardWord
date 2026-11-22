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
    }

    fun classify(samples: FloatArray, minConfidence: Float = 0.08f): Pair<List<DetectedSound>, FloatArray?> {
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

        val scores = outputScores[0]
        val results = mutableListOf<DetectedSound>()

        // 1. Custom learned acoustic profiles (521-dim cosine similarity)
        synchronized(customSounds) {
            for (custom in customSounds) {
                val sim = cosineSimilarity(scores, custom.embedding)
                if (sim >= 0.70f) {
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

        // 2. Top YAMNet classes in active language
        val activeLabels = if (language == "PT") classNamesPt else classNamesEn
        val indexed = scores.indices.map { it to scores[it] }
            .sortedByDescending { it.second }
            .take(6)

        for ((idx, score) in indexed) {
            if (idx < activeLabels.size && score >= minConfidence) {
                val label = activeLabels[idx]
                if ((label.equals("Silence", ignoreCase = true) || label.equals("Silêncio", ignoreCase = true)) && results.isNotEmpty()) {
                    continue
                }
                val isCrit = criticalKeywords.any { label.contains(it, ignoreCase = true) }
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
        return Pair(sortedDesc, scores)
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
