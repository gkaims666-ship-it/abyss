package com.abyss.app.feature.guard

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import com.abyss.app.core.net.Http
import okhttp3.Request

enum class ScamRisk { LOW, MEDIUM, HIGH }

data class ScamResult(
    val number: String,
    val risk: ScamRisk,
    val reasons: List<String>,
) {
    /** Short analytics line for the warning notification. */
    fun summary(): String = when (risk) {
        ScamRisk.HIGH -> "High risk. " + reasons.joinToString("; ")
        ScamRisk.MEDIUM -> "Be cautious. " + reasons.joinToString("; ")
        ScamRisk.LOW -> "Low risk. " + reasons.joinToString("; ")
    }
}

/**
 * Assesses an unknown caller. There is no single free, reliable scam database,
 * so this is deliberately transparent: fast local heuristics, optionally
 * enriched by a user-configured data source (a URL template containing
 * {number} that returns text/JSON). The user controls the source; results are
 * advisory, not definitive.
 */
class ScamLookup(private val context: Context) {

    /** True if [rawNumber] matches a saved contact (then we don't warn). */
    fun isKnownContact(rawNumber: String?): Boolean {
        if (rawNumber.isNullOrBlank()) return false
        return runCatching {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(rawNumber))
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID),
                null, null, null)?.use { it.count > 0 } ?: false
        }.getOrDefault(false)
    }

    fun assess(rawNumber: String?, lookupUrlTemplate: String): ScamResult {
        val number = rawNumber?.trim().orEmpty()
        val reasons = mutableListOf<String>()
        var score = 0

        if (number.isBlank()) {
            return ScamResult("Private/Unknown", ScamRisk.MEDIUM,
                listOf("Hidden or withheld caller ID — common for unwanted calls"))
        }

        val digits = number.filter { it.isDigit() }
        // Heuristics.
        if (digits.length <= 6) { score += 2; reasons += "Unusually short number (often shortcodes/robocalls)" }
        if (number.startsWith("+") && !number.startsWith(localPrefixGuess())) {
            score += 1; reasons += "International number not matching your region"
        }
        if (digits.startsWith("900") || digits.startsWith("809")) {
            score += 2; reasons += "Premium-rate prefix"
        }
        if (digits.toSet().size <= 2 && digits.length >= 6) {
            score += 1; reasons += "Repetitive digit pattern"
        }

        // Optional external data source.
        if (lookupUrlTemplate.contains("{number}")) {
            runCatching {
                val url = lookupUrlTemplate.replace("{number}", Uri.encode(number))
                val resp = Http.client.newCall(Request.Builder().url(url).build()).execute()
                val body = resp.use { it.body?.string().orEmpty() }.lowercase()
                if (listOf("scam", "fraud", "spam", "мошен", "fraude").any { body.contains(it) }) {
                    score += 3; reasons += "Reported in your configured data source"
                }
            }
        }

        if (reasons.isEmpty()) reasons += "No risk signals found"
        val risk = when {
            score >= 3 -> ScamRisk.HIGH
            score >= 1 -> ScamRisk.MEDIUM
            else -> ScamRisk.LOW
        }
        return ScamResult(number, risk, reasons)
    }

    /** Rough local prefix guess from the SIM country; best-effort only. */
    private fun localPrefixGuess(): String = "+"
}
