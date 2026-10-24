package com.example.asmrplayer.core.annotations

import java.text.Normalizer
import java.util.Locale

object AnnotationPolicy {
    const val MaxTagLength = 40
    const val MaxTagsPerItem = 128
    const val PageSize = 50
    fun tagName(raw: String): String {
        val name = Normalizer.normalize(raw, Normalizer.Form.NFKC).trim().replace(Regex("\\s+"), " ")
        require(name.isNotBlank() && name.codePointCount(0, name.length) <= MaxTagLength)
        require(name.none { Character.isISOControl(it) })
        return name
    }
    fun tagKey(raw: String): String = tagName(raw).lowercase(Locale.ROOT)
    fun sameTree(root: String, document: String): Boolean = runCatching {
        val r = java.net.URI(root)
        val d = java.net.URI(document)
        r.scheme == "content" && d.scheme == "content" && r.rawAuthority == d.rawAuthority &&
            r.rawPath.startsWith("/tree/") && d.rawPath.startsWith(r.rawPath.trimEnd('/') + "/document/")
    }.getOrDefault(false)
    fun score(raw: String): Int? {
        if (raw.isBlank()) return null
        require(raw.trim().all { it in '0'..'9' })
        return requireNotNull(raw.trim().toIntOrNull()).also { require(it in 0..100) }
    }
    fun containsPattern(raw: String): String = "%" + raw.trim().take(100)
        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
}
