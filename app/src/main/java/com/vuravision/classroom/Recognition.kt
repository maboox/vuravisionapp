package com.vuravision.classroom

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.*

class Recognition {
    private val downloads=mutableMapOf<String,com.google.android.gms.tasks.Task<Void>>()
    fun downloadManagerReady(context:android.content.Context):Boolean=try{val state=context.packageManager.getApplicationEnabledSetting("com.android.providers.downloads");state !in listOf(2,3,4)}catch(_:IllegalArgumentException){false}
    fun failure(context:android.content.Context,e:Exception):String {
        val trace=android.util.Log.getStackTraceString(e)
        return if(trace.contains("404"))context.tr("Model server returned HTTP 404 (file unavailable). Retry a fresh download or use offline Latin OCR. This is not proof that Download Manager is disabled.","سرور مدل خطای HTTP 404 داد (فایل در دسترس نیست). دانلود تازه را امتحان کنید یا از تشخیص لاتین آفلاین استفاده کنید. این خطا به‌تنهایی نشانهٔ غیرفعال بودن دانلودمنیجر نیست.")
        else context.tr("Download failed. Retry or open diagnostics for the provider error.","دانلود انجام نشد. دوباره تلاش کنید یا جزئیات فنی خطای سرویس را ببینید.")
    }

    private val manager
        get() = RemoteModelManager.getInstance()

    private fun model(language: String): DigitalInkRecognitionModel =
        DigitalInkRecognitionModel.builder(
                requireNotNull(DigitalInkRecognitionModelIdentifier.fromLanguageTag(language))
            )
            .build()

    fun installed(language: String, done: (Boolean) -> Unit, error: (Exception) -> Unit) {
        try { manager
            .isModelDownloaded(model(language))
            .addOnSuccessListener { done(it) }
            .addOnFailureListener { error(it) }
        }catch(e:Exception){error(e)}
    }

    fun download(language: String, done: () -> Unit, error: (Exception) -> Unit) {
        try {
            val task=downloads[language]?:manager.download(model(language),DownloadConditions.Builder().build()).also{task->downloads[language]=task;task.addOnCompleteListener{downloads.remove(language)}}
            task.addOnSuccessListener{installed(language,{ready->if(ready)done()else error(IllegalStateException("Downloaded model was not installed. Retry."))},error)}.addOnFailureListener(error)
        }catch(e:Exception){error(e)}
    }

    fun remove(language: String, done: () -> Unit, error: (Exception) -> Unit) {
        manager
            .deleteDownloadedModel(model(language))
            .addOnSuccessListener { done() }
            .addOnFailureListener { error(it) }
    }

    fun recognize(
        language: String,
        items: List<Item>,
        done: (List<String>) -> Unit,
        error: (Exception) -> Unit,
    ) {
        val ink = Ink.builder()
        items
            .filter { it.kind == "ink" }
            .sortedBy { it.points.firstOrNull()?.t ?: 0 }
            .forEach { o ->
                val stroke = Ink.Stroke.builder()
                o.points.forEach { p ->
                    val (x, y) = o.global(p.x * o.w / o.inkW, p.y * o.h / o.inkH)
                    stroke.addPoint(Ink.Point.create(x, y, p.t))
                }
                if (o.points.isNotEmpty()) ink.addStroke(stroke.build())
            }
        val client =
            DigitalInkRecognition.getClient(
                DigitalInkRecognizerOptions.builder(model(language)).build()
            )
        client
            .recognize(ink.build())
            .addOnSuccessListener { done(it.candidates.map { c -> c.text }) }
            .addOnFailureListener { error(it) }
            .addOnCompleteListener { client.close() }
    }
}
