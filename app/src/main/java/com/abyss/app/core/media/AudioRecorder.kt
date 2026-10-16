package com.abyss.app.core.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Records a voice message to an .m4a file in the app cache using MediaRecorder.
 * One recording at a time.
 */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    var isRecording: Boolean = false
        private set

    fun start(): File {
        stopInternal(discard = true)
        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context)
            else @Suppress("DEPRECATION") MediaRecorder()
        rec.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(96_000)
            setAudioSamplingRate(44_100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = rec
        outputFile = file
        isRecording = true
        return file
    }

    /** Stop and return the recorded file, or null if nothing/too short. */
    fun stop(): File? {
        val file = outputFile
        stopInternal(discard = false)
        return file?.takeIf { it.exists() && it.length() > 1024 }
    }

    fun cancel() = stopInternal(discard = true)

    private fun stopInternal(discard: Boolean) {
        recorder?.let { r -> runCatching { r.stop() }; runCatching { r.release() } }
        recorder = null
        isRecording = false
        if (discard) { outputFile?.delete(); outputFile = null }
    }
}
