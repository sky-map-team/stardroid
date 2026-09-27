import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.google.android.stardroid.buildlogic.RoboVmBackportTransform
import groovy.json.JsonSlurper
import org.robovm.gradle.tasks.AbstractRoboVMTask

// Experimental iOS shell: the pure modules plus a small UIKit front end, AOT-compiled to a native
// iOS app by MobiVM (the maintained RoboVM fork libGDX ships on). See ios/README.md.
//
// Compiling Kotlin needs nothing Apple-specific, so `./gradlew :ios:build` runs anywhere (and in
// CI). The RoboVM tasks — launchIPhoneSimulator, launchIOSDevice, createIPA — need macOS + Xcode.
plugins {
    // No Android here, and the convention supplies exactly what this module needs: the JVM
    // toolchain, ktlint, the JUnit5 stack and the RoboVM-safe `-Xstring-concat=inline` flag.
    id("skymap.pure-kotlin")
    alias(libs.plugins.shadow)
    alias(libs.plugins.robovm)
}

val javaTimeBackport: Configuration by configurations.creating

/**
 * Every jar on the runtime classpath — which is what the RoboVM plugin compiles — passes through
 * [RoboVmBackportTransform], redirecting Java 8 statics RoboVM lacks to `compat/RoboVmBackports`.
 * The standard artifact-transform recipe: jars start out `false`, the classpath asks for `true`.
 */
val roboVmBackported =
    Attribute.of(
        "com.google.android.stardroid.robovm-backported",
        Boolean::class.javaObjectType,
    )

dependencies {
    attributesSchema { attribute(roboVmBackported) }
    artifactTypes.getByName("jar") { attributes.attribute(roboVmBackported, false) }
    registerTransform(RoboVmBackportTransform::class) {
        from.attribute(
            roboVmBackported,
            false,
        ).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
        to.attribute(
            roboVmBackported,
            true,
        ).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
    }
}

configurations.runtimeClasspath { attributes { attribute(roboVmBackported, true) } }

dependencies {
    implementation(project(":core:astronomy"))
    implementation(project(":render:api"))

    // RoboVM puts its runtime on the *boot* classpath itself; it is only needed here to compile.
    compileOnly(libs.robovm.rt)
    implementation(libs.robovm.cocoatouch)

    javaTimeBackport(libs.threetenbp)
}

/**
 * RoboVM's class library (Android's libcore of the Java 7 era) has no `java.time`, but
 * kotlinx-datetime's JVM artifact — the time currency of `:core:astronomy` — is built on it.
 * ThreeTen-BP is the JSR-310 reference backport with the same API, so relocating
 * `org.threeten.bp` to `java.time` yields a drop-in `java.time` that robovm.xml appends to the
 * RoboVM bootclasspath. Java 9+ additions to java.time are absent; `verifyJavaTimeCoverage`
 * checks nothing on the app classpath needs one.
 */
val javaTimeJar =
    tasks.register<ShadowJar>("javaTimeJar") {
        description = "Builds ThreeTen-BP relocated to java.time for the RoboVM bootclasspath."
        configurations.set(listOf(javaTimeBackport))
        archiveFileName.set("java-time-backport.jar")
        destinationDirectory.set(layout.buildDirectory.dir("robovm-boot"))
        relocate("org.threeten.bp", "java.time")
        // Also renames META-INF/services/org.threeten.bp.zone.ZoneRulesProvider, through which
        // the zone-rules provider (and its relocated java/time/TZDB.dat) is discovered.
        mergeServiceFiles()
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF")
    }

// The Shadow plugin is here for javaTimeJar only. Its default fat-jar task, which `build` (and so
// every RoboVM task) depends on, resolves the backport-transformed runtimeClasspath before the
// project jars exist, failing the build with "Could not determine the dependencies of task".
tasks.named<ShadowJar>("shadowJar") {
    enabled = false
    configurations.set(emptyList())
}

/**
 * Bundles the star catalog and the IAU constellation figures from `source-data/` as app
 * resources. The JSON figures are flattened to CSV here at build time, so the app needs no JSON
 * parser at run time (and no Foundation bridging to get one).
 */
abstract class BundleSkyDataTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val starsCsv: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val constellationsJson: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun bundle() {
        val out = outputDir.get().asFile
        starsCsv.get().asFile.copyTo(out.resolve("stars.csv"), overwrite = true)

        @Suppress("UNCHECKED_CAST")
        val root = JsonSlurper().parse(constellationsJson.get().asFile) as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val figures = root["constellations"] as List<Map<String, Any?>>
        out.resolve("constellations.csv").bufferedWriter().use { w ->
            w.write("id,stroke,ra_deg,dec_deg\n")
            for (figure in figures) {
                val id = figure["id"]

                @Suppress("UNCHECKED_CAST")
                val strokes = figure["strokes"] as List<List<List<Number>>>
                strokes.forEachIndexed { index, stroke ->
                    for ((ra, dec) in stroke) w.write("$id,$index,$ra,$dec\n")
                }
            }
        }
    }
}

val bundleSkyData =
    tasks.register<BundleSkyDataTask>("bundleSkyData") {
        val sourceData = rootProject.layout.projectDirectory.dir("source-data")
        starsCsv.set(sourceData.file("stars.csv"))
        constellationsJson.set(sourceData.file("constellations/iau.json"))
        outputDir.set(layout.buildDirectory.dir("generated/ios-resources"))
    }

// Every RoboVM task reads robovm.xml, which references both generated outputs.
tasks.withType<AbstractRoboVMTask>().configureEach { dependsOn(javaTimeJar, bundleSkyData) }

// The plugin's defaults, spelled out: device builds are arm64 only (32-bit iOS is long gone).
robovm {
    archs = "arm64"
}

// ---- checkRoboVmApi: the Mac-free half of "does this build for iOS" -------------------------
//
// RoboVM's class library lacks every Java 8+ API, and the compiler only flags missing classes —
// a missing method fails at run time on the device. The checker (src/apiCheck) walks the call
// graph from the project's classes and resolves each JDK reference against the real RoboVM
// runtime jar, so `./gradlew check` catches Java 8+ API use in the shared modules on Linux CI.

val apiCheck: SourceSet by sourceSets.creating
val robovmBootClasspath: Configuration by configurations.creating

dependencies {
    "apiCheckImplementation"(libs.asm.tree)
    robovmBootClasspath(libs.robovm.rt)
}

abstract class CheckRoboVmApiTask : JavaExec() {
    @get:Classpath
    abstract val bootClasspath: ConfigurableFileCollection

    @get:Classpath
    abstract val appClasspath: ConfigurableFileCollection

    @get:Input
    abstract val rootPackages: ListProperty<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val allowlist: RegularFileProperty

    @get:OutputFile
    abstract val report: RegularFileProperty

    init {
        argumentProviders.add(
            CommandLineArgumentProvider {
                listOf(
                    bootClasspath.asPath,
                    appClasspath.asPath,
                    rootPackages.get().joinToString(","),
                    allowlist.get().asFile.absolutePath,
                    report.get().asFile.absolutePath,
                )
            },
        )
    }
}

val checkRoboVmApi =
    tasks.register<CheckRoboVmApiTask>("checkRoboVmApi") {
        group = "verification"
        description = "Checks every JDK API reachable from the app exists in RoboVM's runtime."
        classpath = apiCheck.runtimeClasspath
        mainClass.set("com.google.android.stardroid.ios.apicheck.RoboVmApiCheckKt")
        bootClasspath.from(robovmBootClasspath, javaTimeJar)
        appClasspath.from(configurations.runtimeClasspath, sourceSets.main.map { it.output })
        rootPackages.set(listOf("com/google/android/stardroid/"))
        allowlist.set(layout.projectDirectory.file("robovm-api-allowlist.txt"))
        report.set(layout.buildDirectory.file("reports/robovm-api-check.txt"))
    }

tasks.named("check") { dependsOn(checkRoboVmApi) }
