package com.vantage.contracts

interface IVoiceSystem {
    fun speak(message: String, onDone: (() -> Unit)? = null)
    fun startListening(onResult: (String) -> Unit, onError: () -> Unit)
    fun stopListening()
    fun shutdown()
}
