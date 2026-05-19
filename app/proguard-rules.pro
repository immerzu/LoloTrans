# ProGuard-Regeln für TranslatorApp

# ML Kit
-keep class com.google.mlkit.** { *; }

# Kotlin
-keepattributes *Annotation*
-keep class kotlin.** { *; }
