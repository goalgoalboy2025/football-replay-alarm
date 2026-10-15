-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}

-keep class io.github.dandan.kanqiutixing.data.remote.** { *; }
-keep class io.github.dandan.kanqiutixing.data.config.** { *; }

-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

-keep class * extends dagger.hilt.android.HiltAndroidApp
-keepnames class * extends androidx.hilt.work.HiltWorkerFactory
-keepclasseswithmembers class * {
    @dagger.hilt.android.AndroidEntryPoint <init>(...);
}
