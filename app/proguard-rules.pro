# Keep Room entities & DAOs
-keep class com.example.customkeyboard.data.db.** { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
# Keep IME service
-keep class com.example.customkeyboard.service.** { *; }
