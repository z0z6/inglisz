package com.example.englishbuddy

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
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
    if (vm.avatar == null) PickerScreen(vm) else CallScreen(vm)
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
        Text("English Buddy", style = MaterialTheme.typography.headlineMedium)
        Text("Wybierz rozmówcę i poziom", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LEVELS.forEach { l ->
                FilterChip(selected = vm.level == l, onClick = { vm.level = l }, label = { Text(l) })
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = vm.apiKey,
            onValueChange = { vm.apiKey = it },
            label = { Text("Klucz API Anthropic (puste = klucz wbudowany w apkę)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(AVATARS) { a ->
                Card(Modifier.fillMaxWidth().clickable { choose(a) }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Portrait(a, Modifier.size(72.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(a.name, style = MaterialTheme.typography.titleMedium)
                            Text(a.subtitle, style = MaterialTheme.typography.bodySmall)
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

        Box(Modifier.clickable { vm.onMic() }) { AvatarStage(a, vm.phase) }

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

// ------------------------------------------------------------------ awatar
// Tu jest jedno miejsce do podmiany na wideo-awatara (np. HeyGen/Simli/D-ID) w przyszłości.

@Composable
fun AvatarStage(a: Avatar, phase: Phase) {
    val speaking = phase == Phase.Speaking
    val inf = rememberInfiniteTransition(label = "stage")
    val mouth by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(170, easing = LinearEasing), RepeatMode.Reverse),
        label = "mouth"
    )
    val pulse by inf.animateFloat(
        initialValue = 1f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse"
    )
    val ring = when (phase) {
        Phase.Listening -> Color(0xFF66BB6A)
        Phase.Thinking -> Color(0xFFFFCA28)
        Phase.Speaking -> Color(a.colorHex)
        Phase.Idle -> Color.Gray
    }
    Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(224.dp)
                .scale(if (phase == Phase.Idle) 1f else pulse)
                .border(4.dp, ring, CircleShape)
        )
        Portrait(
            a,
            Modifier.size(200.dp).scale(if (speaking) 1f + (pulse - 1f) / 3f else 1f),
            mouth = if (speaking) mouth else 0f
        )
    }
}

@Composable
fun rememberPortrait(a: Avatar): ImageBitmap? {
    val ctx = LocalContext.current
    return remember(a.id) {
        runCatching {
            ctx.assets.open(a.asset).use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
        }.getOrNull()
    }
}

@Composable
fun Portrait(a: Avatar, modifier: Modifier, mouth: Float = 0f) {
    val bmp = rememberPortrait(a)
    val color = Color(a.colorHex)
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = a.name,
            modifier = modifier.clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        // Zastępczy, rysowany awatar (gdy nie dodano zdjęcia do assets/avatars/)
        Canvas(modifier.clip(CircleShape)) {
            val w = size.width
            val h = size.height
            drawRect(color.copy(alpha = 0.35f))
            drawCircle(color.copy(alpha = 0.8f), radius = w * 0.45f, center = Offset(w / 2, h * 1.05f))
            drawCircle(color, radius = w * 0.28f, center = Offset(w / 2, h * 0.42f))
            drawCircle(Color.White, radius = w * 0.045f, center = Offset(w * 0.42f, h * 0.38f))
            drawCircle(Color.White, radius = w * 0.045f, center = Offset(w * 0.58f, h * 0.38f))
            drawOval(
                Color(0xFF5D2A2A),
                topLeft = Offset(w * 0.43f, h * 0.52f),
                size = Size(w * 0.14f, h * (0.012f + 0.06f * mouth))
            )
        }
    }
}
