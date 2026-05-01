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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class CoachingSignal(val step: String, val readyToCapture: Boolean, val prompt: String = "", val rawResponse: String = "")

class GemmaEngine {

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    // LiteRT-LM only supports one session at a time — mutex ensures coaching and
    // describeImage never try to use the conversation concurrently.
    private val mutex = Mutex()

    suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
        val modelPath = findModelFile(context)
        if (modelPath == null) {
            Log.e("Vantage", "No .litertlm model file found on device")
            return@withContext
        }
        Log.d("Vantage", "Found model at: $modelPath")

        val nativeLibDir = context.applicationInfo.nativeLibraryDir
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

    fun isReady(): Boolean = conversation != null

    suspend fun describeImage(imagePath: String): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val conv = conversation ?: return@withLock "Engine not ready"
            try {
                val msg = Message.user(
                    Contents.of(
                        Content.ImageFile(imagePath),
                        Content.Text("Describe what you see in this image in 2-3 sentences.")
                    )
                )
                val sb = StringBuilder()
                conv.sendMessageAsync(msg).collect { response ->
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
    }

    suspend fun getCoachingStep(imagePath: String, currentStep: String?, subject: String = ""): CoachingSignal = withContext(Dispatchers.IO) {
        mutex.withLock {
            val eng = engine ?: return@withLock CoachingSignal(currentStep ?: "Hold still", false)
            if (!isReady()) return@withLock CoachingSignal(currentStep ?: "Hold still", false)

            // Reset to a clean context before each coaching step
            conversation?.close()
            val conv = try {
                eng.createConversation().also { conversation = it }
            } catch (e: Exception) {
                Log.e("Vantage", "Failed to reset conversation", e)
                return@withLock CoachingSignal(currentStep ?: "Hold still", false)
            }

            Log.d("Vantage", "getCoachingStep: conversation ready, starting inference")
            try {
                val subjectLine = if (subject.isNotBlank()) "The user wants to photograph: $subject. " else ""
                val prev = if (currentStep != null) "Last instruction: \"$currentStep\". " else ""
                val prompt = "${subjectLine}You are a professional photography coach watching through the camera viewfinder. ${prev}" +
                    "Look at this image carefully and output exactly one of the following — nothing else:\n" +
                    "- The single word READY (only that word) if the subject is well-framed and the shot is worth taking now.\n" +
                    "- A short movement instruction (4 words max) telling the user to move their camera or body. Only use: left, right, up, down, forward, back, tilt left, tilt right.\n" +
                    "Valid examples: READY | Move left | Tilt up | Step back | Pan right | Move forward\n" +
                    "No sentences. No explanation. No punctuation. Output the instruction only."
                val msg = Message.user(
                    Contents.of(
                        Content.ImageFile(imagePath),
                        Content.Text(prompt)
                    )
                )
                val sb = StringBuilder()
                conv.sendMessageAsync(msg).collect { response ->
                    val chunk = response.contents.contents
                        .filterIsInstance<Content.Text>()
                        .joinToString("") { it.text }
                    sb.append(chunk)
                }
                val response = sb.toString().trim().trimEnd('.').trimEnd('!')
                Log.d("Vantage", "getCoachingStep: response='$response'")
                if (response.equals("READY", ignoreCase = true)) {
                    CoachingSignal("Tap to capture!", readyToCapture = true, prompt = prompt, rawResponse = response)
                } else {
                    CoachingSignal(response.ifBlank { currentStep ?: "Hold still" }, readyToCapture = false, prompt = prompt, rawResponse = response)
                }
            } catch (e: Exception) {
                Log.e("Vantage", "Coaching step failed", e)
                CoachingSignal(currentStep ?: "Hold still", false)
            }
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
