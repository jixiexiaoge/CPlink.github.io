plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.detekt)
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/config/detekt/detekt-config.yml"))
}

android {
    namespace = "com.example.carrotamap"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.cplink"
        minSdk = 26
        targetSdk = 35
        versionCode = 260201
        versionName = "v260201"

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
    
    // 启用资源混淆（使用新的 androidResources API）
    androidResources {
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
    implementation(libs.androidx.compose.material.icons.extended)

    // HTTP客户端 - 用于导航确认API请求和反馈提交
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    
    // ExoPlayer - 用于视频播放
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    implementation("androidx.media3:media3-common:1.2.1")
    
    // ZMQ客户端 - 用于与Comma3设备通信
    implementation("org.zeromq:jeromq:0.5.4")
    
    // Koin依赖注入 - P1 架构优化
    implementation("io.insert-koin:koin-android:3.5.3")  // Koin核心
    implementation("io.insert-koin:koin-androidx-compose:3.5.3")  // Compose集成
    implementation("io.insert-koin:koin-androidx-navigation:3.5.3")  // Navigation集成
    
    // Timber日志库 - P2 代码质量优化
    implementation("com.jakewharton.timber:timber:5.0.1")
    
    // DataStore - P3 功能增强（示例）
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // 测试框架 - P0 优先级优化
    testImplementation(libs.junit)
    testImplementation("com.google.truth:truth:1.1.5")  // Google Truth 断言库
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")  // 协程测试
    testImplementation("io.mockk:mockk:1.13.8")  // Kotlin Mock 框架
    
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation("io.mockk:mockk-android:1.13.8")  // Android Mock 支持
    
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
