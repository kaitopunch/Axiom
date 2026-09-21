package com.axiom.store

import java.security.MessageDigest

/**
 * SHA-256 of a payload's JSON, hex. What [EntryDao.replace] compares to decide whether a write would
 * change anything: two payloads with the same fingerprint are the same payload, for every practical
 * purpose — a 32-bit `hashCode` on a megabyte of JSON is not, and a collision there would be new data
 * silently dropped on the floor.
 */
internal fun fingerprint(json: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(json.toByteArray(Charsets.UTF_8))
    val hex = CharArray(digest.size * 2)
    digest.forEachIndexed { index, byte ->
        val value = byte.toInt() and 0xff
        hex[index * 2] = HEX[value ushr 4]
        hex[index * 2 + 1] = HEX[value and 0x0f]
    }
    return String(hex)
}

private val HEX = "0123456789abcdef".toCharArray()
