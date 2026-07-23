plugins {
    id("com.android.library") version "8.12.0"
    id("org.jetbrains.kotlin.android") version "2.1.20"
    `maven-publish`
}

group = "com.dodopayments"
version = "1.0.0"

android {
    namespace = "com.dodopayments.checkout"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    // androidx.activity + coroutines for the host activity; androidx.browser
    // (Custom Tabs) for the checkout surface. Still deliberately NO networking.
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.browser:browser:1.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // Unit tests are pure JVM (java.net.URI, no android.net.Uri) — plain JUnit.
    testImplementation("junit:junit:4.13.2")
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.dodopayments"
            artifactId = "checkout-android"
            version = project.version.toString()
            afterEvaluate {
                from(components["release"])
            }
            pom {
                name.set("Dodo Payments Checkout for Android")
                description.set("Open Dodo Payments' hosted checkout in a Custom Tab and get a clean result from one call.")
                url.set("https://github.com/dodopayments/dodopayments-checkout-android")
            }
        }
    }
}
