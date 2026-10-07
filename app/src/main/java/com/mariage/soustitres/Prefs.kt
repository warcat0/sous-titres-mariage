package com.mariage.soustitres

import android.content.Context
import android.content.SharedPreferences

/** Réglages de l'appli, enregistrés sur le téléphone. */
object Prefs {
    const val NAME = "reglages"
    const val TEXT_SIZE = "textSize"
    const val SHOW_FRENCH = "showFrench"
    const val POSITION_TOP = "positionTop"
    const val BG_ALPHA = "bgAlpha"
    const val MUTE_BEEPS = "muteBeeps"
    const val VIDEO_SOUND = "videoSound"
    const val VIDEO_URI = "videoUri"

    fun get(c: Context): SharedPreferences = c.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun textSize(c: Context) = get(c).getInt(TEXT_SIZE, 30)
    fun showFrench(c: Context) = get(c).getBoolean(SHOW_FRENCH, false)
    fun positionTop(c: Context) = get(c).getBoolean(POSITION_TOP, false)
    fun bgAlpha(c: Context) = get(c).getInt(BG_ALPHA, 55)          // en %
    fun muteBeeps(c: Context) = get(c).getBoolean(MUTE_BEEPS, true)
    fun videoSound(c: Context) = get(c).getBoolean(VIDEO_SOUND, false)
    fun videoUri(c: Context): String? = get(c).getString(VIDEO_URI, null)
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
