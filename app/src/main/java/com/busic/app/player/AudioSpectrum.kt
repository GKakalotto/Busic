package com.busic.app.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import android.os.Handler
import android.os.HandlerThread
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

object AudioSpectrum {
    const val BAR_COUNT = 19
    private const val FFT = 1024
    private const val MASK = 2047

    private val ring = FloatArray(MASK + 1)
    private val real = FloatArray(FFT)
    private val imag = FloatArray(FFT)
    private val hann = FloatArray(FFT) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / (FFT - 1)))).toFloat()
    }
    private val bands = FloatArray(BAR_COUNT)
    private val targets = FloatArray(BAR_COUNT)
    private val gate = Any()
    private var peak = 0.05f
    private val worker = HandlerThread("busic-fft").apply { start() }
    private val handler = Handler(worker.looper)

    @Volatile
    private var written = 0
    private var sampled = 0

    @Volatile
    private var queued = false

    @Volatile
    var wanted: Boolean = false

    fun add(sample: Float) {
        val index = written
        ring[index and MASK] = sample
        val next = index + 1
        written = next
        if (wanted && !queued && next - sampled >= 1024) {
            queued = true
            handler.post {
                queued = false
                computeFrame()
            }
        }
    }

    fun read(dst: FloatArray) {
        synchronized(gate) {
            System.arraycopy(targets, 0, dst, 0, minOf(dst.size, targets.size))
        }
    }

    private fun computeFrame() {
        val end = written
        if (end < FFT || end - sampled < 1024) return
        sampled = end
        for (i in 0 until FFT) {
            real[i] = ring[(end - FFT + i) and MASK] * hann[i]
            imag[i] = 0f
        }
        transform(real, imag)
        val last = FFT / 2
        var max = 0.02f
        for (bar in bands.indices) {
            val start = logBin(bar, last).coerceAtLeast(1)
            val endBin = logBin(bar + 1, last).coerceAtLeast(start + 1)
            var sum = 0f
            var count = 0
            for (bin in start until endBin) {
                sum += hypot(real[bin], imag[bin])
                count++
            }
            val value = if (count == 0) 0f else sum / count
            bands[bar] = value
            if (value > max) max = value
        }
        synchronized(gate) {
            if (max > peak) peak += (max - peak) * 0.45f else peak *= 0.997f
            val scale = peak.coerceAtLeast(0.02f)
            for (i in targets.indices) {
                val raw = (bands[i] / scale).coerceIn(0f, 1f).toDouble().pow(0.72).toFloat()
                val rate = if (raw >= targets[i]) 0.7f else 0.14f
                targets[i] += (raw - targets[i]) * rate
            }
        }
    }

    fun decay() {
        synchronized(gate) {
            peak *= 0.985f
            for (i in targets.indices) targets[i] *= 0.9f
        }
    }

    fun settled(): Boolean = synchronized(gate) { targets.all { it < 0.03f } }

    fun release() {
        wanted = false
        handler.removeCallbacksAndMessages(null)
        queued = false
        written = 0
        sampled = 0
        synchronized(gate) {
            peak = 0.05f
            targets.fill(0f)
        }
    }

    private fun logBin(index: Int, last: Int): Int {
        val t = index.toFloat() / BAR_COUNT
        return last.toDouble().pow(t.toDouble()).toInt().coerceIn(1, last)
    }

    private fun transform(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]
                re[i] = re[j]
                re[j] = tr
                val ti = im[i]
                im[i] = im[j]
                im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wLenRe = cos(angle).toFloat()
            val wLenIm = sin(angle).toFloat()
            var i = 0
            while (i < n) {
                var wRe = 1f
                var wIm = 0f
                for (k in 0 until len / 2) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + len / 2] * wRe - im[i + k + len / 2] * wIm
                    val vIm = re[i + k + len / 2] * wIm + im[i + k + len / 2] * wRe
                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + len / 2] = uRe - vRe
                    im[i + k + len / 2] = uIm - vIm
                    val nextRe = wRe * wLenRe - wIm * wLenIm
                    wIm = wRe * wLenIm + wIm * wLenRe
                    wRe = nextRe
                }
                i += len
            }
            len = len shl 1
        }
    }
}

class SpectrumAudioProcessor : BaseAudioProcessor() {
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val encoding = inputAudioFormat.encoding
        if (encoding != C.ENCODING_PCM_16BIT && encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        if (AudioSpectrum.wanted) analyze(inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN))
        replaceOutputBuffer(size).put(inputBuffer).flip()
    }

    private fun analyze(buf: ByteBuffer) {
        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        val floatPcm = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val frame = (if (floatPcm) 4 else 2) * channels
        while (buf.remaining() >= frame) {
            val sample = if (floatPcm) {
                var sum = 0f
                repeat(channels) { sum += buf.float }
                sum / channels
            } else {
                var sum = 0f
                repeat(channels) { sum += buf.short.toFloat() }
                sum / channels / 32768f
            }
            AudioSpectrum.add(sample)
        }
    }
}
