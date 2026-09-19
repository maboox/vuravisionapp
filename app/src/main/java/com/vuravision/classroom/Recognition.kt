package com.vuravision.classroom

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.*

class Recognition {
    private val manager
        get() = RemoteModelManager.getInstance()

    private fun model(language: String): DigitalInkRecognitionModel =
        DigitalInkRecognitionModel.builder(
                requireNotNull(DigitalInkRecognitionModelIdentifier.fromLanguageTag(language))
            )
            .build()

    fun installed(language: String, done: (Boolean) -> Unit, error: (Exception) -> Unit) {
        manager
            .isModelDownloaded(model(language))
            .addOnSuccessListener { done(it) }
            .addOnFailureListener { error(it) }
    }

    fun download(language: String, done: () -> Unit, error: (Exception) -> Unit) {
        manager
            .download(model(language), DownloadConditions.Builder().build())
            .addOnSuccessListener { done() }
            .addOnFailureListener { error(it) }
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
