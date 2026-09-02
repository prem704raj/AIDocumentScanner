# Preserve generic signatures and runtime-visible annotations used by libraries.
-keepattributes Signature
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

# OpenCV JNI/native bridge. Keep conservatively until a native dependency migration is done.
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# Temporary iText 5 compatibility rule until the release licensing/replacement gate is resolved.
-keep class com.itextpdf.** { *; }
-dontwarn com.itextpdf.**
-dontwarn javax.xml.crypto.**
-dontwarn org.apache.jcp.**
-dontwarn org.apache.xml.security.**
-dontwarn org.bouncycastle.**

# Preserve native method names for any JNI bridge.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# DO NOT restore broad rules such as:
# -keep class com.example.aidocumentscanner.** { *; }
# -keep class androidx.compose.** { *; }
# -keep class coil.** { *; }
# -keep class com.google.mlkit.** { *; }