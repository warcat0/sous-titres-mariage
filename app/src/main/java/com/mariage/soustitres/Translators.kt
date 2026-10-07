package com.mariage.soustitres

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

/**
 * Traducteurs Google ML Kit : gratuits, sans compte, sans clé.
 * Les modèles (~30 Mo par langue) sont téléchargés une fois,
 * ensuite la traduction se fait sur le téléphone, très rapidement.
 */
object Translators {
    private var toEn: Translator? = null
    private var toEs: Translator? = null

    @Synchronized
    fun en(): Translator = toEn ?: Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.FRENCH)
            .setTargetLanguage(TranslateLanguage.ENGLISH)
            .build()
    ).also { toEn = it }

    @Synchronized
    fun es(): Translator = toEs ?: Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.FRENCH)
            .setTargetLanguage(TranslateLanguage.SPANISH)
            .build()
    ).also { toEs = it }

    fun download(onDone: (Boolean, String) -> Unit) {
        val cond = DownloadConditions.Builder().build()
        en().downloadModelIfNeeded(cond)
            .onSuccessTask { es().downloadModelIfNeeded(cond) }
            .addOnSuccessListener { onDone(true, "") }
            .addOnFailureListener { e -> onDone(false, e.message ?: "erreur inconnue") }
    }

    fun checkDownloaded(cb: (Boolean) -> Unit) {
        RemoteModelManager.getInstance()
            .getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { models ->
                val codes = models.map { it.language }
                cb(codes.contains(TranslateLanguage.FRENCH) && codes.contains(TranslateLanguage.SPANISH))
            }
            .addOnFailureListener { cb(false) }
    }
}
