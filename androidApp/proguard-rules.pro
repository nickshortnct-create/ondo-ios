-dontobfuscate

-keepattributes SourceFile, LineNumberTable
-keepattributes *Annotation*

-keep class * extends androidx.glance.appwidget.GlanceAppWidget { *; }
-keepnames class com.nickshortnct.ondo.**,dev.zt64.** { *; }
