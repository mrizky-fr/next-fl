# kotlinx.serialization keeps generated serializers
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class id.nextfl.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# jump3r desktop encoder wrapper unused
-dontwarn javax.sound.sampled.**
