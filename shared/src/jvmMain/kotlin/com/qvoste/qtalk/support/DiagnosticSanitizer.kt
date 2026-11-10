package com.qvoste.qtalk.support

/** Conservative best effort. User-authored report text and screenshots are intentionally untouched. */
class DiagnosticSanitizer(private val knownSecrets: () -> List<String> = { emptyList() }) {
    fun clean(input: String): String {
        var text = input
        val secrets = try { knownSecrets() } catch (_: Exception) { return "[record omitted: secret filter unavailable]" }
        for (secret in secrets.filter { it.isNotEmpty() }.sortedByDescending { it.length }) {
            text = text.replace(secret, "[redacted]", ignoreCase = true)
        }
        // A raw SIP dump can contain arbitrary bodies and continuations. Omit the entire record.
        if (Regex("(?im)(SIP/2\\.0|(?:INVITE|REGISTER|ACK|BYE|OPTIONS|SUBSCRIBE|NOTIFY|MESSAGE|CANCEL)\\s+sips?:|^(?:Via|Call-ID|CSeq|WWW-Authenticate):)").containsMatchIn(text))
            return "[raw protocol record omitted]\n"
        var continuation = false
        text = text.lineSequence().mapNotNull { line ->
            if (Regex("(?i)(authorization|proxy.authorization|bearer|password|passwd|secret|token|cookie|api[_-]?key|ha1|credential)\\s*[\"']?\\s*[:= ]").containsMatchIn(line)) {
                continuation = true
                "[sensitive record omitted]"
            } else if (continuation && !Regex("^\\d{4}-\\d{2}-\\d{2}.*QTalk ").containsMatchIn(line)) null
            else { continuation = false; line }
        }.joinToString("\n")
        val patterns = listOf(
            "(?i)\\b(?:https?|wss?|ftp)://[^\\s<>\"']+",
            "(?i)\\bsips?:[^\\s<>\"']+",
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+(?:\\.[A-Za-z]{2,})?",
            "(?i)(?:[a-z]:[\\\\/](?:users|documents and settings)[\\\\/]|/(?:home|Users)/)[^\\s\"'<>]+",
            "\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b",
            "(?i)(?<![a-z0-9])(?:[0-9a-f]{0,4}:){2,}[0-9a-f:%]*(?![a-z0-9])",
            "(?i)\\b(?:ext|peer|target|username|domain|realm)=[^\\s]+",
            "\\+?\\d[\\d ()-]{5,}\\d",
            "\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+",
            "\\bAIza[A-Za-z0-9_-]{30,}",
            "\\b[A-Za-z0-9_-]{32,}\\b"
        )
        for (pattern in patterns) text = Regex(pattern).replace(text, "[redacted]")
        return text
    }
}
