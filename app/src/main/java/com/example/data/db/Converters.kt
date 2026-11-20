package com.example.data.db

import androidx.room.TypeConverter
import org.json.JSONObject

class Converters {
    @TypeConverter
    fun fromBillBreakdown(map: Map<Int, Int>?): String? {
        if (map.isNullOrEmpty()) return null
        val json = JSONObject()
        map.forEach { (denom, count) ->
            if (count > 0) {
                json.put(denom.toString(), count)
            }
        }
        return if (json.length() == 0) null else json.toString()
    }

    @TypeConverter
    fun toBillBreakdown(raw: String?): Map<Int, Int>? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val json = JSONObject(raw)
            val result = linkedMapOf<Int, Int>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val denom = k.toIntOrNull()
                val count = json.optInt(k, 0)
                if (denom != null && count > 0) {
                    result[denom] = count
                }
            }
            result.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }
}
