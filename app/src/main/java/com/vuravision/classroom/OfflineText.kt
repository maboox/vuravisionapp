package com.vuravision.classroom
import android.graphics.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.math.*
/** Bundled Latin image OCR; not a general mathematical handwriting model. Always review. */
object OfflineText {
 fun recognize(items:List<Item>,renderer:Renderer,done:(List<String>)->Unit,error:(Exception)->Unit){
  val bounds=contentBounds(items);bounds.inset(-24f,-24f)
  val scale=min(3f,2048f/max(bounds.width(),bounds.height())).coerceAtLeast(.001f)
  val bitmap=Bitmap.createBitmap((bounds.width()*scale).toInt().coerceIn(32,2048),(bounds.height()*scale).toInt().coerceIn(32,2048),Bitmap.Config.ARGB_8888)
  val canvas=Canvas(bitmap);canvas.drawColor(Color.WHITE);canvas.scale(scale,scale);canvas.translate(-bounds.left,-bounds.top)
  items.forEach{renderer.draw(canvas,it.copy(color=Color.BLACK,alpha=255,shape=if(it.kind=="ink")"round"else it.shape),true)}
  val client=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
  try{client.process(InputImage.fromBitmap(bitmap,0)).addOnSuccessListener{done(listOf(it.text).filter{it.isNotBlank()})}.addOnFailureListener(error).addOnCompleteListener{client.close();bitmap.recycle()}}catch(e:Exception){client.close();bitmap.recycle();error(e)}
 }
}
