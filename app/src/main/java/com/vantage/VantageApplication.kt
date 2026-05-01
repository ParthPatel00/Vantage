package com.vantage

import android.app.Application
import android.util.Log
import com.vantage.ai.GemmaEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Holds the singleton [GemmaEngine] and kicks off its initialization at app start so the
 * model is loading before the user lands on the camera screen. Exposes [modelInitState] so
 * the loading screen can show real progress and the camera screen can know when AI calls
 * are safe to make.
 *
 * The fake-progress animation runs to 95% over ~8s, then snaps to 100% the moment
 * `gemmaEngine.initialize()` returns. If init is faster than the animation, the user just
 * sees a quick jump to ready; if it's slower, progress stalls at 95% until init lands.
 */
class VantageApplication : Application() {

    val gemmaEngine = GemmaEngine()

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _modelInitState = MutableStateFlow(ModelInitState())
    val modelInitState: StateFlow<ModelInitState> = _modelInitState.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        startModelInit()
    }

    private fun startModelInit() {
        applicationScope.launch {
            val progressJob: Job = launch { animateProgressToCeiling() }
            try {
                Log.d(TAG, "Starting Gemma init…")
                gemmaEngine.initialize(this@VantageApplication)
                Log.d(TAG, "Gemma init returned. gemmaReady=${gemmaEngine.isReady()}")
            } catch (t: Throwable) {
                Log.e(TAG, "Gemma init threw", t)
            } finally {
                progressJob.cancel()
                // ready=true means "init has finished, advance the UI." Whether Gemma
                // actually loaded successfully is checked separately at each call site
                // via gemmaEngine.isReady() — that lets the emulator (no model) fall
                // through to a working camera+Unsplash UI instead of stalling on the
                // loading screen.
                _modelInitState.value = ModelInitState(
                    progress = 1f,
                    ready = true,
                    gemmaReady = gemmaEngine.isReady()
                )
            }
        }
    }

    /** Smooth fake progress so the loading screen feels alive even when init is silent. */
    private suspend fun animateProgressToCeiling() {
        var p = 0f
        val ceiling = 0.95f
        val stepMs = 80L
        val stepDelta = 0.012f
        while (p < ceiling) {
            delay(stepMs)
            p = (p + stepDelta).coerceAtMost(ceiling)
            _modelInitState.value = _modelInitState.value.copy(progress = p)
        }
    }

    companion object {
        private const val TAG = "Vantage"
    }
}

data class ModelInitState(
    val progress: Float = 0f,
    /** True once the init coroutine has finished, regardless of Gemma success. */
    val ready: Boolean = false,
    /** True only if Gemma actually loaded — informational; gating happens at call sites. */
    val gemmaReady: Boolean = false
)
