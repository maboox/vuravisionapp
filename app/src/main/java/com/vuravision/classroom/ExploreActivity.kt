package com.vuravision.classroom
import android.app.*
import android.content.*
import android.graphics.*
import android.os.Bundle
import android.view.*
import android.webkit.*
import android.widget.*
/** Only packaged, trusted documents; no network, file access or JavaScript bridge. */
class ExploreActivity:Activity(){
 private lateinit var web:WebView
 override fun attachBaseContext(base:Context){val config=android.content.res.Configuration(base.resources.configuration);config.setLocale(java.util.Locale(base.getSharedPreferences("vura",0).getString("language","en")?:"en"));super.attachBaseContext(base.createConfigurationContext(config))}
 override fun onCreate(state:Bundle?){super.onCreate(state);window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  val lab=intent.getBooleanExtra("lab",false);val root=column().apply{setBackgroundColor(NAVY);fitsSystemWindows=true};setContentView(root)
  val bar=row().apply{pad(8)};bar.addView(label(if(lab)tr("Discovery lab · 68","آزمایشگاه اکتشاف · ۶۸")else tr("Arcade · 49","بازی‌ها · ۴۹"),18f,Color.WHITE,true),LinearLayout.LayoutParams(0,-2,1f))
  bar.addView(button(s("close")){finish()});root.addView(bar)
  web=WebView(this);root.addView(web,LinearLayout.LayoutParams(-1,0,1f))
  web.settings.apply{javaScriptEnabled=true;domStorageEnabled=true;allowFileAccess=false;allowContentAccess=false;blockNetworkLoads=true;setSupportMultipleWindows(false);mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW}
  web.webViewClient=object:WebViewClient(){override fun shouldOverrideUrlLoading(v:WebView,r:WebResourceRequest)=true}
  val language=getSharedPreferences("vura",0).getString("language","en")?:"en"
  val name=if(lab)"lab"else"arcade";val html=assets.open("$name.html").bufferedReader().use{it.readText()}
  web.loadDataWithBaseURL("https://appassets.androidplatform.net/$name.html?lang=$language",html,"text/html","UTF-8",null)
  if(lab){bar.addView(button(tr("To board","افزودن به تخته")){web.evaluateJavascript("window.vuraSnapshot ? window.vuraSnapshot() : null"){raw->try{
   val data=org.json.JSONTokener(raw).nextValue()as? String?:return@evaluateJavascript
   require(data.startsWith("data:image/png;base64,")&&data.length<12000000)
   val bytes=android.util.Base64.decode(data.substringAfter(','),android.util.Base64.DEFAULT)
   val out=java.io.File(cacheDir,"lab-snapshot.png");out.writeBytes(bytes);setResult(RESULT_OK,Intent().putExtra("snapshot",true));finish()
  }catch(_:Exception){Toast.makeText(this,s("error"),Toast.LENGTH_SHORT).show()}}})}
 }
 override fun onPause(){if(::web.isInitialized){web.onPause();web.pauseTimers()};super.onPause()}
 override fun onResume(){super.onResume();if(::web.isInitialized){web.onResume();web.resumeTimers()}}
 override fun onDestroy(){if(::web.isInitialized){web.resumeTimers();web.stopLoading();(web.parent as? android.view.ViewGroup)?.removeView(web);web.destroy()};super.onDestroy()}
}
