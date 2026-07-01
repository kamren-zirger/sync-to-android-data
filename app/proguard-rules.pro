# Add any project-specific ProGuard rules here.
# By default, unused code and resources are removed during release builds.

# Keep Room generated classes
-keep class * extends androidx.room.RoomDatabase
-keep class com.kamrenzirger.synctoandroiddata.data.** { *; }

# Keep Shizuku and AIDL related classes
-keep class com.kamrenzirger.synctoandroiddata.ISyncService { *; }
-keep class com.kamrenzirger.synctoandroiddata.service.UserService { *; }
-keep class rikka.shizuku.** { *; }
