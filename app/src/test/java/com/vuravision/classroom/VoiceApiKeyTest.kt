package com.vuravision.classroom

import org.junit.Test
import org.junit.Assert.*

class VoiceApiKeyTest {
    private val fake="AIzaSyFakeKeyForTestsOnly0123456789012"
    @Test fun standardKeyRemainsUnchanged(){
        assertEquals(fake,VoiceApiKey.normalize(fake));assertTrue(VoiceApiKey.isAcceptable(fake))
    }
    @Test fun opaqueLongCredentialIsNotRejectedByOldFormatAssumptions(){
        val value="new-format:"+"aB09_-.+=/".repeat(80)
        assertTrue(value.length>200);assertEquals(value,VoiceApiKey.normalize(value))
        assertTrue(VoiceApiKey.isAcceptable(value))
    }
    @Test fun noProviderPrefixOrMinimumLengthIsAssumed(){
        assertTrue(VoiceApiKey.isAcceptable("opaque-token"))
    }
    @Test fun whitespaceAndDirectionMarksAtPasteBoundariesAreRemoved(){
        assertEquals(fake,VoiceApiKey.normalize("\uFEFF\u200F\u2067 \u00A0\t"+fake+"\u2069\u200E\u200B\n"))
    }
    @Test fun pairedCopyQuotesAreRemoved(){
        for(pair in listOf("\"" to "\"","'" to "'","`" to "`","“" to "”","‘" to "’"))
            assertEquals(fake,VoiceApiKey.normalize(pair.first+" "+fake+" "+pair.second))
    }
    @Test fun interiorBytesAreNeverSilentlyRewritten(){
        for(value in listOf("part one","part\tone","part\none","part\r\nx-goog-api-key:other","part\u200Fone","part\u0000one","فارسی")){
            assertEquals(value,VoiceApiKey.normalize(value));assertFalse(VoiceApiKey.isAcceptable(value))
        }
    }
    @Test fun obviousLinksAreRejected(){
        for(value in listOf("https://aistudio.google.com/apikey","HTTPS://example.com","www.example.com","ftp://example.com"))
            assertFalse(VoiceApiKey.isAcceptable(value))
    }
    @Test fun emptyAndOversizedValuesAreRejected(){
        assertEquals("",VoiceApiKey.normalize(" \u200F\uFEFF"))
        assertEquals("",VoiceApiKey.normalize("\" \""))
        assertFalse(VoiceApiKey.isAcceptable(""))
        assertTrue(VoiceApiKey.isAcceptable("a".repeat(VoiceApiKey.MAX_LENGTH)))
        assertFalse(VoiceApiKey.isAcceptable("a".repeat(VoiceApiKey.MAX_LENGTH+1)))
    }
}
