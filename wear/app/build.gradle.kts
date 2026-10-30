import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import javax.inject.Inject
import org.gradle.process.ExecOperations

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Embedded Node.js that runs the Zalo server on the watch (https://github.com/nodejs-mobile/nodejs-mobile).
val nodeMobileVersion = "v18.20.4"
val nodeMobileSha256 = "bd7321eaa1a7602fbe0bb87302df2d79d87835cf4363fbdd17c350dbb485c2af"
val nodeMobileDir = layout.buildDirectory.dir("nodejs-mobile")

android {
    namespace = "com.watchhrept.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.watchhrept.wear"
        minSdk = 30 // Wear OS 3
        targetSdk = 34
        versionCode = 2
        versionName = "0.2.0"

        externalNativeBuild {
            cmake {
                arguments += "-DNODE_MOBILE_DIR=${nodeMobileDir.get().asFile.absolutePath}"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    // libnode.so is ~60 MB per CPU type, so build one APK per type instead of a universal one.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    packaging {
        jniLibs {
            // Compress native libraries in the APK (extracted on install) to keep the download small.
            useLegacyPackaging = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sideload-only app: sign release builds with the debug key unless you configure your own.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

/** Downloads the nodejs-mobile release, checks its hash, and extracts libnode.so and the headers. */
abstract class FetchNodeMobileTask : DefaultTask() {
    @get:Input abstract val version: Property<String>
    @get:Input abstract val sha256: Property<String>
    @get:Internal abstract val cacheDir: DirectoryProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun fetch() {
        val v = version.get()
        val zip = cacheDir.file("nodejs-mobile-$v-android.zip").get().asFile
        if (!zip.exists() || sha256Of(zip) != sha256.get()) {
            zip.parentFile.mkdirs()
            val part = File(zip.path + ".part")
            val url = "https://github.com/nodejs-mobile/nodejs-mobile/releases/download/$v/nodejs-mobile-$v-android.zip"
            logger.lifecycle("Downloading $url")
            URI(url).toURL().openStream().use { input -> part.outputStream().use { input.copyTo(it) } }
            val actual = sha256Of(part)
            if (actual != sha256.get()) {
                part.delete()
                throw GradleException("nodejs-mobile download has SHA-256 $actual, expected ${sha256.get()}")
            }
            part.renameTo(zip)
        }

        val out = outputDir.get().asFile
        out.deleteRecursively()
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                val keep = !entry.isDirectory &&
                    ((entry.name.startsWith("bin/") && entry.name.endsWith("/libnode.so")) || entry.name.startsWith("include/"))
                if (!keep) continue
                val target = File(out, entry.name)
                require(target.canonicalPath.startsWith(out.canonicalPath)) { "Bad zip entry ${entry.name}" }
                target.parentFile.mkdirs()
                target.outputStream().use { zin.copyTo(it) }
            }
        }
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/** Bundles the server (../src) into one file with esbuild and stages it as an app asset. */
abstract class BundleServerTask : DefaultTask() {
    @get:Internal abstract val repoRoot: DirectoryProperty
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val serverSources: ConfigurableFileCollection
    @get:OutputDirectory abstract val outputDir: DirectoryProperty
    @get:Inject abstract val execOps: ExecOperations

    @TaskAction
    fun bundle() {
        val root = repoRoot.get().asFile
        val npm = if (System.getProperty("os.name").startsWith("Windows")) "npm.cmd" else "npm"
        execOps.exec {
            workingDir = root
            commandLine(npm, "run", "--silent", "bundle")
        }
        val target = File(outputDir.get().asFile, "server/server.mjs")
        target.parentFile.mkdirs()
        File(root, "dist/server.bundle.mjs").copyTo(target, overwrite = true)
    }
}

val fetchNodeMobile = tasks.register<FetchNodeMobileTask>("fetchNodeMobile") {
    version.set(nodeMobileVersion)
    sha256.set(nodeMobileSha256)
    cacheDir.set(rootProject.layout.projectDirectory.dir(".nodejs-mobile"))
    outputDir.set(nodeMobileDir)
}

// CMake reads the headers and libnode.so from the extracted release.
tasks.configureEach {
    if (name.startsWith("configureCMake") || name.startsWith("buildCMake")) dependsOn(fetchNodeMobile)
}

androidComponents {
    onVariants { variant ->
        val bundle = tasks.register<BundleServerTask>("bundleServer${variant.name.replaceFirstChar { it.uppercase() }}") {
            val repo = rootProject.layout.projectDirectory.dir("..")
            repoRoot.set(repo)
            serverSources.from(repo.dir("src"), repo.file("package.json"), repo.file("package-lock.json"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(bundle, BundleServerTask::outputDir)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.wear.compose:compose-material:1.4.0")
    implementation("androidx.wear.compose:compose-foundation:1.4.0")
    implementation("androidx.wear.compose:compose-navigation:1.4.0")
    implementation("androidx.wear:wear-input:1.1.0")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
}
