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
        userIntent: String? = null
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
                buildRound1Prompt(userIntent)
            } else {
                buildRound2Prompt(previousSettings, userIntent)
            }

            try {
                val msg = Message.user(Contents.of(Content.ImageFile(imagePath), Content.Text(prompt)))
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

    private fun buildRound1Prompt(userIntent: String? = null): String {
        val intentBlock = if (!userIntent.isNullOrBlank()) {
            """
USER REQUEST: "$userIntent"
This is the user's creative vision. It is your TOP PRIORITY. Choose the filter, color grading, contrast, saturation, and all settings to match what they described. Stick with your choice across rounds.

STYLE EXAMPLES (use as starting points, adjust for the actual scene):
- "cinematic portrait" -> filter:CINEMATIC, brightness:-0.05, contrast:1.40, saturation:0.80, gamma:1.15
- "1980s retro look" -> filter:VINTAGE, brightness:0.05, contrast:1.15, saturation:0.70, gamma:1.20
- "dramatic moody" -> filter:DRAMATIC, brightness:-0.10, contrast:1.55, saturation:0.75, gamma:1.10
- "bright and airy" -> filter:MUTED, brightness:0.10, contrast:0.90, saturation:0.65, gamma:1.18
- "vivid nature" -> filter:VIVID, brightness:0.02, contrast:1.25, saturation:1.45, gamma:1.05
"""
        } else ""
        return """
You are an expert professional photographer and photo editor. Analyze this image carefully.
$intentBlock
Identify: scene type (portrait, landscape, food, indoor, night, golden hour, architecture, etc.)
         light quality (bright sun, overcast, indoor warm, indoor cool, low light, backlit)
         subject distance (close macro, medium 1-2m, far 3m+)

PHOTOGRAPHY RULES:

ISO:
- Bright outdoor: 100-200 | Overcast/shade: 200-400 | Indoor natural: 400-800
- Indoor artificial: 800-1600 | Night/dark: 1600-3200 (pair with noise_reduction:high_quality)

SHUTTER (1/x seconds, x=the number you output):
- Bright outdoor still: 250-500 | Normal handheld: 60-125
- Low light still: 30-60 | Action/motion: 500-2000

WHITE BALANCE:
- Sunny: daylight | Overcast: cloudy | Shade: shade | Sunset: twilight
- Tungsten bulbs: incandescent | Fluorescent office: fluorescent | Unknown: auto

ZOOM:
- Wide environment/group: 0.6 | Most scenes: 1.0
- Flattering portrait (compresses background): 2.0 | Distant/telephoto: 3.0

FILTER - Choose the filter that best matches the scene mood AND user request. BE BOLD:
- NATURAL: Clean look. Use ONLY when no clear mood or style fits.
- WARM: Golden hour, sunsets, candlelit, cozy warm lighting
- COOL: Winter, ocean, blue hour, moody cool tones
- VIVID: Bold colorful scenes, landscapes, flowers, markets
- DRAMATIC: Dark moody urban, stormy, night cityscapes, high-contrast tension
- CINEMATIC: Film/movie look, teal-orange grade, storytelling portraits
- VINTAGE: Retro 70s/80s, faded analog, warm nostalgia
- MUTED: Soft pastel, airy, desaturated calm, minimalist
- NOIR: Black-and-white drama, editorial, architecture
BE BOLD with your choice. The user expects a visible transformation.

FOCUS (0=infinity/far, 20=very close/macro):
- Landscapes/subjects >3m: 0 | Portraits 1-2m: 5-8 | Table/food ~0.5m: 12-16 | Macro <30cm: 18-20

NOISE REDUCTION: off (ISO<400) | fast (ISO 400-1600) | high_quality (ISO>1600)
SHARPNESS: off or fast for portraits | high_quality for landscapes/architecture/text

BRIGHTNESS: -0.1 to +0.15 for exposure correction. Negative for moody/dramatic, positive for airy/bright.
CONTRAST: 1.15-1.30 portraits | 1.30-1.50 landscapes/architecture | 1.50-1.80 dramatic/noir/cinematic
SATURATION: 0.85-1.10 portraits | 1.25-1.50 vivid nature/food | 0.55-0.75 muted/cinematic/vintage
GAMMA: 1.08-1.15 standard lift | 1.15-1.25 for faded/vintage looks | 1.0 only for noir/dramatic
IMPORTANT: Do NOT output all-default values (brightness=0, contrast=1.0, saturation=1.0, gamma=1.0). Every photo deserves enhancement. Push your values to create a visible improvement.

FLASH: "on" only if subject is in shadow in an otherwise bright scene. Default: "off".

COMPOSITION:
Detect the main subject. Output its bounding box as subject_box:[y1,x1,y2,x2] in 0-1000 coordinates.
Output where it SHOULD be for ideal composition as suggested_box:[y1,x1,y2,x2].
Rules: portraits on vertical thirds, landscapes horizon on horizontal third,
       lead room in direction of gaze/motion, avoid dead-center framing.
Output composition_tip: a short direction to the photographer (e.g. "move left", "tilt down").
Set composition_ok:true if current framing is acceptable, false if they should reframe.

This is your FIRST look at the scene. Apply your best initial settings.
Set ready:false - you will analyze the result next round to confirm.

Output ONLY a single-line JSON object, no markdown, no explanation:
{"filter":"WARM","iso":200,"shutter":125,"wb":"daylight","focus":0,"noise_reduction":"fast","sharpness":"fast","zoom":1.0,"brightness":0.05,"contrast":1.25,"saturation":1.15,"gamma":1.10,"flash":"off","subject_box":[200,300,800,700],"suggested_box":[200,333,800,667],"composition_tip":"move slightly left","composition_ok":true,"ready":false,"scene_description":"what you see in the scene","tip":"photography advice for this situation","reason":"brief rationale"}
""".trimIndent()
    }

    private fun buildRound2Prompt(prev: SceneAnalysis, userIntent: String? = null): String {
        val wbStr = wbModeToString(prev.wbMode)
        val noiseStr = noiseModeToString(prev.noiseReductionMode)
        val sharpStr = sharpnessModeToString(prev.sharpnessMode)
        val intentBlock = if (!userIntent.isNullOrBlank()) {
            "\nUSER REQUEST: \"$userIntent\"\nThis is the user's creative vision. Keep the same filter and mood you chose in round 1. Do not change the filter.\n"
        } else ""
        return """
You are an expert photographer. You previously applied these camera settings:
filter=${prev.filter.name}, iso=${prev.iso}, shutter=1/${prev.shutter}s, wb=$wbStr,
focus=${prev.focusDistance}, noise_reduction=$noiseStr, sharpness=$sharpStr,
zoom=${prev.zoom}x, brightness=${prev.brightness}, contrast=${prev.contrast},
saturation=${prev.saturation}, gamma=${prev.gamma}, flash=${prev.flash}
$intentBlock
This is a NEW frame captured WITH those settings already active on the camera.
Evaluate carefully: Is the exposure correct? Is the color balance accurate? Is the zoom appropriate? Is the image quality good?

Also re-evaluate composition. Update subject_box and suggested_box for the current frame.
If composition has improved, set composition_ok:true. If still off, set composition_ok:false with a new tip.

If YES (settings look optimal and composition is good):
  Set ready:true. You may make minor adjustments if needed (within 20% of current values).
If NO (something is still off):
  Set ready:false. Output corrected settings and explain what was wrong.

Output ONLY a single-line JSON object, no markdown, no explanation:
{"filter":"NATURAL","iso":200,"shutter":125,"wb":"daylight","focus":0,"noise_reduction":"fast","sharpness":"fast","zoom":1.0,"brightness":0.0,"contrast":1.0,"saturation":1.0,"gamma":1.0,"flash":"off","subject_box":[200,300,800,700],"suggested_box":[200,333,800,667],"composition_tip":"looks good","composition_ok":true,"ready":false,"scene_description":"what you observe","tip":"photography advice","reason":"what you adjusted"}
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
        val jsonStr = rawJson.replace(Regex("""(?<=[,{])\s*""([a-z_])""")) { ",\"${it.groupValues[1]}" }
            .replace(Regex("""^""([a-z])""")) { "\"${it.groupValues[1]}" }
            .replace("\"\"", "\"")
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
