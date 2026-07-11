package com.example.customkeyboard.data

/**
 * A curated list of high-frequency English words, ordered roughly by real-world usage
 * frequency (most common first). This ships inside the APK so prediction/auto-correct works
 * fully offline with zero network calls. Can be extended / replaced with a larger asset-backed
 * dictionary without changing any calling code.
 */
object CommonWords {
    val TOP_ENGLISH_WORDS: List<String> = listOf(
        "the", "be", "to", "of", "and", "a", "in", "that", "have", "i",
        "it", "for", "not", "on", "with", "he", "as", "you", "do", "at",
        "this", "but", "his", "by", "from", "they", "we", "say", "her", "she",
        "or", "an", "will", "my", "one", "all", "would", "there", "their", "what",
        "so", "up", "out", "if", "about", "who", "get", "which", "go", "me",
        "when", "make", "can", "like", "time", "no", "just", "him", "know", "take",
        "people", "into", "year", "your", "good", "some", "could", "them", "see", "other",
        "than", "then", "now", "look", "only", "come", "its", "over", "think", "also",
        "back", "after", "use", "two", "how", "our", "work", "first", "well", "way",
        "even", "new", "want", "because", "any", "these", "give", "day", "most", "us",
        "is", "was", "are", "been", "has", "had", "were", "said", "did", "getting",
        "going", "love", "great", "please", "thanks", "thank", "hello", "hi", "hey", "yes",
        "okay", "ok", "sorry", "sure", "right", "today", "tomorrow", "yesterday", "morning", "night",
        "email", "meeting", "call", "later", "soon", "here", "there", "home", "work", "school",
        "phone", "message", "text", "app", "photo", "video", "music", "food", "coffee", "lunch",
        "dinner", "breakfast", "friend", "family", "happy", "birthday", "congratulations", "awesome", "cool", "nice",
        "important", "question", "answer", "problem", "solution", "project", "report", "document", "file", "link",
        "address", "number", "code", "password", "account", "please", "let", "know", "need", "help",
        "thank", "you", "very", "much", "really", "actually", "probably", "definitely", "maybe", "perhaps",
        "before", "during", "while", "until", "since", "though", "although", "however", "therefore", "because",
        "keyboard", "typing", "swipe", "gesture", "voice", "clipboard", "settings", "theme", "dark", "light"
    ).distinct()
}
