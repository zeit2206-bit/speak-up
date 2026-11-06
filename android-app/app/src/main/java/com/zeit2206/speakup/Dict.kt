package com.zeit2206.speakup

import android.content.Context

/**
 * Word lookup, same data and rules as the PDF IPA Reader extension:
 * US IPA from the CMU dictionary (assets/ipa.tsv), Spanish glosses from FreeDict (assets/es.tsv).
 */
object Dict {
    @Volatile private var ipa: HashMap<String, String>? = null
    @Volatile private var es: HashMap<String, String>? = null
    val ready get() = ipa != null && es != null

    fun load(context: Context) {
        if (ipa == null) ipa = readTsv(context, "ipa.tsv", 140_000)
        if (es == null) es = readTsv(context, "es.tsv", 60_000)
    }

    // readBytes + indexOf: useLines is slow on device for 100k+ lines.
    private fun readTsv(context: Context, name: String, size: Int): HashMap<String, String> {
        val m = HashMap<String, String>(size)
        val s = context.assets.open(name).use { it.readBytes() }.decodeToString()
        var p = 0
        while (p < s.length) {
            val tab = s.indexOf('\t', p); if (tab < 0) break
            var nl = s.indexOf('\n', tab); if (nl < 0) nl = s.length
            m[s.substring(p, tab)] = s.substring(tab + 1, nl)
            p = nl + 1
        }
        return m
    }

    data class Entry(val word: String, val ipa: String?, val es: String?)

    fun lookup(raw: String): Entry {
        val w = normalizeToken(raw)
        return Entry(w, find(ipa, w)?.let(::fixStress), find(es, w))
    }

    /** Regular inflections the dictionaries may not list verbatim ("movies" → "movie"). */
    private fun baseForms(w: String) = buildList {
        if (w.endsWith("'s") || w.endsWith("s'")) add(w.dropLast(2))
        if (w.endsWith("ies") && w.length > 4) add(w.dropLast(3) + "y")
        if (w.endsWith("es") && w.length > 3) add(w.dropLast(2))
        if (w.endsWith("s") && w.length > 3) add(w.dropLast(1))
        if (w.endsWith("ed") && w.length > 4) { add(w.dropLast(1)); add(w.dropLast(2)) }
        if (w.endsWith("ing") && w.length > 5) { add(w.dropLast(3) + "e"); add(w.dropLast(3)) }
        if (w.endsWith("ly") && w.length > 4) add(w.dropLast(2))
        if ('-' in w) add(w.replace("-", ""))
    }

    private fun find(m: Map<String, String>?, w: String): String? {
        if (m == null || w.isEmpty()) return null
        m[w]?.let { return it }
        return baseForms(w).firstNotNullOfOrNull { m[it] }
    }

    /** CMU sometimes marks two primary stresses: keep the last one as primary. */
    private fun fixStress(ipa: String): String {
        if (ipa.count { it == 'ˈ' } <= 1) return ipa
        val last = ipa.lastIndexOf('ˈ')
        return buildString { ipa.forEachIndexed { i, c -> append(if (c == 'ˈ' && i != last) 'ˌ' else c) } }
    }
}

fun normalizeToken(s: String) = s.lowercase().replace('’', '\'').replace(Regex("[^a-z0-9'\\-]"), "").trim('\'', '-')
