# Consumer rules for :data.
# Room 的生成代码通过反射在运行时被实例化，开启混淆时需保留其子类。
-keep class * extends androidx.room.RoomDatabase { <init>(); }
