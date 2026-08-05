# Room entities and generated implementations are referenced through annotations.
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keepattributes *Annotation*
