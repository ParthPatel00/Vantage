package com.vantage.ai

import android.content.Context
import android.os.Environment
import android.system.Os
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class GemmaEngine {

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
        val modelPath = findModelFile(context)
        if (modelPath == null) {
            Log.e("Vantage", "No .litertlm model file found on device")
            return@withContext
        }
        Log.d("Vantage", "Found model at: $modelPath")

        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        // QNN HTP needs ADSP_LIBRARY_PATH to locate libQnnHtpV79Skel.so on the Hexagon DSP.
        try {
            Os.setenv("ADSP_LIBRARY_PATH", nativeLibDir, true)
            Os.setenv("LD_LIBRARY_PATH", nativeLibDir, true)
        } catch (e: Exception) {
            Log.w("Vantage", "Could not set native library paths: ${e.message}")
        }

        val config = EngineConfig(
            modelPath = modelPath,
            backend = Backend.NPU(nativeLibDir),
            visionBackend = Backend.GPU(),
            audioBackend = Backend.CPU()
        )
        engine = Engine(config)
        engine!!.initialize()
        conversation = engine!!.createConversation()
        Log.d("Vantage", "Gemma engine ready")
    }

    suspend fun describeImage(imagePath: String): String = withContext(Dispatchers.IO) {
        val conv = conversation ?: return@withContext "Engine not ready"
        try {
            val userMessage = Message.user(
                Contents.of(
                    Content.ImageFile(imagePath),
                    Content.Text("Describe what you see in this image in 2-3 sentences.")
                )
            )
            val sb = StringBuilder()
            conv.sendMessageAsync(userMessage).collect { response ->
                val chunk = response.contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString("") { it.text }
                sb.append(chunk)
            }
            sb.toString().ifBlank { "No response" }
        } catch (e: Exception) {
            Log.e("Vantage", "Gemma inference failed", e)
            "Error: ${e.message}"
        }
    }

    fun close() {
        conversation?.close()
        engine?.close()
        conversation = null
        engine = null
    }

    private fun findModelFile(context: Context): String? {
        val searchDirs = listOf(
            context.getExternalFilesDir(null),
            File(Environment.getExternalStorageDirectory(), "Download"),
            context.filesDir,
            File("/data/local/tmp")
        ).filterNotNull()

        for (dir in searchDirs) {
            if (!dir.exists()) continue
            val files = dir.listFiles { f -> f.name.endsWith(".litertlm") } ?: continue
            val sm8750 = files.find { it.name.contains("sm8750") }
            if (sm8750 != null) return sm8750.absolutePath
            if (files.isNotEmpty()) return files[0].absolutePath
        }
        return null
    }
}
