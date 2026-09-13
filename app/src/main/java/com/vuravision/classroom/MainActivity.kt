package com.vuravision.classroom

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.vuravision.classroom.document.*
import com.vuravision.classroom.engineering.EngineeringActivity
import com.vuravision.classroom.games.GamesDialog
import com.vuravision.classroom.lab.LabDialog
import com.vuravision.classroom.pdf.PdfSupport
import com.vuravision.classroom.settings.SettingsDialog
import com.vuravision.classroom.sharing.LocalShareServer
import com.vuravision.classroom.whiteboard.WhiteboardView
import java.io.File

class MainActivity:AppCompatActivity(){
    private lateinit var board:WhiteboardView
    private val autosave by lazy{AutoSave(this)}
    private var shareServer:LocalShareServer?=null

    private val importImage=registerForActivityResult(ActivityResultContracts.GetContent()){u->u?.let{importImage(it)}}
    private val importPdf=registerForActivityResult(ActivityResultContracts.GetContent()){u->u?.let{importPdf(it)}}
    private val openVura=registerForActivityResult(ActivityResultContracts.OpenDocument()){u->u?.let{runCatching{contentResolver.openInputStream(it)!!.use{stream->board.document=VuraArchive.read(this,stream)}}.onFailure{toast("Open failed: ${it.message}")}}}
    private val saveVura=registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")){u->u?.let{runCatching{contentResolver.openOutputStream(it)!!.use{out->VuraArchive.write(this,board.document,out)}}.onSuccess{toast("Saved")}.onFailure{toast("Save failed")}}}
    private val exportPdf=registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")){u->u?.let{runCatching{contentResolver.openOutputStream(it)!!.use{out->PdfExporter.export(board.document,out)}}.onSuccess{toast("PDF exported")}.onFailure{toast("Export failed")}}}

    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);window.statusBarColor=getColor(R.color.vv_bg);board=WhiteboardView(this);board.document=autosave.restore()?:BoardDocument(title="Lesson")
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(getColor(R.color.vv_bg))}
        root.addView(toolbar(),LinearLayout.LayoutParams(-1,72.dp));root.addView(board,LinearLayout.LayoutParams(-1,0,1f));setContentView(root)
    }
    override fun onPause(){super.onPause();runCatching{autosave.save(board.document)}}
    override fun onDestroy(){shareServer?.stop();super.onDestroy()}

    private fun toolbar():LinearLayout=LinearLayout(this).apply{
        orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(12,8,12,8);setBackgroundColor(getColor(R.color.vv_surface))
        fun b(text:String,action:()->Unit)=Button(this@MainActivity).apply{this.text=text;setTextColor(Color.WHITE);setBackgroundColor(getColor(R.color.vv_surface2));setOnClickListener{action()};minWidth=104.dp;minHeight=56.dp}.also{addView(it,LinearLayout.LayoutParams(-2,-1).apply{setMargins(4,0,4,0)})}
        b("✎ Pen"){board.tool=WhiteboardView.Tool.PEN};b("⌫ Erase"){board.tool=WhiteboardView.Tool.ERASER};b("↔ Select"){board.tool=WhiteboardView.Tool.SELECT};b("✋ Pan"){board.tool=WhiteboardView.Tool.PAN};b("↶"){board.undo()};b("↷"){board.redo()}
        addView(Space(this@MainActivity),LinearLayout.LayoutParams(0,1,1f));b("VuraVision ☰"){showMenu()}
    }

    private fun showMenu(){ val items=arrayOf("Import image","Import PDF","Save .vura","Open .vura","Export PDF","Share PDF by QR","Lab","Games","Settings","Engineering Mode","Clear page")
        AlertDialog.Builder(this).setTitle("VuraVision").setItems(items){_,i->when(i){
            0->importImage.launch("image/*");1->importPdf.launch("application/pdf");2->saveVura.launch("lesson.vura");3->openVura.launch(arrayOf("application/zip","application/octet-stream"));4->exportPdf.launch("VuraVision-lesson.pdf");5->shareQr();6->LabDialog.show(this);7->GamesDialog.show(this);8->SettingsDialog.show(this);9->startActivity(Intent(this,EngineeringActivity::class.java));10->confirmClear()
        }}.show()
    }
    private fun importImage(uri:Uri){runCatching{val f=File(filesDir,"imports/image_${System.currentTimeMillis()}.bin");f.parentFile?.mkdirs();contentResolver.openInputStream(uri)!!.use{input->f.outputStream().use{input.copyTo(it)}};board.addObject(CanvasObject.ImageObject(bounds=RectF(160f,120f,960f,720f),localPath=f.absolutePath))}.onFailure{toast("Image import failed")}}
    private fun importPdf(uri:Uri){runCatching{val o=PdfSupport.importPdf(this,uri);PdfSupport.renderPreview(this,o);board.addObject(o)}.onFailure{toast("PDF import failed: ${it.message}")}}
    private fun shareQr(){runCatching{val f=File(cacheDir,"VuraVision-share.pdf");f.outputStream().use{PdfExporter.export(board.document,it)};shareServer?.stop();val s=LocalShareServer(f);shareServer=s;val url=s.start();val image=ImageView(this).apply{setImageBitmap(s.qr(url));adjustViewBounds=true};val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,12,24,12);addView(image,LinearLayout.LayoutParams(-1,560.dp));addView(TextView(this@MainActivity).apply{text=url;textSize=14f;setTextColor(Color.DKGRAY);gravity=Gravity.CENTER})};AlertDialog.Builder(this).setTitle("Scan on the same LAN/Wi‑Fi").setView(box).setNegativeButton("Stop sharing"){_,_->s.stop()}.setOnCancelListener{s.stop()}.show()}.onFailure{toast("Sharing failed: ${it.message}")}}
    private fun confirmClear(){AlertDialog.Builder(this).setTitle("Clear page?").setMessage("Undo can recover removed objects until the app is closed.").setPositiveButton("Clear"){_,_->board.clearPage()}.setNegativeButton("Cancel",null).show()}
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
    private val Int.dp:Int get()=(this*resources.displayMetrics.density).toInt()
}
