package com.example.englishbuddy

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Duży awatar z ramką stanu (słucha / myśli / mówi). `mouth` 0..1 = otwarcie ust. */
@Composable
fun AvatarStage(a: Avatar, phase: Phase, mouth: Float) {
    val m by animateFloatAsState(mouth, tween(70), label = "mouth")
    val ring = when (phase) {
        Phase.Listening -> Color(0xFF66BB6A)
        Phase.Thinking -> Color(0xFFFFCA28)
        Phase.Speaking -> Color(a.colorHex)
        Phase.Idle -> Color.Gray
    }
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier.size(270.dp).clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color(a.colorHex).copy(alpha = 0.55f), Color(a.colorHex).copy(alpha = 0.15f))
                )
            )
            .border(3.dp, ring, shape)
    ) {
        AvatarFace(a.look, m, phase, Modifier.fillMaxSize())
    }
}

/** Mała, statyczna miniatura (lista wyboru). */
@Composable
fun Portrait(a: Avatar, modifier: Modifier) {
    Box(modifier.clip(CircleShape).background(Color(a.colorHex).copy(alpha = 0.35f))) {
        AvatarFace(a.look, 0f, Phase.Idle, Modifier.fillMaxSize(), animated = false)
    }
}

@Composable
fun AvatarFace(look: Look, mouth: Float, phase: Phase, modifier: Modifier, animated: Boolean = true) {
    var time by remember { mutableFloatStateOf(0f) }
    if (animated) {
        LaunchedEffect(Unit) {
            while (true) {
                withFrameNanos { time = ((it / 1_000_000L) % 1_000_000L) / 1000f }
            }
        }
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val speaking = phase == Phase.Speaking
        val t = time
        val sway = when (phase) {
            Phase.Speaking -> sin(t * 2.3f) * 1.6f
            Phase.Thinking -> 3f
            else -> sin(t * 0.8f) * 0.6f
        }
        val nod = if (speaking) sin(t * 3.1f) * 0.008f * h else 0f
        val ph = t % 4.2f
        val blink = if (ph > 4.0f) sin((ph - 4.0f) / 0.2f * PI.toFloat()) else 0f
        val gx = when (phase) {
            Phase.Thinking -> -0.6f
            Phase.Speaking -> sin(t * 0.7f) * 0.4f
            else -> 0f
        }
        val gy = if (phase == Phase.Thinking) -0.7f else 0f
        val brow = (if (phase == Phase.Listening) 0.012f else 0f) +
            (if (phase == Phase.Thinking) 0.016f else 0f) + mouth * 0.006f
        rotate(sway, Offset(w / 2, h * 0.75f)) {
            translate(0f, nod) { drawFace(look, mouth, brow, blink, gx, gy) }
        }
    }
}

private fun DrawScope.drawFace(look: Look, mouth: Float, brow: Float, blink: Float, gx: Float, gy: Float) {
    val w = size.width
    val h = size.height
    val skin = Color(look.skin)
    val hair = Color(look.hair)
    val shirt = Color(look.shirt)
    val shade = skin.copy(red = skin.red * 0.85f, green = skin.green * 0.78f, blue = skin.blue * 0.78f)

    // włosy z tyłu (długie)
    if (look.hairStyle == 1) drawOval(hair, Offset(w * 0.22f, h * 0.12f), Size(w * 0.56f, h * 0.74f))
    // ramiona i szyja
    drawOval(shirt, Offset(w * 0.06f, h * 0.78f), Size(w * 0.88f, h * 0.5f))
    drawRoundRect(shade, Offset(w * 0.43f, h * 0.62f), Size(w * 0.14f, h * 0.2f), CornerRadius(w * 0.03f))
    // uszy i głowa
    drawCircle(skin, w * 0.035f, Offset(w * 0.275f, h * 0.47f))
    drawCircle(skin, w * 0.035f, Offset(w * 0.725f, h * 0.47f))
    drawOval(skin, Offset(w * 0.28f, h * 0.15f), Size(w * 0.44f, h * 0.55f))
    // włosy z przodu
    when (look.hairStyle) {
        2 -> for (i in 0..8) {
            val a = (180f + i * 22.5f) * PI.toFloat() / 180f
            drawCircle(hair, w * 0.065f, Offset(w * 0.5f + w * 0.225f * cos(a), h * 0.30f + h * 0.19f * sin(a)))
        }
        else -> {
            drawArc(hair, 180f, 180f, true, Offset(w * 0.265f, h * 0.115f), Size(w * 0.47f, h * 0.36f))
            drawOval(hair, Offset(w * 0.30f, h * 0.20f), Size(w * 0.32f, h * 0.09f))
        }
    }
    // policzki
    val blush = Color(0xFFFF8A80).copy(alpha = 0.18f)
    drawCircle(blush, w * 0.05f, Offset(w * 0.36f, h * 0.55f))
    drawCircle(blush, w * 0.05f, Offset(w * 0.64f, h * 0.55f))
    // brwi
    val bc = hair.copy(alpha = 0.9f)
    drawLine(bc, Offset(w * 0.35f, h * (0.365f - brow)), Offset(w * 0.46f, h * (0.35f - brow)), w * 0.014f, StrokeCap.Round)
    drawLine(bc, Offset(w * 0.54f, h * (0.35f - brow)), Offset(w * 0.65f, h * (0.365f - brow)), w * 0.014f, StrokeCap.Round)
    // oczy
    for (cx in listOf(0.41f, 0.59f)) {
        val ew = w * 0.045f
        val eh = h * 0.028f * (1f - 0.92f * blink) + h * 0.002f
        val c = Offset(w * cx, h * 0.43f)
        drawOval(Color.White, Offset(c.x - ew, c.y - eh), Size(2 * ew, 2 * eh))
        if (blink < 0.7f) {
            val ic = Offset(c.x + gx * ew * 0.45f, c.y + gy * eh * 0.45f)
            drawCircle(Color(look.eyes), w * 0.022f, ic)
            drawCircle(Color.Black, w * 0.01f, ic)
        }
        if (look.glasses) {
            drawCircle(Color(0xFF222222), w * 0.07f, c, style = Stroke(w * 0.008f))
        }
    }
    if (look.glasses) {
        drawLine(Color(0xFF222222), Offset(w * 0.48f, h * 0.42f), Offset(w * 0.52f, h * 0.42f), w * 0.008f)
    }
    // nos
    drawArc(shade, 20f, 140f, false, Offset(w * 0.46f, h * 0.47f), Size(w * 0.08f, h * 0.07f),
        style = Stroke(w * 0.008f, cap = StrokeCap.Round))
    // broda i wąsy
    if (look.beard) {
        drawArc(hair.copy(alpha = 0.92f), 0f, 180f, true, Offset(w * 0.29f, h * 0.40f), Size(w * 0.42f, h * 0.30f))
        drawOval(hair, Offset(w * 0.41f, h * 0.535f), Size(w * 0.18f, h * 0.03f))
    }
    // usta
    val cx = w * 0.5f
    val cy = h * 0.60f
    val open = mouth.coerceIn(0f, 1f)
    val mw = w * (0.15f - 0.035f * open)
    val lip = Color(0xFFB5524F)
    if (open < 0.06f) {
        drawArc(lip, 15f, 150f, false, Offset(cx - mw / 2, cy - h * 0.02f), Size(mw, h * 0.04f),
            style = Stroke(w * 0.010f, cap = StrokeCap.Round))
    } else {
        val mh = h * (0.012f + 0.075f * open)
        val top = Offset(cx - mw / 2, cy - mh * 0.35f)
        drawOval(Color(0xFF4A1C1C), top, Size(mw, mh))
        drawRoundRect(Color.White, Offset(cx - mw * 0.38f, top.y + mh * 0.04f),
            Size(mw * 0.76f, (mh * 0.28f).coerceAtMost(h * 0.02f)), CornerRadius(w * 0.004f))
        if (open > 0.3f) {
            drawOval(Color(0xFFD9726E), Offset(cx - mw * 0.25f, top.y + mh * 0.55f), Size(mw * 0.5f, mh * 0.38f))
        }
        drawOval(lip, top, Size(mw, mh), style = Stroke(w * 0.010f))
    }
}
