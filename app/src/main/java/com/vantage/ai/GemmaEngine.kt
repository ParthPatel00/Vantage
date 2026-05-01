package com.vantage.ai

import android.content.Context
import android.hardware.camera2.CameraMetadata
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
import com.vantage.models.FilterType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

class GemmaEngine {

    private var engine: Engine? = null
    private var conversation: Conversation? = null
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

    /**
     * Free-form image+text query. Used by the inspiration tool to ask Gemma for an
     * Unsplash query that fits the current scene. Shares the [analyzeScene] mutex so
     * the two paths can't trample each other's conversation state.
     */
    suspend fun queryWithImage(imagePath: String, prompt: String): String =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val eng = engine ?: return@withLock "Engine not ready"
                if (!isReady()) return@withLock "Engine not ready"
                conversation?.close()
                conversation = null
                val conv = try {
                    eng.createConversation().also { conversation = it }
                } catch (e: Exception) {
                    Log.e("Vantage", "queryWithImage: failed to create conversation", e)
                    return@withLock "Error: ${e.message}"
                }
                try {
                    val msg = Message.user(
                        Contents.of(Content.ImageFile(imagePath), Content.Text(prompt))
                    )
                    val sb = StringBuilder()
                    conv.sendMessageAsync(msg).collect { response ->
                        val chunk = response.contents.contents
                            .filterIsInstance<Content.Text>()
                            .joinToString("") { it.text }
                        sb.append(chunk)
                    }
                    sb.toString().trim().ifBlank { "No response" }
                } catch (e: Exception) {
                    Log.e("Vantage", "queryWithImage failed", e)
                    "Error: ${e.message}"
                }
            }
        }

    /**
     * Analyzes the scene and returns optimal camera settings as a [SceneAnalysis].
     *
     * @param imagePath path to the preview frame JPEG
     * @param iteration 0 = cold start (no prior settings), 1+ = feedback round
     * @param previousSettings settings applied in the previous round (null on first call)
     */
    suspend fun analyzeScene(
        imagePath: String,
        iteration: Int = 0,
        previousSettings: SceneAnalysis? = null,
        userIntent: String? = null,
        referenceImagePath: String? = null
    ): SceneAnalysis = withContext(Dispatchers.IO) {
        mutex.withLock {
            val eng = engine ?: return@withLock SceneAnalysis(ready = true)
            if (!isReady()) return@withLock SceneAnalysis(ready = true)

            conversation?.close()
            conversation = null
            val conv = try {
                eng.createConversation().also { conversation = it }
            } catch (e: Exception) {
                Log.e("Vantage", "analyzeScene: failed to create conversation", e)
                return@withLock SceneAnalysis(ready = true)
            }

            val prompt = if (iteration == 0 || previousSettings == null) {
                buildRound1Prompt(userIntent, hasReference = referenceImagePath != null)
            } else {
                buildRound2Prompt(previousSettings, userIntent)
            }

            try {
                val contents = if (referenceImagePath != null) {
                    Contents.of(
                        Content.ImageFile(referenceImagePath),
                        Content.ImageFile(imagePath),
                        Content.Text(prompt)
                    )
                } else {
                    Contents.of(Content.ImageFile(imagePath), Content.Text(prompt))
                }
                val msg = Message.user(contents)
                val sb = StringBuilder()
                conv.sendMessageAsync(msg).collect { response ->
                    val chunk = response.contents.contents
                        .filterIsInstance<Content.Text>()
                        .joinToString("") { it.text }
                    sb.append(chunk)
                }
                var raw = sb.toString().trim()
                raw = stripRepetition(raw)
                Log.d("Vantage", "analyzeScene[$iteration]: raw='$raw'")
                parseSceneAnalysis(raw, forceNotReady = iteration == 0)
            } catch (e: Exception) {
                Log.e("Vantage", "analyzeScene failed", e)
                SceneAnalysis(ready = true, rawResponse = "ERROR: ${e.message}")
            }
        }
    }

    private fun buildRound1Prompt(userIntent: String? = null, hasReference: Boolean = false): String {
        val intentLine = if (!userIntent.isNullOrBlank()) {
            if (hasReference) {
                """
USER WANTS: "$userIntent"
REFERENCE IMAGE: The FIRST image is a reference photo showing the target style.
The SECOND image is the camera's current frame.
Analyze the reference image's color grading, contrast, saturation, warmth, mood, and aesthetic.
Choose filter, brightness, contrast, saturation, gamma to make the camera frame match the reference style.
"""
            } else {
                "USER WANTS: \"$userIntent\". Match this mood.\n"
            }
        } else ""
        return """
${intentLine}Analyze this photo. Output ONLY a short JSON, no explanation.
Pick a filter: NATURAL|WARM|COOL|VIVID|DRAMATIC|CINEMATIC|VINTAGE|NOIR|KODAK_GOLD|PORTRA|FUJI_VELVIA|GOLDEN_HOUR|BLUE_HOUR|MUTED|FADE
Set iso (100-3200), shutter (30-2000), wb (auto|daylight|cloudy|shade|incandescent|fluorescent|twilight), brightness (-0.1 to 0.1), contrast (1.1-1.6), saturation (0.6-1.4), gamma (1.0-1.2).
Zoom: 0.6 wide/group, 1.0 default, 2.0 portrait, 3.0 distant subject.
Composition: detect subject, give a short tip (e.g. "move left", "tilt down", "step back"). Set composition_ok:true if framing is good.
Do NOT use default values. Every photo needs visible enhancement.
{"filter":"...","iso":...,"shutter":...,"wb":"...","zoom":...,"brightness":...,"contrast":...,"saturation":...,"gamma":...,"composition_tip":"...","composition_ok":...,"reason":"..."}
""".trimIndent()
    }

    private fun buildRound2Prompt(prev: SceneAnalysis, userIntent: String? = null): String {
        val wbStr = wbModeToString(prev.wbMode)
        return """
Previous: filter=${prev.filter.name}, iso=${prev.iso}, shutter=${prev.shutter}, wb=$wbStr, zoom=${prev.zoom}, brightness=${prev.brightness}, contrast=${prev.contrast}, saturation=${prev.saturation}, gamma=${prev.gamma}
Is exposure/color/framing correct now? Fine-tune if needed. Set ready:true if good. Output ONLY JSON:
{"filter":"...","iso":...,"shutter":...,"wb":"...","zoom":...,"brightness":...,"contrast":...,"saturation":...,"gamma":...,"composition_tip":"...","composition_ok":...,"ready":true,"reason":"..."}
""".trimIndent()
    }

    private fun JSONObject.fuzzyString(vararg keys: String, default: String = ""): String {
        for (key in keys) {
            val v = optString(key, "")
            if (v.isNotBlank()) return v
        }
        val keySet = keys()
        while (keySet.hasNext()) {
            val k = keySet.next()
            val stripped = k.trim('_')
            if (keys.any { it == stripped }) {
                val v = optString(k, "")
                if (v.isNotBlank()) return v
            }
        }
        return default
    }

    private fun JSONObject.fuzzyBoolean(vararg keys: String, default: Boolean): Boolean {
        for (key in keys) { if (has(key)) return optBoolean(key, default) }
        val keySet = keys()
        while (keySet.hasNext()) {
            val k = keySet.next()
            val stripped = k.trim('_')
            if (keys.any { it == stripped }) return optBoolean(k, default)
        }
        return default
    }

    private fun JSONObject.fuzzyArray(vararg keys: String): org.json.JSONArray? {
        for (key in keys) { optJSONArray(key)?.let { return it } }
        val keySet = keys()
        while (keySet.hasNext()) {
            val k = keySet.next()
            val stripped = k.trim('_')
            if (keys.any { it == stripped || k.contains(it) }) {
                optJSONArray(k)?.let { return it }
            }
        }
        return null
    }

    private fun stripRepetition(raw: String): String {
        if (raw.length < 500) return raw
        val window = 30
        for (i in 400 until (raw.length - window).coerceAtMost(1500)) {
            val pattern = raw.substring(i, i + window)
            val nextOccurrence = raw.indexOf(pattern, i + window)
            if (nextOccurrence in (i + window)..(i + window + 50)) {
                return raw.substring(0, i)
            }
        }
        return if (raw.length > 2000) raw.take(2000) else raw
    }

    private fun extractFirstJson(raw: String): String? {
        val start = raw.indexOf('{')
        if (start == -1) return null
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until raw.length) {
            val c = raw[i]
            if (escape) { escape = false; continue }
            if (c == '\\' && inString) { escape = true; continue }
            if (c == '"') { inString = !inString; continue }
            if (inString) continue
            if (c == '{') depth++
            if (c == '}') { depth--; if (depth == 0) return raw.substring(start, i + 1) }
        }
        return if (depth > 0) raw.substring(start) + "}" else null
    }

    private fun parseSceneAnalysis(raw: String, forceNotReady: Boolean = false): SceneAnalysis {
        val rawJson = extractFirstJson(raw)
            ?: return SceneAnalysis(ready = true, rawResponse = "NO JSON: $raw")
        val jsonStr = rawJson
            .replace(Regex("""(?<=[,{])\s*""([a-z_])""")) { ",\"${it.groupValues[1]}" }
            .replace(Regex("""^""([a-z])""")) { "\"${it.groupValues[1]}" }
            .replace("\"\"", "\"")
            .replace(Regex(""""shutter"\s*:\s*1/(\d+)""")) { "\"shutter\":${it.groupValues[1]}" }
        Log.d("Vantage", "parseSceneAnalysis: sanitized=$jsonStr")
        return try {
            val j = JSONObject(jsonStr)
            val subjectArr = j.fuzzyArray("subject_box")
            val suggestedArr = j.fuzzyArray("suggested_box")
            SceneAnalysis(
                filter = FilterType.fromString(j.optString("filter", "NATURAL")),
                iso = j.optInt("iso", 200).coerceIn(100, 3200),
                shutter = j.optInt("shutter", 125).coerceIn(8, 4000),
                wbMode = wbStringToMode(j.optString("wb", "auto")),
                focusDistance = j.optDouble("focus", 0.0).toFloat().coerceIn(0f, 20f),
                noiseReductionMode = noiseStringToMode(j.fuzzyString("noise_reduction", default = "fast")),
                sharpnessMode = sharpnessStringToMode(j.optString("sharpness", "fast")),
                zoom = listOf(0.6f, 1f, 2f, 3f).minByOrNull { abs(it - j.optDouble("zoom", 1.0).toFloat()) } ?: 1f,
                brightness = j.optDouble("brightness", 0.0).toFloat().coerceIn(-0.15f, 0.15f),
                contrast = j.optDouble("contrast", 1.0).toFloat().coerceIn(0.80f, 1.80f),
                saturation = j.optDouble("saturation", 1.0).toFloat().coerceIn(0.0f, 1.60f),
                gamma = j.optDouble("gamma", 1.0).toFloat().coerceIn(0.90f, 1.30f),
                flash = j.optString("flash", "off"),
                ready = if (forceNotReady) false else j.fuzzyBoolean("ready", default = false),
                reasoning = j.fuzzyString("reason", default = ""),
                rawResponse = raw,
                subjectBox = if (subjectArr != null) (0 until subjectArr.length()).map { subjectArr.getInt(it) } else emptyList(),
                suggestedBox = if (suggestedArr != null) (0 until suggestedArr.length()).map { suggestedArr.getInt(it) } else emptyList(),
                compositionTip = j.fuzzyString("composition_tip", default = ""),
                compositionOk = j.fuzzyBoolean("composition_ok", default = true),
                sceneDescription = j.fuzzyString("scene_description", default = ""),
                photographyTip = j.fuzzyString("tip", "advice", default = "")
            )
        } catch (e: Exception) {
            Log.w("Vantage", "parseSceneAnalysis failed: ${e.message}")
            SceneAnalysis(ready = true, rawResponse = "PARSE ERROR: ${e.message} | $raw")
        }
    }

    private fun wbStringToMode(wb: String): Int = when (wb.lowercase().trim()) {
        "incandescent", "tungsten" -> CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT
        "fluorescent"              -> CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT
        "daylight", "sunny"       -> CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT
        "cloudy"                   -> CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
        "twilight", "sunset"      -> CameraMetadata.CONTROL_AWB_MODE_TWILIGHT
        "shade", "shadow"         -> CameraMetadata.CONTROL_AWB_MODE_SHADE
        else                       -> CameraMetadata.CONTROL_AWB_MODE_AUTO
    }

    private fun noiseStringToMode(s: String): Int = when (s.lowercase().trim()) {
        "off"          -> CameraMetadata.NOISE_REDUCTION_MODE_OFF
        "high_quality" -> CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY
        "minimal"      -> CameraMetadata.NOISE_REDUCTION_MODE_MINIMAL
        else           -> CameraMetadata.NOISE_REDUCTION_MODE_FAST
    }

    private fun sharpnessStringToMode(s: String): Int = when (s.lowercase().trim()) {
        "off"          -> CameraMetadata.EDGE_MODE_OFF
        "high_quality" -> CameraMetadata.EDGE_MODE_HIGH_QUALITY
        else           -> CameraMetadata.EDGE_MODE_FAST
    }

    private fun wbModeToString(mode: Int): String = when (mode) {
        CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT    -> "incandescent"
        CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT     -> "fluorescent"
        CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT        -> "daylight"
        CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT -> "cloudy"
        CameraMetadata.CONTROL_AWB_MODE_TWILIGHT        -> "twilight"
        CameraMetadata.CONTROL_AWB_MODE_SHADE           -> "shade"
        else                                             -> "auto"
    }

    private fun noiseModeToString(mode: Int): String = when (mode) {
        CameraMetadata.NOISE_REDUCTION_MODE_OFF          -> "off"
        CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY -> "high_quality"
        CameraMetadata.NOISE_REDUCTION_MODE_MINIMAL      -> "minimal"
        else                                              -> "fast"
    }

    private fun sharpnessModeToString(mode: Int): String = when (mode) {
        CameraMetadata.EDGE_MODE_OFF          -> "off"
        CameraMetadata.EDGE_MODE_HIGH_QUALITY -> "high_quality"
        else                                  -> "fast"
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
