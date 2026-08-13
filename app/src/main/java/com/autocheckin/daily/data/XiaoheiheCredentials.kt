package com.autocheckin.daily.data

/** Parses the account format used by the Xiaoheihe reference script. */
data class XiaoheiheCredentials(
    val heyboxId: String,
    val pkey: String,
    val tokenId: String
) {
    fun toCookie(): String = listOf(
        "heybox_id=$heyboxId",
        "pkey=$pkey",
        "x_xhh_tokenid=$tokenId"
    ).joinToString("; ")
}

object XiaoheiheCredentialParser {
    fun parse(raw: String): XiaoheiheCredentials? {
        val value = raw.trim()
        val separator = value.indexOf('#')
        if (separator <= 0) return null

        val heyboxId = value.substring(0, separator).trim()
        val fields = value.substring(separator + 1)
            .split(';')
            .mapNotNull { field ->
                field.trim().split('=', limit = 2).takeIf { it.size == 2 }
                    ?.let { it[0].trim() to it[1].trim() }
            }.toMap()
        val pkey = fields["pkey"].orEmpty()
        val tokenId = fields["x_xhh_tokenid"].orEmpty()
        return if (heyboxId.isNotBlank() && pkey.isNotBlank() && tokenId.isNotBlank()) {
            XiaoheiheCredentials(heyboxId, pkey, tokenId)
        } else null
    }

    fun normalize(raw: String): String = parse(raw)?.toCookie() ?: raw.trim()
}
