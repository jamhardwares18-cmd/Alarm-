package com.example.tamilalarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class AlarmItem(val id: Int, val hour: Int, val minute: Int, val enabled: Boolean)

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("tamil_alarm", Context.MODE_PRIVATE)

    fun hourly(c: Context): Boolean = sp(c).getBoolean("hourly", false)

    fun setHourly(c: Context, on: Boolean) {
        sp(c).edit().putBoolean("hourly", on).apply()
    }

    fun alarms(c: Context): List<AlarmItem> {
        return try {
            val arr = JSONArray(sp(c).getString("alarms", "[]"))
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                AlarmItem(o.getInt("id"), o.getInt("h"), o.getInt("m"), o.getBoolean("on"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveAlarms(c: Context, list: List<AlarmItem>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("h", it.hour)
                    .put("m", it.minute)
                    .put("on", it.enabled)
            )
        }
        sp(c).edit().putString("alarms", arr.toString()).apply()
    }

    fun nextId(c: Context): Int = (alarms(c).maxOfOrNull { it.id } ?: 0) + 1

    fun updateAlarm(c: Context, item: AlarmItem) {
        saveAlarms(c, alarms(c).map { if (it.id == item.id) item else it })
    }

    fun removeAlarm(c: Context, id: Int) {
        saveAlarms(c, alarms(c).filter { it.id != id })
    }

    fun versesJson(c: Context): String? = sp(c).getString("verses", null)

    fun setVersesJson(c: Context, json: String) {
        sp(c).edit().putString("verses", json).apply()
    }
}
