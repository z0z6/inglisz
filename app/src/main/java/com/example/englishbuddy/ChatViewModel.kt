package com.example.englishbuddy

import android.app.Application
import android.media.MediaPlayer
import java.io.File
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.runtime.mutableStateMapOf
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

object Wav {
    private fun le16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    private fun le32(b: ByteArray, o: Int) = le16(b, o) or (le16(b, o + 2) shl 16)

    /** Obwiednia głośności (0..1) w oknach co [windowMs] ms – steruje otwarciem ust. */
    fun envelope(file: File, windowMs: Int): FloatArray {
        val b = file.readBytes()
        var pos = 12
        var sr = 22050
        var ch = 1
        var bits = 16
        var dataStart = -1
        var dataLen = 0
        while (pos + 8 <= b.size) {
            val id = String(b, pos, 4, Charsets.US_ASCII)
            val size = le32(b, pos + 4)
            if (id == "fmt ") {
                ch = le16(b, pos + 10); sr = le32(b, pos + 12); bits = le16(b, pos + 22)
            } else if (id == "data") {
                dataStart = pos + 8
                dataLen = if (size <= 0 || dataStart + size > b.size) b.size - dataStart else size
                break
            }
            if (size < 0) break
            pos += 8 + size + (size and 1)
        }
        require(dataStart >= 0 && bits == 16 && ch >= 1)
        val frameBytes = 2 * ch
        val win = sr * windowMs / 1000
        val n = dataLen / frameBytes / win
        val raw = FloatArray(n)
        for (w in 0 until n) {
            var sum = 0.0
            for (i in 0 until win) {
                val off = dataStart + (w * win + i) * frameBytes
                val v = (b[off + 1].toInt() shl 8) or (b[off].toInt() and 0xFF)
                sum += v.toDouble() * v
            }
            raw[w] = (sqrt(sum / win) / 32768.0).toFloat()
        }
        if (n == 0) return raw
        val sorted = raw.sortedArray()
        val ref = sorted[(n * 0.95f).toInt().coerceAtMost(n - 1)].coerceAtLeast(0.02f)
        val out = FloatArray(n)
        var prev = 0f
        for (i in 0 until n) {
            var v = (raw[i] / ref).coerceIn(0f, 1f)
            if (v < 0.12f) v = 0f
            v = maxOf(v, prev * 0.55f)   // szybkie otwarcie, wolniejsze zamykanie
            out[i] = v
            prev = v
        }
        return out
    }
}

object Gemini {
    private val client = OkHttpClient.Builder().callTimeout(40, TimeUnit.SECONDS).build()

    fun chat(key: String, model: String, system: String, history: List<Pair<String, String>>): String {
        val contents = JSONArray()
        history.forEach { (role, text) ->
            contents.put(
                JSONObject()
                    .put("role", if (role == "assistant") "model" else "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", text)))
            )
        }
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("maxOutputTokens", 1024).put("temperature", 0.8))
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", key)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            val s = resp.body?.string().orEmpty()
            if (resp.code == 429) throw RuntimeException("Limit darmowego Gemini wyczerpany – spróbuj za chwilę.")
            if (!resp.isSuccessful) throw RuntimeException("Gemini ${resp.code}: ${s.take(200)}")
            val parts = JSONObject(s).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
            val text = buildString {
                for (i in 0 until parts.length()) {
                    val p = parts.getJSONObject(i)
                    if (!p.optBoolean("thought", false)) append(p.optString("text"))
                }
            }
            if (text.isBlank()) throw RuntimeException("Pusta odpowiedź modelu (spróbuj innego modelu w ustawieniach).")
            return text
        }
    }
}

object Tavus {
    private val client = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private const val BASE = "https://tavusapi.com/v2"

    data class Conv(val id: String, val url: String)

    // Limity kosztów: rozmowa kończy się po 15 min lub 30 s po wyjściu użytkownika.
    private fun body(pal: String, face: String, ctx: String, greeting: String, legacy: Boolean): JSONObject {
        val props = JSONObject()
            .put("max_call_duration", 900)
            .put("participant_left_timeout", 30)
            .put("participant_absent_timeout", 120)
        if (legacy) props.put("language", "english") else props.put("languages", JSONArray().put("en"))
        val b = JSONObject()
            .put("conversation_name", "English practice")
            .put("conversational_context", ctx)
            .put("custom_greeting", greeting)
            .put("properties", props)
        if (pal.isNotBlank()) b.put(if (legacy) "persona_id" else "pal_id", pal)
        if (face.isNotBlank()) b.put(if (legacy) "replica_id" else "face_id", face)
        return b
    }

    private fun post(url: String, key: String, json: JSONObject?): Pair<Int, String> {
        val rb = (json?.toString() ?: "{}").toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(url).header("x-api-key", key).post(rb).build()
        client.newCall(req).execute().use { return it.code to it.body?.string().orEmpty() }
    }

    fun create(key: String, pal: String, face: String, ctx: String, greeting: String): Conv {
        var res = post("$BASE/conversations", key, body(pal, face, ctx, greeting, false))
        if (res.first == 400 || res.first == 422) {
            // starsze nazwy pól API (persona/replica) jako plan B
            val retry = post("$BASE/conversations", key, body(pal, face, ctx, greeting, true))
            if (retry.first in 200..299) res = retry
        }
        if (res.first !in 200..299) throw RuntimeException("Tavus ${res.first}: ${res.second.take(250)}")
        val j = JSONObject(res.second)
        return Conv(j.getString("conversation_id"), j.getString("conversation_url"))
    }

    fun end(key: String, id: String) {
        post("$BASE/conversations/$id/end", key, null)
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

    // ---------- tryb lekki: darmowy Gemini ----------
    var geminiKey by mutableStateOf(prefs.getString("gemini", "") ?: "")
    var geminiModel by mutableStateOf(prefs.getString("gemini_model", "gemini-3.5-flash") ?: "gemini-3.5-flash")
    var mouth by mutableFloatStateOf(0f)
        private set
    private var lipJob: Job? = null
    private var player: MediaPlayer? = null
    private var synthId: String? = null
    private var synthFile: File? = null
    private var synthText: String = ""

    // ---------- tryb wideo (Tavus) ----------
    var videoMode by mutableStateOf(prefs.getBoolean("video_mode", false))
    var tavusKey by mutableStateOf(prefs.getString("tavus", "") ?: "")
    var palId by mutableStateOf(prefs.getString("pal", "") ?: "")
    val faceIds = mutableStateMapOf<String, String>()
    var videoUrl by mutableStateOf<String?>(null)
        private set
    private var videoConvId: String? = null

    private val history = mutableListOf<Pair<String, String>>()
    private var inCall = false
    private var job: Job? = null

    // ---------- TTS ----------
    private lateinit var tts: TextToSpeech
    private var ttsReady = false

    // ---------- STT ----------
    private var recognizer: SpeechRecognizer? = null

    init {
        AVATARS.forEach { faceIds[it.id] = prefs.getString("face_${it.id}", "") ?: "" }
        tts = TextToSpeech(app) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        if (utteranceId?.startsWith("d") == true) main.post { phase = Phase.Speaking; startFakeLips() }
                    }
                    override fun onDone(utteranceId: String?) { main.post { onUtteranceDone(utteranceId) } }
                    override fun onError(utteranceId: String?) { main.post { onUtteranceError(utteranceId) } }
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
        val e = prefs.edit()
        e.putString("level", level).putString("key", apiKey)
        e.putString("gemini", geminiKey).putString("gemini_model", geminiModel)
        e.putBoolean("video_mode", videoMode).putString("tavus", tavusKey).putString("pal", palId)
        faceIds.forEach { (k, v) -> e.putString("face_$k", v) }
        e.apply()
        error = null
        if (videoMode) { startVideoCall(a); return }
        avatar = a
        messages.clear()
        history.clear()
        inCall = true
        history.add("user" to "[The conversation begins. Greet me in character and ask one easy opening question.]")
        requestReply()
    }

    private fun startVideoCall(a: Avatar) {
        val key = tavusKey.trim().ifBlank { BuildConfig.TAVUS_API_KEY }
        val pal = palId.trim()
        val face = faceIds[a.id].orEmpty().trim()
        if (key.isBlank()) { error = "Wpisz klucz API Tavus."; return }
        if (pal.isBlank() && face.isBlank()) { error = "Podaj PAL ID (persona) lub Face ID z platform.tavus.io."; return }
        avatar = a
        videoUrl = null
        phase = Phase.Thinking
        val ctx = videoContext(a, level)
        job = viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { Tavus.create(key, pal, face, ctx, a.greeting) }
            }
            r.onSuccess {
                videoConvId = it.id
                videoUrl = it.url
                phase = Phase.Idle
            }.onFailure {
                error = it.message ?: "Błąd połączenia z Tavus."
                avatar = null
                phase = Phase.Idle
            }
        }
    }

    fun endCall() {
        val convId = videoConvId
        if (convId != null) {
            val key = tavusKey.trim().ifBlank { BuildConfig.TAVUS_API_KEY }
            viewModelScope.launch(Dispatchers.IO) { runCatching { Tavus.end(key, convId) } }
        }
        videoConvId = null
        videoUrl = null
        inCall = false
        job?.cancel()
        stopSpeaking()
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
        stopSpeaking()
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
        val gKey = geminiKey.trim().ifBlank { BuildConfig.GEMINI_API_KEY }
        val cKey = apiKey.trim().ifBlank { BuildConfig.ANTHROPIC_API_KEY }
        val gModel = geminiModel.trim().ifBlank { "gemini-3.5-flash" }
        if (gKey.isBlank() && cKey.isBlank()) {
            error = "Brak klucza – wpisz darmowy klucz Gemini na ekranie startowym."
            phase = Phase.Idle
            return
        }
        phase = Phase.Thinking
        val snapshot = history.toList()
        val sys = systemPrompt(a, level)
        job = viewModelScope.launch {
            val res = withContext(Dispatchers.IO) { runCatching { if (gKey.isNotBlank()) Gemini.chat(gKey, gModel, sys, snapshot) else Claude.chat(cKey, sys, snapshot) } }
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

    private fun configureVoice(a: Avatar) {
        tts.language = a.locale
        val voices = tts.voices.orEmpty()
            .filter { it.locale.language == "en" && it.locale.country == a.locale.country }
            .sortedBy { it.name }
        if (voices.isNotEmpty()) tts.voice = voices[a.voiceIndex % voices.size]
        tts.setPitch(a.pitch)
        tts.setSpeechRate(a.rate)
    }

    // Głos jest najpierw generowany do pliku WAV, potem odtwarzany; z pliku liczymy
    // obwiednię głośności, która steruje ustami awatara w rytm mowy.
    private fun speak(text: String) {
        val a = avatar ?: return
        if (!ttsReady) { afterSpeech(); return }
        configureVoice(a)
        val id = "f${System.nanoTime()}"
        val file = File(getApplication<Application>().cacheDir, "$id.wav")
        synthId = id
        synthFile = file
        synthText = text
        if (tts.synthesizeToFile(text, null, file, id) != TextToSpeech.SUCCESS) speakDirect(text)
    }

    private fun speakDirect(text: String) {
        synthId = null
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "d${System.nanoTime()}")
    }

    private fun onUtteranceDone(id: String?) {
        when {
            id == null -> {}
            id.startsWith("f") -> if (id == synthId) synthFile?.let { playFile(it) }
            else -> finishPlayback(null)
        }
    }

    private fun onUtteranceError(id: String?) {
        if (id != null && id.startsWith("f") && id == synthId) speakDirect(synthText) else finishPlayback(null)
    }

    private fun playFile(file: File) {
        val env = runCatching { Wav.envelope(file, 40) }.getOrNull()
        val mp = MediaPlayer()
        try {
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener { finishPlayback(file) }
            mp.setOnErrorListener { _, _, _ -> finishPlayback(file); true }
            mp.prepare()
            player = mp
            mp.start()
        } catch (e: Exception) {
            runCatching { mp.release() }
            finishPlayback(file)
            return
        }
        phase = Phase.Speaking
        lipJob?.cancel()
        if (env == null) { startFakeLips(); return }
        lipJob = viewModelScope.launch {
            while (isActive) {
                val pos = runCatching { mp.currentPosition }.getOrDefault(0)
                mouth = env.getOrElse(pos / 40) { 0f }
                delay(30)
            }
        }
    }

    private fun startFakeLips() {
        lipJob?.cancel()
        lipJob = viewModelScope.launch {
            var t = 0f
            while (isActive) {
                mouth = 0.25f + 0.65f * abs(sin(t)) * (0.6f + 0.4f * sin(t * 0.37f))
                t += 0.45f
                delay(60)
            }
        }
    }

    private fun finishPlayback(file: File?) {
        lipJob?.cancel()
        lipJob = null
        mouth = 0f
        runCatching { player?.release() }
        player = null
        file?.delete()
        afterSpeech()
    }

    private fun stopSpeaking() {
        synthId = null
        tts.stop()
        lipJob?.cancel()
        lipJob = null
        mouth = 0f
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
        synthFile?.delete()
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
