package com.vuravision.classroom

import android.content.Context
import android.os.Build
import com.google.gson.Gson

object DeviceProfiles {
    fun load(context:Context):DeviceProfile {
        val prefs=context.getSharedPreferences("vura",0)
        try{prefs.getString("deviceProfile",null)?.let{return Gson().fromJson(it,DeviceProfile::class.java).also{it.validate()}}}catch(_:Exception){}
        return DeviceProfile(name=Build.MODEL.take(60).ifBlank{"VuraVision"}).also{save(context,it)}
    }
    fun save(context:Context,profile:DeviceProfile){profile.validate();context.getSharedPreferences("vura",0).edit().putString("deviceProfile",Gson().toJson(profile)).apply()}
}
