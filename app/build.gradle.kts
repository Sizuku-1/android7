plugins {
    id("com.android.application")
}

android {
    namespace = "cn.edu.sicnu.cs.txp.android7"
    compileSdk {
        version = release(37)
    }
    defaultConfig {
        applicationId = "cn.edu.sicnu.cs.txp.android7"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
