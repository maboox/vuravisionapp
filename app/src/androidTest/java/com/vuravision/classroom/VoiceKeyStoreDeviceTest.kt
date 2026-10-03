package com.vuravision.classroom

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class VoiceKeyStoreDeviceTest {
    @Test fun apiKeyRoundTripsThroughKeystoreWithoutPlaintextPreference(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val scope="voice_test_"+newId()
        val vault=VoiceKeyStore(context,scope)
        val fake="AIzaSyDeviceTestOnlyFakeKey0123456789012"
        try{
            assertFalse(vault.hasKey());vault.save(fake)
            assertTrue(vault.hasKey());assertEquals(fake,vault.read())
            val prefs=context.getSharedPreferences(scope,Context.MODE_PRIVATE)
            assertFalse(prefs.all.values.any{it.toString().contains(fake)})
            val first=prefs.getString("key",null);vault.save(fake)
            assertNotEquals(first,prefs.getString("key",null)) // fresh GCM IV per save
            val longCredential="new-format:"+"abc-_.+=/".repeat(40)
            vault.save("\u200F\""+longCredential+"\"\u200E")
            assertEquals(longCredential,vault.read())
            assertFalse(prefs.all.values.any{it.toString().contains(longCredential)})
            try{vault.save("part\r\nx-goog-api-key:other");fail("Unsafe input must be rejected")}catch(_:IllegalArgumentException){}
            assertEquals(longCredential,vault.read()) // failed input preserves the saved key
            vault.clear();assertFalse(vault.hasKey());assertEquals("",vault.read())
        }finally{
            context.getSharedPreferences(scope,Context.MODE_PRIVATE).edit().clear().commit()
            KeyStore.getInstance("AndroidKeyStore").apply{load(null);deleteEntry("vura_gemini_voice_"+scope)}
        }
    }
}
