# SOSRing R8 rules

# Keep JNI native method names (MapLibre and others bind to these)
-keepclasseswithmembernames class * { native <methods>; }

# MapLibre is native/JNI heavy; keep its surface and silence missing-class warnings
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**

# OkHttp / Okio reference optional platform providers not present on Android
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ZXing
-dontwarn com.google.zxing.**
