plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.zhijing.data"
    compileSdk = 36
    buildToolsVersion = "36.1.0"

    defaultConfig {
        minSdk = 33

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    // Robolectric 需要访问合并后的资源与 manifest 才能在 JVM 上跑 Room DAO 测试。
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// 导出 Room schema：迁移测试要拿历史版本的真实建表语句重建旧库，
// 手抄 DDL 迟早会和实体注解漂移，所以让它由编译器生成。
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    api("androidx.room:room-runtime:2.6.1")
    api("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Repository 对外暴露 Flow / suspend，属于 api 契约，需传递给调用方。
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.1.5")

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    implementation(kotlin("test"))
}
