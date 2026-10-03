package com.vuravision.classroom

import org.junit.Test
import org.junit.Assert.*
import java.net.URI
import java.net.URLDecoder

class GoogleSearchQueryTest {
    @Test fun persianQuestionAndMixedMathRoundTripWithoutExtraParameters(){
        val question="چرا آب می‌جوشد؟ x+y=2 & a+b / 50% #"
        val uri=URI(GoogleSearchQuery.url(question))
        assertEquals("https",uri.scheme);assertEquals("www.google.com",uri.host)
        assertEquals("/search",uri.path);assertNull(uri.fragment)
        assertFalse(uri.rawQuery.contains('&'))
        assertEquals(question,URLDecoder.decode(uri.rawQuery.removePrefix("q="),"UTF-8"))
    }
    @Test fun boundarySpacesAreTrimmedButQuestionContentsRemainIntact(){
        val query="what is gravity?\nGive an example"
        assertEquals(query,URLDecoder.decode(URI(GoogleSearchQuery.url(" $query ")).rawQuery.removePrefix("q="),"UTF-8"))
    }
    @Test fun blankAndOversizedQueriesAreRejected(){
        for(value in listOf("", " \n ", "a".repeat(GoogleSearchQuery.MAX_LENGTH+1))){
            try{GoogleSearchQuery.url(value);fail("Must reject invalid query")}catch(_:IllegalArgumentException){}
        }
        assertTrue(GoogleSearchQuery.url("a".repeat(GoogleSearchQuery.MAX_LENGTH)).startsWith("https://www.google.com/search?q="))
    }
    @Test fun windowMovementAndSizeAreClampedToHost(){
        assertEquals(SearchWindowBounds(300,200,500,400),SearchWindowBounds(999,999,500,400).fit(800,600,280,240))
        assertEquals(SearchWindowBounds(0,0,280,240),SearchWindowBounds(-99,-50,20,40).fit(800,600,280,240))
    }
    @Test fun windowFitsSmallOrResizedScreens(){
        assertEquals(SearchWindowBounds(0,0,200,180),SearchWindowBounds(700,400,600,500).fit(200,180,280,240))
        assertEquals(SearchWindowBounds(0,0,1,1),SearchWindowBounds(10,10,600,500).fit(0,0,280,240))
    }
}
