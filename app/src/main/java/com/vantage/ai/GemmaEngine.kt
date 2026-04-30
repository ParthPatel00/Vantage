package com.vantage.ai

import android.content.Context
import android.os.Environment
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class GemmaEngine {

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
        val modelPath = findModelFile()
        if (modelPath == null) {
            Log.e("Vantage", "No .litertlm model file found on device")
            return@withContext
        }
        Log.d("Vantage", "Found model at: $modelPath")

        try {
            val config = EngineConfig(
                modelPath = modelPath,
                backend = Backend.GPU(),
                visionBackend = Backend.GPU(),
                cacheDir = context.cacheDir.absolutePath
            )
            engine = Engine(config)
            engine!!.initialize()

            val convConfig = ConversationConfig(
                systemInstruction = Contents.of("You are a photography assistant. Describe what you see in images clearly and concisely."),
                samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.7)
            )
            conversation = engine!!.createConversation(convConfig)

            Log.d("Vantage", "Gemma engine initialized successfully")
        } catch (e: Exception) {
            Log.e("Vantage", "Failed to initialize Gemma engine", e)
        }
    }

    suspend fun describeImage(imagePath: String): String = withContext(Dispatchers.IO) {
        val conv = conversation
        if (conv == null) {
            Log.e("Vantage", "Engine not initialized")
            return@withContext "Engine not ready"
        }

        try {
            val contents = Contents.of(
                Content.ImageFile(imagePath),
                Content.Text("Describe what you see in this image in 2-3 sentences.")
            )
            val message = conv.sendMessage(contents)
            val textParts = message.contents.contents.filterIsInstance<Content.Text>()
            textParts.joinToString("") { it.text }.ifBlank { "No response" }
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

    private fun findModelFile(): String? {
        val searchDirs = listOf(
            File(Environment.getExternalStorageDirectory(), "Download"),
            File("/data/local/tmp")
        )
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
