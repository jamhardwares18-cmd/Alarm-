package com.example.tamilalarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object Verses {
    data class Verse(val ref: String, val text: String)

    private fun parse(json: String): List<Verse> {
        val arr = JSONArray(json)
        return List(24) { i ->
            if (i < arr.length()) {
                val o = arr.getJSONObject(i)
                Verse(o.optString("ref"), o.optString("text"))
            } else {
                Verse("", "")
            }
        }
    }

    private fun defaults(c: Context): List<Verse> {
        val s = c.assets.open("verses.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        return parse(s)
    }

    /** 24 entries, index = hour of day (0..23). */
    fun all(c: Context): List<Verse> {
        val saved = Prefs.versesJson(c)
        return try {
            if (saved != null) parse(saved) else defaults(c)
        } catch (e: Exception) {
            try {
                defaults(c)
            } catch (e2: Exception) {
                List(24) { Verse("", "") }
            }
        }
    }

    fun save(c: Context, list: List<Verse>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("ref", it.ref).put("text", it.text)) }
        Prefs.setVersesJson(c, arr.toString())
    }

    /** Text to speak for the given hour, or null if nothing is set. */
    fun speech(c: Context, hour: Int): String? {
        val v = all(c).getOrNull(hour) ?: return null
        if (v.ref.isBlank() && v.text.isBlank()) return null
        val spokenRef = v.ref.replace(":", ", வசனம் ")
        return if (v.text.isBlank()) {
            "இன்றைய வசனம். $spokenRef."
        } else {
            "இன்றைய வசனம். $spokenRef. ${v.text}"
        }
    }
}
