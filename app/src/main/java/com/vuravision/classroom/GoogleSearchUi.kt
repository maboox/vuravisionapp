package com.vuravision.classroom

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.*
import android.webkit.*
import android.widget.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Ask-each-time and ambiguous recognition use an editable review. */
class GoogleSearchReview(private val activity:Activity) {
    private fun tr(en:String,fa:String)=if(activity.resources.configuration.locales[0].language=="fa")fa else en
    fun show(candidates:List<String>,floating:(String)->Unit,browser:(String,Boolean)->Unit,target:GoogleSearchSettings=GoogleSearchSettings()){
        val content=activity.column().apply{pad(16)}
        if(target.review)content.addView(activity.infoTitle(tr("Review query","بررسی جست‌وجو"),tr("Edit the recognized word or question before searching. Your writing remains on the board.","کلمه یا سؤال تشخیص‌داده‌شده را پیش از جست‌وجو اصلاح کنید. نوشته روی تخته باقی می‌ماند.")))
        val input=activity.field(candidates.firstOrNull().orEmpty()).apply{inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE;minLines=2;maxLines=5;hint=tr("Word or question","کلمه یا سؤال")};if(target.review || candidates.firstOrNull().orEmpty().length>GoogleSearchQuery.MAX_LENGTH)content.addView(input)
        val choices=activity.row()
        candidates.distinct().take(3).forEach{v->choices.addView(activity.button(v.take(40)){input.setText(v)})}
        if(target.review)content.addView(activity.scrollRow(choices))
        val adjacent=CheckBox(activity).apply{text=tr("Request browser beside the board (if supported)","درخواست مرورگر کنار تخته (اگر نمایشگر پشتیبانی کند)");isChecked=target.adjacent}
        if(target.destination=="ask")content.addView(adjacent)
        val dialog=MaterialAlertDialogBuilder(activity).setTitle(tr("Search Google","جستجو در گوگل"))
            .setView(ScrollView(activity).apply{addView(content)}).setNegativeButton(activity.s("cancel"),null).show().also{Fonts.onShown(it)}
        fun submit(action:(String)->Unit){
            val query=input.text?.toString().orEmpty().trim()
            if(query.isEmpty()||query.length>GoogleSearchQuery.MAX_LENGTH){input.error=tr("Enter 1–4096 characters","متن جستجو باید بین ۱ تا ۴۰۹۶ کاراکتر باشد");return}
            input.error=null;dialog.dismiss();action(query)
        }
        if(target.destination=="ask"){
            content.addView(activity.button(tr("Search in floating window","جستجو در پنجرهٔ شناور"),true){submit(floating)})
            content.addView(activity.button(tr("Open in browser","بازکردن در مرورگر")){submit{browser(it,adjacent.isChecked)}})
        }else content.addView(activity.button(tr("Search","جستجو"),true){submit{if(target.destination=="floating")floating(it)else browser(it,target.adjacent)}})
    }
}

object GoogleSearchBrowser {
    fun intent(query:String,adjacent:Boolean)=Intent(Intent.ACTION_VIEW,Uri.parse(GoogleSearchQuery.url(query))).apply{
        addCategory(Intent.CATEGORY_BROWSABLE)
        if(adjacent)addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
    }
    fun open(activity:Activity,query:String,adjacent:Boolean){
        try{activity.startActivity(intent(query,adjacent))}catch(_:Exception){
            Toast.makeText(activity,if(activity.resources.configuration.locales[0].language=="fa")"مرورگر در دسترس نیست؛ پنجرهٔ شناور را امتحان کنید."else"No browser is available. Try the floating window.",Toast.LENGTH_LONG).show()
        }
    }
}

/** One interactive in-app window; WebView exists only while the window is open. */
class GoogleSearchWindow(private val activity:Activity,private val host:FrameLayout) {
    private var panel:LinearLayout?=null
    private var web:WebView?=null
    private var currentQuery=""
    private var bounds=SearchWindowBounds(0,0,600,500)
    private var expanded=false
    private var compact=bounds
    private val layoutListener=View.OnLayoutChangeListener{_,l,t,r,b,ol,ot,or,ob->if(r-l!=or-ol||b-t!=ob-ot)place(if(expanded)SearchWindowBounds(0,0,r-l,b-t)else bounds)}
    private fun tr(en:String,fa:String)=if(activity.resources.configuration.locales[0].language=="fa")fa else en

    fun open(query:String){
        close();currentQuery=query
        val url=GoogleSearchQuery.url(query)
        val box=activity.column().apply{background=rounded(SURFACE,activity.dp(12).toFloat(),OUTLINE);elevation=activity.dp(10).toFloat();isClickable=true;clipToOutline=true}
        panel=box
        val heading=activity.row()
        val handle=activity.label(tr("Google · drag to move","گوگل · برای جابه‌جایی بکشید"),14f,NAVY,true).apply{minHeight=activity.dp(48);gravity=Gravity.CENTER_VERTICAL;setPadding(activity.dp(10),0,0,0)}
        heading.addView(handle,LinearLayout.LayoutParams(0,activity.dp(48),1f))
        fun control(key:String,title:String,action:()->Unit)=WorkspaceIcon(activity,key,title,false,action)
        heading.addView(control("fullscreen",tr("Resize search window","تغییر اندازهٔ پنجرهٔ جستجو")){
            if(!expanded){compact=bounds;place(SearchWindowBounds(0,0,host.width,host.height));expanded=true}
            else{expanded=false;place(compact)}
        })
        heading.addView(control("share",tr("Open search in browser","بازکردن جستجو در مرورگر")){val q=currentQuery;close();GoogleSearchBrowser.open(activity,q,false)})
        heading.addView(control("close",activity.s("close")){close()});box.addView(heading)
        val navigation=activity.row()
        val state=activity.label(tr("Loading…","در حال بارگذاری…"),12f,MUTED).apply{maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END}
        navigation.addView(control("previous",tr("Back","بازگشت")){web?.let{if(it.canGoBack())it.goBack()}})
        navigation.addView(control("convert",tr("Reload","بارگذاری دوباره")){web?.reload()})
        navigation.addView(state,LinearLayout.LayoutParams(0,activity.dp(48),1f));box.addView(navigation)
        val browser=try{WebView(activity)}catch(_:Exception){
            close();Toast.makeText(activity,tr("WebView is unavailable; opening the browser","WebView در دسترس نیست؛ مرورگر باز می‌شود"),Toast.LENGTH_LONG).show();GoogleSearchBrowser.open(activity,query,false);return
        }
        web=browser
        browser.settings.apply{
            javaScriptEnabled=true;domStorageEnabled=true
            allowFileAccess=false;allowContentAccess=false
            mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically=false;setSupportMultipleWindows(false)
            mediaPlaybackRequiresUserGesture=true;setGeolocationEnabled(false)
        }
        browser.webChromeClient=object:WebChromeClient(){override fun onPermissionRequest(request:PermissionRequest){request.deny()}}
        browser.webViewClient=object:WebViewClient(){
            override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean{
                val allowed=request.url.scheme=="https" && !request.url.host.isNullOrBlank()
                if(!allowed)state.text=tr("Use a browser to open this link","برای این لینک از مرورگر استفاده کنید")
                return !allowed
            }
            override fun onPageStarted(view:WebView,url:String?,favicon:android.graphics.Bitmap?){state.text=tr("Loading…","در حال بارگذاری…")}
            override fun onPageFinished(view:WebView,url:String?){state.text=Uri.parse(url.orEmpty()).host.orEmpty()}
            override fun onReceivedError(view:WebView,request:WebResourceRequest,error:WebResourceError){if(request.isForMainFrame)state.text=tr("Could not load. Check internet and reload.","بارگذاری نشد؛ اینترنت را بررسی و دوباره بارگذاری کنید.")}
            override fun onReceivedSslError(view:WebView,handler:SslErrorHandler,error:android.net.http.SslError){handler.cancel();state.text=tr("Secure connection failed","اتصال امن برقرار نشد")}
            override fun onRenderProcessGone(view:WebView,detail:RenderProcessGoneDetail):Boolean{
                if(web===view){close();Toast.makeText(activity,tr("Search window closed. Try again or use your browser.","پنجرهٔ جستجو بسته شد؛ دوباره تلاش کنید یا مرورگر را باز کنید."),Toast.LENGTH_LONG).show()};return true
            }
        }
        box.addView(browser,LinearLayout.LayoutParams(-1,0,1f))
        val resize=activity.label(tr("Drag to resize ↘","برای تغییر اندازه بکشید ↘"),12f,MUTED).apply{gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL;minHeight=activity.dp(40);setPadding(0,0,activity.dp(12),0)}
        box.addView(resize)
        fun gesture(target:View,resizing:Boolean){
            var startX=0f;var startY=0f;var initial=bounds
            target.setOnTouchListener{v,event->
                when(event.actionMasked){
                    MotionEvent.ACTION_DOWN->{startX=event.rawX;startY=event.rawY;initial=bounds;true}
                    MotionEvent.ACTION_MOVE->{if(expanded&&!resizing)return@setOnTouchListener true;expanded=false;val dx=(event.rawX-startX).toInt();val dy=(event.rawY-startY).toInt();place(if(resizing)initial.copy(width=initial.width+dx,height=initial.height+dy)else initial.copy(x=initial.x+dx,y=initial.y+dy));true}
                    MotionEvent.ACTION_UP->{v.performClick();true}
                    MotionEvent.ACTION_CANCEL->true
                    else->false
                }
            }
        }
        gesture(handle,false);gesture(resize,true)
        bounds=SearchWindowBounds((host.width-activity.dp(620)-activity.dp(16)).coerceAtLeast(0),activity.dp(76),activity.dp(620),activity.dp(510))
        host.addView(box,FrameLayout.LayoutParams(1,1,Gravity.TOP or Gravity.LEFT));host.addOnLayoutChangeListener(layoutListener)
        place(bounds);host.post{if(panel===box)place(bounds)}
        browser.loadUrl(url)
    }
    private fun place(preferred:SearchWindowBounds){
        val box=panel?:return
        if(host.width<=0||host.height<=0)return
        val fitted=preferred.fit(host.width,host.height,activity.dp(280),activity.dp(240));bounds=fitted
        val p=box.layoutParams as FrameLayout.LayoutParams
        if(p.width==fitted.width&&p.height==fitted.height&&p.leftMargin==fitted.x&&p.topMargin==fitted.y)return
        p.width=fitted.width;p.height=fitted.height;p.leftMargin=fitted.x;p.topMargin=fitted.y;box.layoutParams=p
    }
    fun close(){
        host.removeOnLayoutChangeListener(layoutListener)
        panel?.let{host.removeView(it);it.removeAllViews()};panel=null
        web?.let{it.stopLoading();(it.parent as? android.view.ViewGroup)?.removeView(it);it.webChromeClient=null;it.webViewClient=WebViewClient();it.destroy()};web=null
        expanded=false;currentQuery=""
    }
}
