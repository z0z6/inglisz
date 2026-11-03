package com.example.englishbuddy

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { App() }
            }
        }
    }
}

@Composable
fun App(vm: ChatViewModel = viewModel()) {
    BackHandler(enabled = vm.avatar != null) { vm.endCall() }
    when {
        vm.avatar == null -> PickerScreen(vm)
        vm.videoMode -> VideoScreen(vm)
        else -> CallScreen(vm)
    }
}

// ------------------------------------------------------------------ wybór awatara

@Composable
fun PickerScreen(vm: ChatViewModel) {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<Avatar?>(null) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) pending?.let { vm.startCall(it) }
        else vm.error = "Bez zgody na mikrofon rozmowa nie jest możliwa."
        pending = null
    }

    fun choose(a: Avatar) {
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) vm.startCall(a) else {
            pending = a
            permLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Column {
                    Text("English Buddy", style = MaterialTheme.typography.headlineMedium)
                    Text("Wybierz rozmówcę i poziom", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LEVELS.forEach { l ->
                            FilterChip(selected = vm.level == l, onClick = { vm.level = l }, label = { Text(l) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = vm.videoMode, onCheckedChange = { vm.videoMode = it })
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (vm.videoMode) "Wideo-awatary (Tavus, płatne za minutę)"
                            else "Tryb darmowy (Gemini + głos z telefonu)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (vm.videoMode) {
                        OutlinedTextField(
                            value = vm.tavusKey,
                            onValueChange = { vm.tavusKey = it },
                            label = { Text("Klucz API Tavus (puste = wbudowany w apkę)") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = vm.palId,
                            onValueChange = { vm.palId = it },
                            label = { Text("PAL ID / persona (z platform.tavus.io)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        OutlinedTextField(
                            value = vm.geminiKey,
                            onValueChange = { vm.geminiKey = it },
                            label = { Text("Darmowy klucz Gemini (aistudio.google.com/apikey)") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = vm.geminiModel,
                            onValueChange = { vm.geminiModel = it },
                            label = { Text("Model Gemini (np. gemini-3.5-flash)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = vm.apiKey,
                            onValueChange = { vm.apiKey = it },
                            label = { Text("(opcjonalnie) klucz Anthropic – użyty, gdy brak Gemini") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            items(AVATARS) { a ->
                Card(Modifier.fillMaxWidth().clickable { choose(a) }) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Portrait(a, Modifier.size(72.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(a.name, style = MaterialTheme.typography.titleMedium)
                                Text(a.subtitle, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (vm.videoMode) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = vm.faceIds[a.id].orEmpty(),
                                onValueChange = { vm.faceIds[a.id] = it },
                                label = { Text("Face ID dla ${a.name} (twarz z Tavus)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
        ErrorBar(vm)
    }
}

// ------------------------------------------------------------------ ekran rozmowy

@Composable
fun CallScreen(vm: ChatViewModel) {
    val a = vm.avatar ?: return
    val listState = rememberLazyListState()
    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.lastIndex)
    }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(a.name, style = MaterialTheme.typography.titleLarge)
                Text("Poziom ${vm.level}", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { vm.endCall() }) { Text("Zakończ") }
        }

        Box(Modifier.clickable { vm.onMic() }) { AvatarStage(a, vm.phase, vm.mouth) }

        Text(
            when (vm.phase) {
                Phase.Idle -> "Dotknij mikrofonu, aby mówić"
                Phase.Listening -> "Słucham…"
                Phase.Thinking -> "Myślę…"
                Phase.Speaking -> "Mówi… (dotknij, aby przerwać)"
            },
            style = MaterialTheme.typography.labelLarge
        )
        if (vm.partial.isNotBlank()) {
            Text(vm.partial, fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(vm.messages) { MessageBubble(it) }
        }

        ErrorBar(vm)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = vm.handsFree, onCheckedChange = { vm.handsFree = it })
            Spacer(Modifier.width(8.dp))
            Text("Tryb rozmowy (automatyczne słuchanie)", style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = { vm.onMic() },
            enabled = vm.phase != Phase.Thinking,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(
                when (vm.phase) {
                    Phase.Listening -> "Gotowe – zakończ mówienie"
                    Phase.Speaking -> "Przerwij i mów"
                    Phase.Thinking -> "…"
                    Phase.Idle -> "🎤 Mów"
                }
            )
        }
    }
}

@Composable
fun MessageBubble(m: Msg) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (m.fromUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (m.fromUser) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ) { Text(m.text, Modifier.padding(10.dp)) }
        m.tip?.let {
            Text(
                "💡 $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(start = 8.dp, top = 2.dp)
            )
        }
    }
}

@Composable
fun ErrorBar(vm: ChatViewModel) {
    vm.error?.let {
        Text(
            it,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().clickable { vm.error = null }.padding(6.dp)
        )
    }
}

// ------------------------------------------------------------------ tryb wideo (Tavus)

@Composable
fun VideoScreen(vm: ChatViewModel) {
    val a = vm.avatar ?: return
    val ctx = LocalContext.current
    val url = vm.videoUrl
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(a.name, style = MaterialTheme.typography.titleLarge)
                Text("Wideo • poziom ${vm.level}", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { vm.endCall() }) { Text("Zakończ") }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (url == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text("Łączę z ${a.name}…")
                }
            } else {
                TavusWebView(url, Modifier.fillMaxSize())
            }
        }
        ErrorBar(vm)
        if (url != null) {
            TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) {
                Text("Nie działa? Otwórz w przeglądarce")
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TavusWebView(url: String, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.userAgentString = settings.userAgentString.replace("; wv", "")
                webChromeClient = object : WebChromeClient() {
                    override fun onPermissionRequest(request: PermissionRequest) {
                        // Mikrofon tak; kamery nie udostępniamy.
                        val res = request.resources
                            .filter { it == PermissionRequest.RESOURCE_AUDIO_CAPTURE }
                            .toTypedArray()
                        if (res.isNotEmpty()) request.grant(res) else request.deny()
                    }
                }
                webViewClient = WebViewClient()
                loadUrl(url)
            }
        },
        onRelease = { it.destroy() }
    )
}

