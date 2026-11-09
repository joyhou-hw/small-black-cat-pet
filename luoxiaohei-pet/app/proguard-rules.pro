# ── Kotlin Serialization ──
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.luoxiaohei.pet.**$$serializer { *; }
-keepclassmembers class com.luoxiaohei.pet.** {
    *** Companion;
}
-keepclasseswithmembers class com.luoxiaohei.pet.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Room ──
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# ── OkHttp ──
-dontwarn okhttp3.**
-dontwarn okio.**

# ── ML Kit ──
-keep class com.google.mlkit.** { *; }

# ── Security Crypto ──
-keep class androidx.security.crypto.** { *; }

# ── Retrofit ──
-keepattributes Signature, Exceptions
-keep class retrofit2.** { *; }
