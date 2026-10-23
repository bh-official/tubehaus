// The app is one set of Kotlin sources (common/src) compiled twice: by the Android module
// with Jetpack Compose and by the desktop module with Compose for Desktop. Both expose the
// same androidx.compose.* API, so the screens, the YouTube engine and the library code are
// written once and only the video player differs per platform.
plugins {
    id("com.android.application") version "8.10.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.0" apply false
    id("org.jetbrains.kotlin.jvm") version "2.2.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.0" apply false
    id("org.jetbrains.compose") version "1.8.2" apply false
}
