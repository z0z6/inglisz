package com.example.englishbuddy

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class Phase { Idle, Listening, Thinking, Speaking }

data class Msg(val fromUser: Boolean, val text: String, val tip: String? = null)

object Claude {
    private val client = OkHttpClient.Builder()
        .callTimeout(40, TimeUnit.SECONDS)
        .build()

    // Model o niskim opóźnieniu – najlepszy do rozmowy na żywo.
    private const val MODEL = "claude-haiku-5-5"

    fun chat(key: String, system: String, history: List<Pair<String, String>>): String {
        val msgs = JSONArray()
        history.forEach { (role, text) ->
            msgs.put(JSONObject().put("role", role).put("content", text))
        }
        val body = JSONObject()
            .put("model", MODEL)
            .put("max_tokens", 400)
            .put("system", system)
            .put("messages", msgs)

        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", key)
            .header("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(req).execute().use { resp ->
            val s = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw RuntimeException("API ${resp.code}: ${s.take(200)}")
            val arr = JSONObject(s).getJSONArray("content")
            return buildString {
                for (i in 0 until arr.length()) {
                    val b = arr.getJSONObject(i)
                    if (b.optString("type") == "text") append(b.getString("text"))
                }
            }
        }
    }
}

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val main = Handler(Looper.getMainLooper())
    private val prefs = app.getSharedPreferences("eb", Context.MODE_PRIVATE)

    var phase by mutableStateOf(Phase.Idle)
        private set
    var partial by mutableStateOf("")
        private set
    var avatar by mutableStateOf<Avatar?>(null)
        private set
    var level by mutableStateOf(prefs.getString("level", "B1") ?: "B1")
    var apiKey by mutableStateOf(prefs.getString("key", "") ?: "")
    var handsFree by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    val messages = mutableStateListOf<Msg>()

    private val history = mutableListOf<Pair<String, String>>()
    private var inCall = false
    private var job: Job? = null

    // ---------- TTS ----------
    private lateinit var tts: TextToSpeech
    private var ttsReady = false

    // ---------- STT ----------
    private var recognizer: SpeechRecognizer? = null

    init {
        tts = TextToSpeech(app) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { main.post { phase = Phase.Speaking } }
                    override fun onDone(utteranceId: String?) { main.post { afterSpeech() } }
                    override fun onError(utteranceId: String?) { main.post { afterSpeech() } }
                })
            }
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onError(code: Int) {
            phase = Phase.Idle
            partial = ""
            // 5 = anulowane, 6 = cisza, 7 = brak dopasowania – to nie są błędy dla użytkownika
            if (code != 5 && code != 6 && code != 7) {
                error = if (code == 9) "Brak zgody na mikrofon." else "Błąd rozpoznawania mowy ($code)."
            }
        }

        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty().trim()
            partial = ""
            if (text.isEmpty()) phase = Phase.Idle else submit(text)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
        }
    }

    fun startCall(a: Avatar) {
        prefs.edit().putString("level", level).putString("key", apiKey).apply()
        error = null
        avatar = a
        messages.clear()
        history.clear()
        inCall = true
        history.add("user" to "[The conversation begins. Greet me in character and ask one easy opening question.]")
        requestReply()
    }

    fun endCall() {
        inCall = false
        job?.cancel()
        tts.stop()
        recognizer?.cancel()
        phase = Phase.Idle
        partial = ""
        avatar = null
    }

    fun onMic() {
        when (phase) {
            Phase.Idle, Phase.Speaking -> listen()
            Phase.Listening -> recognizer?.stopListening()
            Phase.Thinking -> {}
        }
    }

    private fun listen() {
        val ctx = getApplication<Application>()
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            error = "Brak usługi rozpoznawania mowy (zainstaluj/aktualizuj aplikację Google)."
            return
        }
        tts.stop()
        partial = ""
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(ctx).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }
        phase = Phase.Listening
        r.startListening(intent)
    }

    private fun submit(text: String) {
        messages.add(Msg(true, text))
        history.add("user" to text)
        requestReply()
    }

    private fun requestReply() {
        val a = avatar ?: return
        val key = apiKey.ifBlank { BuildConfig.ANTHROPIC_API_KEY }
        if (key.isBlank()) {
            error = "Brak klucza API – wpisz go na ekranie startowym."
            phase = Phase.Idle
            return
        }
        phase = Phase.Thinking
        val snapshot = history.toList()
        val sys = systemPrompt(a, level)
        job = viewModelScope.launch {
            val res = withContext(Dispatchers.IO) { runCatching { Claude.chat(key, sys, snapshot) } }
            res.onSuccess { raw ->
                val parts = raw.split("|||", limit = 2)
                val spoken = parts[0].trim()
                val tip = parts.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
                history.add("assistant" to spoken)
                messages.add(Msg(false, spoken, tip))
                if (inCall) speak(spoken) else phase = Phase.Idle
            }.onFailure {
                error = it.message ?: "Błąd sieci."
                phase = Phase.Idle
            }
        }
    }

    private fun speak(text: String) {
        val a = avatar ?: return
        if (!ttsReady) { afterSpeech(); return }
        tts.language = a.locale
        val voices = tts.voices.orEmpty()
            .filter { it.locale.language == "en" && it.locale.country == a.locale.country }
            .sortedBy { it.name }
        if (voices.isNotEmpty()) tts.voice = voices[a.voiceIndex % voices.size]
        tts.setPitch(a.pitch)
        tts.setSpeechRate(a.rate)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "u${System.nanoTime()}")
    }

    private fun afterSpeech() {
        phase = Phase.Idle
        if (inCall && handsFree) listen()
    }

    override fun onCleared() {
        tts.shutdown()
        recognizer?.destroy()
        super.onCleared()
    }
}
