# yt-dlp wrapper: uses reflection (Jackson) for its VideoInfo mappers
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
-dontwarn java.beans.**

# The library unpacks Python and FFmpeg with Apache commons-compress, which creates its zip
# "extra field" classes by reflection. Without these rules R8 strips them and the engine
# fails to start in release builds ("The download engine didn't start").
-keep class org.apache.commons.compress.** { *; }
-keep class org.apache.commons.io.** { *; }
-dontwarn org.apache.commons.compress.**
-dontwarn org.apache.commons.io.**
-dontwarn org.tukaani.xz.**
-dontwarn org.brotli.dec.**
-dontwarn com.github.luben.zstd.**

-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
