plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.carrotamap"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.cplink"
        minSdk = 26
        targetSdk = 35
        versionCode = 251030
        versionName = "v251030"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 签名配置
    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = "cplink123456"
            keyAlias = "cplink_key"
            keyPassword = "cplink123456"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            isJniDebuggable = false
            isPseudoLocalesEnabled = false
            isCrunchPngs = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                "security-config.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = true
            isJniDebuggable = false
            isPseudoLocalesEnabled = false
            isCrunchPngs = false
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
    
    // R8优化配置
    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0",
                "META-INF/*.kotlin_module",
                "META-INF/*.version",
                "META-INF/proguard/*",
                "META-INF/com.android.tools/*",
                "META-INF/gradle-plugins/*",
                "META-INF/versions/*",
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                "META-INF/spring.schemas",
                "META-INF/spring.tooling",
                "META-INF/spring.handlers",
                "META-INF/spring.factories",
                "META-INF/spring-autoconfigure-metadata.properties",
                "META-INF/spring-boot-autoconfigure-processor.properties",
                "META-INF/spring-configuration-metadata.json",
                "META-INF/spring-configuration-metadata.properties",
                "META-INF/spring.factories",
                "META-INF/spring.schemas",
                "META-INF/spring.tooling",
                "META-INF/spring.handlers",
                "META-INF/spring-autoconfigure-metadata.properties",
                "META-INF/spring-boot-autoconfigure-processor.properties",
                "META-INF/spring-configuration-metadata.json",
                "META-INF/spring-configuration-metadata.properties"
            )
        }
    }
    
    // 启用资源混淆
    aaptOptions {
        noCompress += setOf("tflite", "lite")
        ignoreAssetsPattern += setOf("!.svn", "!.git", "!.ds_store", "!*.scc", ".*", "<dir>_*", "!CVS", "!thumbs.db", "!picasa.ini", "!*~")
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // HTTP客户端 - 用于导航确认API请求和反馈提交
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    
    // ExoPlayer - 用于视频播放
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    implementation("androidx.media3:media3-common:1.2.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}