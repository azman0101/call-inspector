# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Release hardening. These rules preserve reflection-based runtime integrations
# while allowing application code and resources to be shrunk and obfuscated.
-keepattributes !SourceFile,!LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class io.sentry.** { *; }
-keep class io.sentry.android.** { *; }
-repackageclasses 'o'
-renamesourcefileattribute SourceFile
-dontnote
-dontwarn javax.annotation.**
-dontwarn org.jetbrains.annotations.**
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}
