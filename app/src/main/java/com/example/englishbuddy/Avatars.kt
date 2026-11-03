package com.example.englishbuddy

import java.util.Locale

data class Avatar(
    val id: String,
    val name: String,
    val subtitle: String,
    val persona: String,
    val locale: Locale,
    val voiceIndex: Int,   // który z dostępnych głosów TTS dla danego akcentu
    val pitch: Float,
    val rate: Float,
    val colorHex: Long,
    val asset: String      // ścieżka portretu w assets/
)

val LEVELS = listOf("A2", "B1", "B2", "C1")

val AVATARS = listOf(
    Avatar(
        "emma", "Emma", "Amerykańska nauczycielka • cierpliwa, codzienne tematy",
        "You are Emma, a warm and patient American English teacher from Chicago who loves travel and cooking.",
        Locale.US, 0, 1.1f, 0.95f, 0xFFE57373, "avatars/emma.jpg"
    ),
    Avatar(
        "james", "James", "Brytyjczyk • rozmowy o pracy i biznesie",
        "You are James, a friendly British professional from London. You help people practise business English and job interviews.",
        Locale.UK, 1, 0.85f, 1.0f, 0xFF64B5F6, "avatars/james.jpg"
    ),
    Avatar(
        "sofia", "Sofia", "Australijka • luźny small talk",
        "You are Sofia, a cheerful and funny Australian from Sydney. You are great at casual small talk and explain slang simply.",
        Locale("en", "AU"), 0, 1.15f, 1.0f, 0xFF81C784, "avatars/sofia.jpg"
    ),
    Avatar(
        "raj", "Raj", "Inżynier z Bangalore • technologia i praca",
        "You are Raj, a patient software engineer from Bangalore who speaks clear Indian English and loves tech and cricket.",
        Locale("en", "IN"), 0, 0.9f, 1.0f, 0xFFFFB74D, "avatars/raj.jpg"
    )
)

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
