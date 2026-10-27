import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension

private fun Project.catalogVersion(name: String): String =
    extensions.getByType(VersionCatalogsExtension::class.java)
        .named("libs").findVersion(name).get().requiredVersion

val Project.compileSdkLevel get() = catalogVersion("androidCompileSdk").toInt()
val Project.minSdkLevel get() = catalogVersion("androidMinSdk").toInt()
val Project.targetSdkLevel get() = catalogVersion("androidTargetSdk").toInt()
val Project.jdkVersion get() = catalogVersion("jdk").toInt()
val Project.javaVersion: JavaVersion get() = JavaVersion.toVersion(jdkVersion)
