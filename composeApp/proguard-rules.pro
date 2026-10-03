# R8 rules for the release build. Libraries ship consumer rules; these cover reflection-based gaps.
# Add -keep rules here only when the release smoke test (docs/RELEASING.md) shows a runtime failure.

# kotlinx.serialization: keep generated serializers and companions of @Serializable classes
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations, EnclosingMethod
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-keep,includedescriptorclasses class com.habitsheet.**$$serializer { *; }

# SQLDelight: generated database/query classes are instantiated reflectively by the driver schema
-keep class com.habitsheet.database.** { *; }

# Ktor + OkHttp: optional TLS providers and slf4j are referenced but not bundled
-dontwarn org.slf4j.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
-dontwarn io.ktor.**
-keep class io.ktor.client.engine.okhttp.OkHttpEngineContainer { *; }
-keepnames class io.ktor.client.HttpClientEngineContainer

# Play Services Auth (Google sign-in): keep its own consumer rules authoritative
-dontwarn com.google.android.gms.**

# Compose Multiplatform resources: generated accessor class
-keep class com.habitsheet.resources.** { *; }

# Android components referenced from the manifest are kept by AGP; widget actions are string-based.
