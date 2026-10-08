// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.11.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    // KSP 版本必须与 Kotlin 版本严格配对（Kotlin 2.0.21 对应的最高正式版）。
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}