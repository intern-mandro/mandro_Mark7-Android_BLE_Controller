# kotlinx.serialization
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class com.mandro.mark7.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.mandro.mark7.**$$serializer { *; }

# Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }
