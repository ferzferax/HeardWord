package com.example.audiomonitor.audio

import com.example.audiomonitor.data.AcousticMetrics
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object AudioProcessor {

    private const val EPSILON = 1e-6f
    private const val FFT_SIZE = 512

    fun computeMetrics(pcm: ShortArray, sampleRate: Int = 16000): Pair<AcousticMetrics, FloatArray> {
        val n = pcm.size
        if (n == 0) return Pair(AcousticMetrics(), FloatArray(0))

        val rawFloats = FloatArray(n)
        var sum = 0.0
        for (i in 0 until n) {
            val s = pcm[i].toFloat() / 32768.0f
            rawFloats[i] = s
            sum += s.toDouble()
        }
        val dcOffset = (sum / n).toFloat()

        val floats = FloatArray(n)
        var sumSquares = 0.0
        var maxPeak = 0.0f

        // Remoção de DC offset sem amplificar ruído térmico em ambientes silenciosos
        for (i in 0 until n) {
            val s = (rawFloats[i] - dcOffset).coerceIn(-1.0f, 1.0f)
            floats[i] = s
            val absVal = abs(s)
            if (absVal > maxPeak) maxPeak = absVal
            sumSquares += (s * s).toDouble()
        }

        val rawRms = sqrt(sumSquares / n).toFloat()
        val rmsDb = 20.0f * log10(max(rawRms, EPSILON))
        val peakDb = 20.0f * log10(max(maxPeak, EPSILON))
        val crestFactor = if (rawRms > EPSILON) maxPeak / rawRms else 1.0f

        // Ganho suave apenas acima do piso de ruído térmico (~ -35 dBFS = 0.018f RMS)
        val conditioned = FloatArray(n)
        val gain = if (rawRms in 0.018f..0.12f) 1.35f else 1.0f
        for (i in 0 until n) {
            conditioned[i] = (floats[i] * gain).coerceIn(-1.0f, 1.0f)
        }

        // Análise de FFT real para espectro de 16 bandas e métricas espectrais
        val (dominantHz, centroidHz, subGravePct, medioPct, agudoPct, spectrum) = analyzeSpectrum(floats, sampleRate)

        val metrics = AcousticMetrics(
            rmsDb = (rmsDb * 10).toInt() / 10.0f,
            peakDb = (peakDb * 10).toInt() / 10.0f,
            dominantHz = dominantHz,
            centroidHz = centroidHz,
            bandSubGravePct = subGravePct,
            bandMedioPct = medioPct,
            bandAgudoPct = agudoPct,
            crestFactor = (crestFactor * 100).toInt() / 100.0f,
            spectrum = spectrum
        )

        return Pair(metrics, conditioned)
    }

    private data class SpectrumAnalysis(
        val dominantHz: Float,
        val centroidHz: Float,
        val subGravePct: Int,
        val medioPct: Int,
        val agudoPct: Int,
        val spectrum: List<Float>
    )

    private fun analyzeSpectrum(samples: FloatArray, sampleRate: Int): SpectrumAnalysis {
        val real = FloatArray(FFT_SIZE)
        val imag = FloatArray(FFT_SIZE)

        // Janelamento Hann nos primeiros FFT_SIZE amostras (ou amostras centrais)
        val offset = max(0, (samples.size - FFT_SIZE) / 2)
        for (i in 0 until FFT_SIZE) {
            val idx = offset + i
            val s = if (idx < samples.size) samples[idx] else 0.0f
            val window = 0.5f * (1.0f - cos(2.0 * PI * i / (FFT_SIZE - 1)).toFloat())
            real[i] = s * window
            imag[i] = 0.0f
        }

        // Executar FFT Radix-2
        fftRadix2(real, imag)

        // Calcular magnitudes dos 256 bins de frequência (0 a Nyquist = 8000 Hz)
        val numBins = FFT_SIZE / 2
        val binWidth = (sampleRate / 2.0f) / numBins // 31.25 Hz por bin
        val magnitudes = FloatArray(numBins)

        var totalEnergy = 0.0
        var maxMag = 0.0f
        var dominantBin = 0
        var weightedFreqSum = 0.0

        var subGraveEnergy = 0.0
        var medioEnergy = 0.0
        var agudoEnergy = 0.0

        for (k in 1 until numBins) {
            val mag = sqrt(real[k] * real[k] + imag[k] * imag[k])
            magnitudes[k] = mag
            val freq = k * binWidth
            val energy = mag * mag

            totalEnergy += energy
            weightedFreqSum += energy * freq

            if (mag > maxMag) {
                maxMag = mag
                dominantBin = k
            }

            when {
                freq < 250.0f -> subGraveEnergy += energy
                freq < 2000.0f -> medioEnergy += energy
                else -> agudoEnergy += energy
            }
        }

        val dominantHz = dominantBin * binWidth
        val centroidHz = if (totalEnergy > 0) (weightedFreqSum / totalEnergy).toFloat() else 0.0f
        val subGravePct = if (totalEnergy > 0) ((subGraveEnergy / totalEnergy) * 100).toInt() else 0
        val medioPct = if (totalEnergy > 0) ((medioEnergy / totalEnergy) * 100).toInt() else 0
        val agudoPct = if (totalEnergy > 0) ((agudoEnergy / totalEnergy) * 100).toInt() else 0

        // 16 bandas de frequência distribuídas logaritmicamente (20Hz a 8000Hz)
        val binRanges = arrayOf(
            1..2,     // ~31 - 62 Hz
            2..4,     // ~62 - 125 Hz
            4..6,     // ~125 - 187 Hz
            6..9,     // ~187 - 281 Hz
            9..14,    // ~281 - 437 Hz
            14..21,   // ~437 - 656 Hz
            21..31,   // ~656 - 968 Hz
            31..46,   // ~968 - 1437 Hz
            46..66,   // ~1437 - 2062 Hz
            66..93,   // ~2062 - 2906 Hz
            93..128,  // ~2906 - 4000 Hz
            128..166, // ~4000 - 5187 Hz
            166..200, // ~5187 - 6250 Hz
            200..225, // ~6250 - 7031 Hz
            225..242, // ~7031 - 7562 Hz
            242..255  // ~7562 - 8000 Hz
        )

        val spectrumBars = mutableListOf<Float>()
        for (range in binRanges) {
            var sum = 0.0f
            var count = 0
            for (b in range) {
                if (b < numBins) {
                    sum += magnitudes[b]
                    count++
                }
            }
            val avg = if (count > 0) sum / count else 0.0f
            // Normalizar para visualização em barras (escala perceptiva logarítmica / dB)
            // Silêncio ~ 0.001f, som forte ~ 0.5f - 1.0f
            val dbVal = 20.0f * log10(max(avg, 0.0001f)) // varia de -80 a 0 dB
            val normalized = ((dbVal + 70.0f) / 70.0f).coerceIn(0.04f, 1.0f)
            spectrumBars.add(normalized)
        }

        return SpectrumAnalysis(
            dominantHz = dominantHz,
            centroidHz = centroidHz,
            subGravePct = subGravePct,
            medioPct = medioPct,
            agudoPct = agudoPct,
            spectrum = spectrumBars
        )
    }

    // Algoritmo FFT Cooley-Tukey Radix-2 in-place (N = 512)
    private fun fftRadix2(re: FloatArray, im: FloatArray) {
        val n = re.size
        // Inversão de bits
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tempRe = re[i]
                val tempIm = im[i]
                re[i] = re[j]
                im[i] = im[j]
                re[j] = tempRe
                im[j] = tempIm
            }
            var k = n / 2
            while (k <= j) {
                j -= k
                k /= 2
            }
            j += k
        }

        // Estágios de borboleta FFT
        var l = 2
        while (l <= n) {
            val halfL = l / 2
            val angle = -2.0 * PI / l
            val wRe = cos(angle).toFloat()
            val wIm = sin(angle).toFloat()

            var i = 0
            while (i < n) {
                var curWRe = 1.0f
                var curWIm = 0.0f
                for (m in 0 until halfL) {
                    val idxA = i + m
                    val idxB = idxA + halfL
                    val tRe = curWRe * re[idxB] - curWIm * im[idxB]
                    val tIm = curWRe * im[idxB] + curWIm * re[idxB]

                    re[idxB] = re[idxA] - tRe
                    im[idxB] = im[idxA] - tIm
                    re[idxA] += tRe
                    im[idxA] += tIm

                    val nextWRe = curWRe * wRe - curWIm * wIm
                    val nextWIm = curWRe * wIm + curWIm * wRe
                    curWRe = nextWRe
                    curWIm = nextWIm
                }
                i += l
            }
            l *= 2
        }
    }
}
