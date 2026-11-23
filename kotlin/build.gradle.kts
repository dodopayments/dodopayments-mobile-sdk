import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library") version "8.13.0"
    id("org.jetbrains.kotlin.android") version "2.1.20"
    id("com.vanniktech.maven.publish") version "0.37.0"
}

// Releases are served by JitPack (see ../jitpack.yml), which uses the git tag
// as the version. The defaults match those coordinates so a local
// publishToMavenLocal or composite build resolves the same dependency.
group = findProperty("jitpackGroup")?.toString() ?: "com.github.dodopayments.dodopayments-mobile-sdk"
version = findProperty("jitpackVersion")?.toString() ?: "kotlin-v1.1.2"

android {
    namespace = "com.dodopayments.checkout"
    compileSdk = 35

    defaultConfig {
        minSdk = 23
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// compilerOptions, not android.kotlinOptions: the latter is a hard error from
// Kotlin 2.3 onward, so it would block the next Kotlin Gradle plugin bump.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // androidx.activity + coroutines for the host activity; androidx.browser
    // (Custom Tabs) for the checkout surface. Still deliberately NO networking.
    // Pinned below the versions that pull in androidx.core builds requiring
    // compileSdk 36+/AGP 8.9.1+, since we only use APIs stable since much
    // older releases of both.
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.browser:browser:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Unit tests are pure JVM (java.net.URI, no android.net.Uri) — plain JUnit.
    testImplementation("junit:junit:4.13.2")
}

mavenPublishing {
    coordinates(project.group.toString(), "checkout-android", project.version.toString())

    pom {
        name.set("Dodo Payments Checkout for Android")
        description.set("Open Dodo Payments' hosted checkout in a Custom Tab and get a clean result from one call.")
        url.set("https://github.com/dodopayments/dodopayments-mobile-sdk")
        licenses {
            license {
                name.set("Apache-2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
            }
        }
        developers {
            developer {
                id.set("dodopayments")
                name.set("Dodo Payments")
            }
        }
        scm {
            url.set("https://github.com/dodopayments/dodopayments-mobile-sdk")
            connection.set("scm:git:git://github.com/dodopayments/dodopayments-mobile-sdk.git")
            developerConnection.set("scm:git:ssh://git@github.com/dodopayments/dodopayments-mobile-sdk.git")
        }
    }
}
