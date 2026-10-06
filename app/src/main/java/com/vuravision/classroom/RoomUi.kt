package com.vuravision.classroom

import android.app.Activity
import android.app.Dialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.*
import android.widget.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

/** Native room/profile dialogs share the existing icon, spacing and back navigation. */
class RoomUi(private val activity:Activity,private val rooms:RoomController):AutoCloseable {
    private val c get()=activity
    private val main=Handler(Looper.getMainLooper())
    private val io=Executors.newSingleThreadExecutor()
    private val dialogs=mutableSetOf<Dialog>()
    private var closed=false
    private var scanner:RoomQrScanner?=null
    private var pictureResult:((String)->Unit)?=null
    private var refreshDetails:(()->Unit)?=null
    companion object {const val CAMERA_REQUEST=7810;const val PHOTO_REQUEST=7811;const val QR_IMAGE_REQUEST=7812}
    private fun show(title:String,body:View,back:(()->Unit)?=null):Dialog {
        var dialog:Dialog?=null
        val built=MaterialAlertDialogBuilder(c).setCustomTitle(c.navigationHeading(title,back?.let{{dialog?.dismiss();it()}}))
            .setView(ScrollView(c).apply{addView(body)}).setNegativeButton(c.s("close"),null).create()
        dialog=built;built.setOnCancelListener{back?.invoke()};built.setOnDismissListener{dialogs.remove(built)};built.show();built.window?.setGravity(Gravity.CENTER);Fonts.applyTree(built.window?.decorView?:body);dialogs.add(built);return built
    }
    private fun toast(key:String){Toast.makeText(c,c.s(key),Toast.LENGTH_LONG).show()}
    private fun avatar(profile:DeviceProfile):View {
        if(profile.photo.isNotEmpty())try{val data=Base64.decode(profile.photo,Base64.DEFAULT);val opts=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(data,0,data.size,opts)
            if(opts.outWidth>0&&opts.outHeight>0){opts.inSampleSize=1;while(maxOf(opts.outWidth,opts.outHeight)/opts.inSampleSize>96)opts.inSampleSize*=2;opts.inJustDecodeBounds=false
                val image=BitmapFactory.decodeByteArray(data,0,data.size,opts);if(image!=null)return ImageView(c).apply{setImageBitmap(image);scaleType=ImageView.ScaleType.CENTER_CROP;contentDescription=profile.name}}}catch(_:Exception){}
        return c.label(profile.emoji,24f,profile.color).apply{gravity=Gravity.CENTER;contentDescription=profile.name}
    }
    fun profile(back:(()->Unit)?=null){
        var value=DeviceProfiles.load(c);val body=c.column().apply{pad(16)};val preview=c.row();body.addView(preview)
        fun refresh(){preview.removeAllViews();preview.addView(avatar(value),LinearLayout.LayoutParams(c.dp(56),c.dp(56)));preview.addView(c.label(value.name,18f,value.color,true))}
        refresh();val name=EditText(c).apply{setText(value.name);hint=c.s("profile_name");filters=arrayOf(android.text.InputFilter.LengthFilter(60));isSingleLine=true};body.addView(name)
        body.addView(c.label(c.s("profile_local"),14f))
        body.addView(c.colorPalette(value.color,listOf(0xff4262e8.toInt(),TEAL,ORANGE,0xffba4058.toInt(),0xff7754ad.toInt(),0xff267941.toInt()),null){color->value=value.copy(color=color);refresh()})
        val emoji=c.row();listOf("✏️","🌟","🦊","🌱","🚀","🎨").forEach{mark->emoji.addView(c.button(mark){value=value.copy(emoji=mark,photo="");refresh()})};body.addView(c.scrollRow(emoji))
        body.addView(c.button(c.s("profile_photo")){pictureResult={data->value=value.copy(photo=data);refresh()};pick(PHOTO_REQUEST)})
        body.addView(c.button(c.s("profile_remove_photo")){value=value.copy(photo="");refresh()})
        val dialog=show(c.s("device_profile"),body,back)
        body.addView(c.button(c.s("save"),true){val clean=name.text.toString().trim();try{value=value.copy(name=clean);DeviceProfiles.save(c,value);rooms.refreshPresence();dialog.dismiss();back?.invoke()}catch(_:Exception){toast("profile_name_needed")}})
    }
    fun home(back:(()->Unit)?=null){
        if(rooms.active){details(back);return}
        val body=c.column().apply{pad(16)};val p=DeviceProfiles.load(c);val heading=c.row();heading.addView(avatar(p),LinearLayout.LayoutParams(c.dp(56),c.dp(56)));heading.addView(c.label(p.name,18f,p.color,true));body.addView(heading)
        body.addView(c.label(c.s("room_network_hint"),14f));val dialog=show(c.s("shared_room"),body,back)
        body.addView(c.button(c.s("device_profile")){dialog.dismiss();profile{home(back)}})
        body.addView(c.button(c.s("room_create"),true){dialog.dismiss();create{home(back)}})
        body.addView(c.button(c.s("room_join")){dialog.dismiss();join{home(back)}})
        if(rooms.canResume)body.addView(c.button(c.s("room_resume")){rooms.resume{error->if(error==null){dialog.dismiss();details(back)}else toast(error)}})
    }
    private fun create(back:()->Unit){
        val body=c.column().apply{pad(16)};val name=EditText(c).apply{hint=c.s("room_name");setText(DeviceProfiles.load(c).name);filters=arrayOf(android.text.InputFilter.LengthFilter(60));isSingleLine=true};body.addView(name);body.addView(c.label(c.s("room_host_hint"),14f))
        val dialog=show(c.s("room_create"),body,back);body.addView(c.button(c.s("room_create"),true){try{rooms.host(name.text.toString());dialog.dismiss();invite{details(back)}}catch(_:Exception){toast("room_create_failed")}})
    }
    private fun join(back:()->Unit){
        val body=c.column().apply{pad(16)};body.addView(c.label(c.s("room_network_hint"),14f));val found=c.column();body.addView(found)
        val manual=EditText(c).apply{hint=c.s("room_address");isSingleLine=true;layoutDirection=View.LAYOUT_DIRECTION_LTR;inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI};body.addView(manual)
        val dialog=show(c.s("room_join"),body,back);dialog.setOnDismissListener{dialogs.remove(dialog);rooms.discovery.stopDiscovery();if(rooms.connecting)rooms.leave()}
        fun connect(address:RoomAddress){if(closed||!dialog.isShowing)return;rooms.discovery.stopDiscovery();rooms.join(address){error->if(error==null){dialog.dismiss();details(back)}else toast("room_connect_failed")}}
        body.addView(c.button(c.s("room_join"),true){try{connect(RoomAddress.parse(manual.text.toString()))}catch(_:Exception){toast("room_invalid_address")}})
        body.addView(c.button(c.s("room_scan_qr")){pictureResult={text->manual.setText(text);try{connect(RoomAddress.parse(text))}catch(_:Exception){toast("room_invalid_address")}};scan()})
        body.addView(c.button(c.s("room_qr_image")){pictureResult={text->manual.setText(text);try{connect(RoomAddress.parse(text))}catch(_:Exception){toast("room_invalid_address")}};pick(QR_IMAGE_REQUEST)})
        val rows=mutableMapOf<String,View>();rooms.discovery.discover({name,address->rows.remove(name)?.let{found.removeView(it)};val b=c.button(name){connect(address)};rows[name]=b;found.addView(b)}, {name->rows.remove(name)?.let{found.removeView(it)}}, {if(dialog.isShowing)found.addView(c.label(c.s("room_discovery_hint"),14f))})
    }
    fun details(back:(()->Unit)?=null){
        if(!rooms.active){home(back);return};val body=c.column().apply{pad(16)};val status=c.label("",15f);body.addView(status);val members=c.column();body.addView(members);var signature=""
        val dialog=show(c.s("shared_room"),body,back)
        fun refresh(){if(!dialog.isShowing)return;val frame=rooms.frame;status.text=frame.room+" · "+c.s(roleKey(frame.role))+"\n"+when{rooms.message.isNotEmpty()->c.s(rooms.message);!rooms.connected->c.s("room_reconnecting");!frame.approved->c.s("room_waiting");else->c.s("room_connected")}
            val next=frame.members.joinToString{"${it.profile.id}:${it.profile.hashCode()}:${it.role}:${it.approved}:${it.online}"};if(next==signature)return;signature=next;members.removeAllViews()
            frame.members.forEach{member->val row=c.row().apply{pad(4)};row.addView(avatar(member.profile),LinearLayout.LayoutParams(c.dp(48),c.dp(48)))
                row.addView(c.label(member.profile.name+"\n"+c.s(if(!member.approved)"room_waiting"else roleKey(member.role))+" · "+c.s(if(member.online)"room_online"else"room_offline"),14f),LinearLayout.LayoutParams(0,-2,1f))
                if(rooms.hosting&&member.profile.id!=rooms.peer){row.addView(WorkspaceIcon(c,"settings",c.s("room_access")){access(member)});row.addView(WorkspaceIcon(c,"close",c.s("room_remove")){rooms.remove(member.profile.id)})};members.addView(row)}
        }
        refreshDetails=::refresh;dialog.setOnDismissListener{dialogs.remove(dialog);refreshDetails=null};refresh()
        if(rooms.hosting)body.addView(c.button(c.s("room_invite")){dialog.dismiss();invite{details(back)}})
        body.addView(c.button(c.s("device_profile")){dialog.dismiss();profile{details(back)}})
        body.addView(c.button(c.s("room_leave")){MaterialAlertDialogBuilder(c).setTitle(c.s("room_leave")).setMessage(c.s("room_leave_hint")).setNegativeButton(c.s("cancel"),null).setPositiveButton(c.s("room_leave")){_,_->rooms.leave();dialog.dismiss();back?.invoke()}.show()})
    }
    private fun access(member:RoomMember){
        var dialog:Dialog?=null
        dialog=MaterialAlertDialogBuilder(c).setCustomTitle(c.navigationHeading(member.profile.name){dialog?.dismiss()}).setItems(arrayOf(c.s("room_view"),c.s("room_control"),c.s("room_collaborate"))){_,i->rooms.setRole(member.profile.id,RoomRoles.assignable[i])}.setNegativeButton(c.s("cancel"),null).show()
    }
    private fun invite(back:()->Unit){
        val body=c.column().apply{pad(16)};body.addView(c.label(c.s("room_network_hint"),14f));val addresses=rooms.invitations
        if(addresses.isEmpty())body.addView(c.label(c.s("room_no_network"),14f))
        addresses.forEach{address->body.addView(ImageView(c).apply{setImageBitmap(Sharing.qr(address.url));contentDescription=c.s("room_invite");scaleType=ImageView.ScaleType.FIT_CENTER},LinearLayout.LayoutParams(-1,c.dp(240)))
            body.addView(c.label(address.url,13f).apply{setTextIsSelectable(true);layoutDirection=View.LAYOUT_DIRECTION_LTR})
            body.addView(c.button(c.s("room_copy_address")){(c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("VuraVision",address.url));toast("done")})}
        show(c.s("room_invite"),body,back)
    }
    fun refresh(){refreshDetails?.invoke()}
    private fun roleKey(role:String)=when(role){RoomRoles.HOST->"room_host";RoomRoles.CONTROL->"room_control";RoomRoles.COLLAB->"room_collaborate";else->"room_view"}
    private fun pick(code:Int){try{c.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="image/*";addCategory(Intent.CATEGORY_OPENABLE)},code)}catch(_:Exception){toast("room_image_failed")}}
    private fun scan(){if(c.checkSelfPermission(android.Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){c.requestPermissions(arrayOf(android.Manifest.permission.CAMERA),CAMERA_REQUEST);return}
        scanner?.close();scanner=RoomQrScanner(c){text->pictureResult?.invoke(text);scanner=null}.also{it.open()}}
    fun permission(code:Int,results:IntArray):Boolean{if(code!=CAMERA_REQUEST)return false;if(results.firstOrNull()==PackageManager.PERMISSION_GRANTED)scan()else toast("room_camera_needed");return true}
    fun result(code:Int,result:Int,data:Intent?):Boolean{
        if(code !in listOf(PHOTO_REQUEST,QR_IMAGE_REQUEST))return false;val uri=data?.data
        if(result!=Activity.RESULT_OK||uri==null)return true;val callback=pictureResult
        io.execute{try{val out=ByteArrayOutputStream();c.contentResolver.openInputStream(uri)!!.use{boundedCopy(it,out,16L*1024*1024)};val bytes=out.toByteArray();val opt=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,opt);require(opt.outWidth>0&&opt.outHeight>0)
            val edge=if(code==PHOTO_REQUEST)320 else 1600;opt.inSampleSize=1;while(maxOf(opt.outWidth,opt.outHeight)/opt.inSampleSize>edge)opt.inSampleSize*=2;opt.inJustDecodeBounds=false
            val bitmap=requireNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size,opt));val value=if(code==PHOTO_REQUEST){val side=minOf(bitmap.width,bitmap.height);val square=Bitmap.createBitmap(bitmap,(bitmap.width-side)/2,(bitmap.height-side)/2,side,side);val resized=Bitmap.createScaledBitmap(square,160,160,true);val encoded=ByteArrayOutputStream();resized.compress(Bitmap.CompressFormat.JPEG,80,encoded);if(resized!==bitmap)resized.recycle();if(square!==bitmap&&square!==resized)square.recycle();Base64.encodeToString(encoded.toByteArray(),Base64.NO_WRAP)}else{val pixels=IntArray(bitmap.width*bitmap.height);bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height);MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width,bitmap.height,pixels)))).text.also{RoomAddress.parse(it)}}
            bitmap.recycle();main.post{if(!closed)callback?.invoke(value)}
        }catch(_:Exception){main.post{if(!closed)toast("room_image_failed")}}};return true
    }
    fun pause(){scanner?.close();scanner=null}
    override fun close(){closed=true;pause();dialogs.toList().forEach{it.dismiss()};dialogs.clear();io.shutdownNow();main.removeCallbacksAndMessages(null)}
}
