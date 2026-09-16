package com.webunime.mobile.data

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.ToJson

/** Terima Int JSON yang kadang datang sebagai 1030.5 / string. */
class LenientIntAdapter {
    @FromJson
    fun fromJson(reader: JsonReader): Int? {
        return when (reader.peek()) {
            JsonReader.Token.NULL -> {
                reader.nextNull<Unit>()
                null
            }
            JsonReader.Token.NUMBER -> reader.nextDouble().toInt()
            JsonReader.Token.STRING -> {
                val raw = reader.nextString().trim()
                raw.toDoubleOrNull()?.toInt() ?: raw.toIntOrNull()
            }
            else -> {
                reader.skipValue()
                null
            }
        }
    }

    @ToJson
    fun toJson(writer: JsonWriter, value: Int?) {
        if (value == null) writer.nullValue() else writer.value(value)
    }
}
