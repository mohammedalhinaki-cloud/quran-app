# Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# Media3 / ExoPlayer
-dontwarn androidx.media3.**

# Kotlin coroutines
-dontwarn kotlinx.coroutines.**

# Keep application entry points
-keep class com.borhji.mushaf.MushafApp { *; }
-keep class com.borhji.mushaf.MainActivity { *; }
-keep class com.borhji.mushaf.playback.QuranPlaybackService { *; }
