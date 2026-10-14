# The app's own classes: lesson files, room protocol and settings are (de)serialized by Gson through reflection,
# and views are referenced from tests and layouts. Shrinking applies to libraries only.
-keep class com.vuravision.classroom.** { *; }
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# PDFBox-Android loads fonts, CMaps and filters by name.
-keep class com.tom_roush.** { *; }
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
-dontwarn javax.**
-dontwarn java.awt.**

# Local web server, formula parser, QR codes.
-keep class fi.iki.elonen.** { *; }
-keep class net.objecthunter.exp4j.** { *; }
-dontwarn org.slf4j.**
