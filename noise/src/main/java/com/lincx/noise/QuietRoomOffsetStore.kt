package com.lincx.noise

import android.content.Context

/** 把安静房间偏移记在本机。不联网。 */
internal class QuietRoomOffsetStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun read(): Double {
        if (!prefs.contains(KEY)) return 0.0
        val value = Double.fromBits(prefs.getLong(KEY, 0L))
        if (!value.isFinite()) return 0.0
        return value.coerceIn(-DbLevel.MAX_OFFSET_DB, DbLevel.MAX_OFFSET_DB)
    }

    fun write(offsetDb: Double) {
        val stored = offsetDb.coerceIn(-DbLevel.MAX_OFFSET_DB, DbLevel.MAX_OFFSET_DB)
        prefs.edit().putLong(KEY, stored.toBits()).apply()
    }

    private companion object {
        const val NAME = "noise_quiet_room"
        const val KEY = "offset_db"
    }
}
