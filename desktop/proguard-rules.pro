# Shrink only, keep names
-dontobfuscate

# Swing main dispatcher via ServiceLoader
-keep class kotlinx.coroutines.swing.SwingDispatcherFactory { *; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory

# Project JSON serializers
-keepattributes *Annotation*, InnerClasses, Signature
-keepclassmembers class id.nextfl.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class id.nextfl.**$$serializer { *; }

# JNA reads fields reflectively
-keep class com.sun.jna.** { *; }
-keep class * extends com.sun.jna.Structure { *; }
-keep interface * extends com.sun.jna.Library { *; }
-keep class io.github.vinceglb.filekit.** { *; }

# Decoders load tables by name
-keep class javazoom.jl.** { *; }
-keep class de.sciss.jump3r.** { *; }

# Linux-only or optional references
-dontwarn org.freedesktop.dbus.**
-dontwarn com.github.hypfvieh.**
-dontwarn org.slf4j.**
-dontwarn javax.sound.sampled.spi.**
-dontwarn com.sun.jna.**
-dontnote org.freedesktop.dbus.**
