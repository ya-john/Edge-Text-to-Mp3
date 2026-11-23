# PDFBox loads fonts, glyph lists and CMaps reflectively and from bundled resources.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
-dontwarn javax.xml.**

# commonmark uses only plain classes, but keep its node visitors intact.
-keep class org.commonmark.** { *; }

# OkHttp ships its own consumer rules; these silence optional platform probes.
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
