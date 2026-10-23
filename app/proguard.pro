# JEBML uses reflection
-keepclassmembers class * extends org.ebml.Element {
    <init>(...);
}

# Glance finds the widgets to update by the class name of their GlanceAppWidget. If R8 merges
# NowPlayingWidget and ShelfWidget into one class, updating one turns the other into it.
-keep class * extends androidx.glance.appwidget.GlanceAppWidget

# The sherpa-onnx native library reads its config classes and their fields by name
-keep class com.k2fsa.sherpa.onnx.** { *; }

# Optional compression formats of commons-compress that the voice download doesn't use
-dontwarn org.apache.commons.compress.**
-dontwarn org.tukaani.xz.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn io.airlift.compress.**
-dontwarn org.objectweb.asm.**
