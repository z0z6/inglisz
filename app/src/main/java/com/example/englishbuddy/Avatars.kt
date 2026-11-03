package com.example.englishbuddy

import java.util.Locale

/** Wygląd rysowanego awatara. hairStyle: 0 = krótkie, 1 = długie, 2 = kręcone */
data class Look(
    val skin: Long,
    val hair: Long,
    val hairStyle: Int,
    val shirt: Long,
    val eyes: Long,
    val glasses: Boolean = false,
    val beard: Boolean = false
)

data class Avatar(
    val id: String,
    val name: String,
    val subtitle: String,
    val persona: String,
    val locale: Locale,
    val voiceIndex: Int,   // który z głosów TTS dla danego akcentu
    val pitch: Float,
    val rate: Float,
    val colorHex: Long,
    val greeting: String,  // tryb wideo (Tavus)
    val look: Look
)

val LEVELS = listOf("A2", "B1", "B2", "C1")

val AVATARS = listOf(
    Avatar(
        "emma", "Emma", "Amerykańska nauczycielka • cierpliwa, codzienne tematy",
        "You are Emma, a warm and patient American English teacher from Chicago who loves travel and cooking.",
        Locale.US, 0, 1.1f, 0.95f, 0xFFE57373,
        "Hi! I'm Emma. It's so nice to meet you. What would you like to talk about today?",
        Look(0xFFF2C9A8, 0xFFC8963E, 1, 0xFFE57373, 0xFF4A90D9)
    ),
    Avatar(
        "james", "James", "Brytyjczyk • rozmowy o pracy i biznesie",
        "You are James, a friendly British professional from London. You help people practise business English and job interviews.",
        Locale.UK, 1, 0.85f, 1.0f, 0xFF64B5F6,
        "Hello, I'm James. Lovely to meet you. Shall we start with a bit about your work?",
        Look(0xFFEBC3A0, 0xFF5A3A22, 0, 0xFF2F4B7C, 0xFF6B8E5A, glasses = true)
    ),
    Avatar(
        "sofia", "Sofia", "Australijka • luźny small talk",
        "You are Sofia, a cheerful and funny Australian from Sydney. You are great at casual small talk and explain slang simply.",
        Locale("en", "AU"), 0, 1.15f, 1.0f, 0xFF81C784,
        "G'day! I'm Sofia. How's your day going so far?",
        Look(0xFFD9A27A, 0xFF3B2314, 2, 0xFF2E8B57, 0xFF5B3A1E)
    ),
    Avatar(
        "raj", "Raj", "Inżynier z Bangalore • technologia i praca",
        "You are Raj, a patient software engineer from Bangalore who speaks clear Indian English and loves tech and cricket.",
        Locale("en", "IN"), 0, 0.9f, 1.0f, 0xFFFFB74D,
        "Hi, I'm Raj. Nice to meet you! Tell me, what do you do these days?",
        Look(0xFFB77A52, 0xFF1C1410, 0, 0xFFE08A2E, 0xFF2B1B10, beard = true)
    )
)

// Tryb lekki (tekst -> model -> TTS)
fun systemPrompt(a: Avatar, level: String) = """
${a.persona}
You are having a SPOKEN conversation with a Polish learner of English (CEFR level $level). Stay in character.
Rules:
- Use natural English matched to level $level. Reply in 1-3 short sentences, and usually end with a follow-up question.
- Plain spoken text only: no markdown, no lists, no emojis.
- If the learner's last message contained a noticeable grammar, vocabulary or word-choice mistake, add the separator ||| after your reply and then a very short correction in Polish that includes the corrected English phrase. If there was no real mistake, do not add the separator.
- Do not speak Polish in the spoken part. If the learner seems stuck, give a one-line hint in simple English.
- Speech recognition may produce small errors in punctuation or homophones; ignore those.
""".trimIndent()

// Tryb wideo (Tavus) – dopisywane jako conversational_context
fun videoContext(a: Avatar, level: String) = """
You are playing this character in a live video call: ${a.persona}
You are talking with a Polish learner of English at CEFR level $level. Your goal is to help them practise speaking English.
- Speak natural English matched to level $level, in short turns (1-3 sentences), and usually end with a follow-up question.
- Never lecture. If the learner makes a noticeable mistake, gently model the correct form inside your reply (for example: "Ah, you went to the cinema - nice!") without stopping the flow.
- If the learner is clearly stuck, rephrase more simply or give a short hint. Only if they explicitly ask, explain briefly in Polish.
- Be expressive and warm, with natural facial expressions and gestures, like a real person on a video call.
""".trimIndent()
