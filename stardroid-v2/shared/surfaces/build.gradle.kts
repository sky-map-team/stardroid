plugins {
    id("skymap.pure-kmp")
}

// What the notification and widget surfaces decide, shared by Android and iOS: which alert is due
// tonight and when it fires. Posting (channels, WorkManager, UNUserNotificationCenter) stays with
// each platform. See docs/design/ios-platform-surfaces.md section 5.
kotlin {
    sourceSets {
        commonMain.dependencies {
            // TonightSky and SkyEvent appear in the planner's signature.
            api(project(":core:events"))
        }
        commonTest.dependencies {
            implementation(project(":core:testing"))
        }
    }
}
