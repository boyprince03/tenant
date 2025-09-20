plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // Kapt for Room
    id("kotlin-kapt") // 新增這一行！
    //firebase
    id("com.google.gms.google-services")
}

android {
    namespace = "com.stevedaydream.tenantapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.stevedaydream.tenantapp"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
// 【請替換為】
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    // Room
    implementation ("androidx.room:room-runtime:2.6.1")
    kapt ("androidx.room:room-compiler:2.6.1")
    implementation ("androidx.room:room-ktx:2.6.1")
// Navigation
    implementation ("androidx.navigation:navigation-compose:2.7.7")
// Material Design 3
    implementation ("androidx.compose.material3:material3:1.3.2")
// Compose
    implementation ("androidx.compose.ui:ui:1.6.6")
    implementation ("androidx.activity:activity-compose:1.9.0")
// 協程
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
// Google Drive API（同步部分）
    implementation ("com.google.accompanist:accompanist-flowlayout:0.28.0")
//    excel import
    implementation ("net.sourceforge.jexcelapi:jxl:2.6.12")
    implementation ("androidx.compose.material:material-icons-extended:<compose_version>")
    // Import the Firebase BoM
    // 導入 Firebase BOM (Bill of Materials)，它會自動管理 Firebase 套件的版本
    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))

    // 加入 Firebase Cloud Messaging (FCM) 的依賴
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Firebase Authentication - 用於使用者登入註冊
    implementation ("com.google.firebase:firebase-auth-ktx")

    // Firebase Firestore - 用於雲端資料庫
    implementation ("com.google.firebase:firebase-firestore-ktx")

    // 如果您需要非同步操作 (通常都需要)，請確保有這個
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.1")


//    implementation ("com.google.api-client:google-api-client-android:1.33.0")
//    implementation ("com.google.api-client:google-api-client-gson:1.33.0")
//    implementation ("com.google.apis:google-api-services-drive:v3-rev20230710-2.0.0")
//    implementation ("com.google.android.gms:play-services-auth:21.1.0")
}