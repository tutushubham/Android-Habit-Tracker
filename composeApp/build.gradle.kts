import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

kotlin {
    jvmToolchain(17)

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    // Warnings fail the build for production code: every Android and iOS main compilation (which compiles commonMain
    // together with androidMain / iosMain). Not for the shared-metadata compilations, whose only warnings are KLIB
    // resolver notices about duplicate library names in the dependency graph, and not for tests.
    targets.matching { it.platformType != org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.common }.configureEach {
        compilations.matching { it.name in setOf("main", "debug", "release") }.configureEach {
            compileTaskProvider.configure { compilerOptions.allWarningsAsErrors.set(true) }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.backhandler)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.compose.components.resources)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.sqldelight.runtime)
            implementation(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.sqldelight.android.driver)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.google.play.services.auth)
        }
        androidUnitTest.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
            implementation(libs.ktor.client.darwin)
        }
    }
}

// Release signing comes from keystore.properties (gitignored) or HABITSHEET_* env vars. Debug builds need neither.
val releaseSigning: Properties? = run {
    val file = rootProject.file("keystore.properties")
    val props = Properties()
    if (file.exists()) file.inputStream().use { props.load(it) }
    val env = mapOf(
        "storeFile" to "HABITSHEET_STORE_FILE",
        "storePassword" to "HABITSHEET_STORE_PASSWORD",
        "keyAlias" to "HABITSHEET_KEY_ALIAS",
        "keyPassword" to "HABITSHEET_KEY_PASSWORD",
    )
    env.forEach { (key, name) -> System.getenv(name)?.let { props.setProperty(key, it) } }
    if (env.keys.all { props.getProperty(it) != null }) props else null
}

// Renders the commonMain @Preview functions in Android Studio; debug builds only, never shipped.
dependencies {
    debugImplementation(libs.compose.ui.tooling)
}

android {
    namespace = "com.habitsheet.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.habitsheet.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = providers.gradleProperty("appVersionCode").get().toInt()
        versionName = providers.gradleProperty("appVersionName").get()
    }

    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = rootProject.file(releaseSigning.getProperty("storeFile"))
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

sqldelight {
    databases {
        create("HabitsDatabase") {
            packageName.set("com.habitsheet.database")
            // One .db snapshot per released schema version; verifyMigrations opens each, applies the .sqm files up to
            // the current schema and fails the build if the result differs. Never edit or delete a committed snapshot.
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}

compose.resources {
    packageOfResClass = "com.habitsheet.resources"
}

// Generates iosApp/Config/Version.xcconfig from gradle.properties (committed, so Xcode works without Gradle).
val syncIosVersion by tasks.registering {
    group = "release"
    description = "Writes iosApp/Config/Version.xcconfig from appVersionName/appVersionCode."
    val versionName = providers.gradleProperty("appVersionName")
    val versionCode = providers.gradleProperty("appVersionCode")
    val outFile = rootProject.layout.projectDirectory.file("iosApp/Config/Version.xcconfig")
    inputs.property("name", versionName)
    inputs.property("code", versionCode)
    outputs.file(outFile)
    doLast {
        val f = outFile.asFile
        f.parentFile.mkdirs()
        f.writeText(
            listOf(
                "// GENERATED by `./gradlew :composeApp:syncIosVersion` from gradle.properties. Do not edit.",
                "#include? \"Local.xcconfig\"",
                "MARKETING_VERSION = ${versionName.get()}",
                "CURRENT_PROJECT_VERSION = ${versionCode.get()}",
            ).joinToString(separator = "\n", postfix = "\n"),
        )
    }
}

// Fail early and clearly when a release artifact is requested without signing credentials.
val checkReleaseSigning by tasks.registering {
    val configured = releaseSigning != null
    doLast {
        if (!configured) {
            throw GradleException(
                "Release signing is not configured. Create keystore.properties in the repo root " +
                    "(see keystore.properties.example) or set HABITSHEET_STORE_FILE, HABITSHEET_STORE_PASSWORD, " +
                    "HABITSHEET_KEY_ALIAS and HABITSHEET_KEY_PASSWORD. See docs/RELEASING.md.",
            )
        }
    }
}
// Only the tasks that produce a signed release artifact; release unit tests and lint (part of `check`) need no keystore.
val signedReleaseTasks = setOf("assembleRelease", "bundleRelease", "packageRelease", "packageReleaseBundle", "signReleaseBundle")
tasks.matching { it.name in signedReleaseTasks }.configureEach {
    dependsOn(checkReleaseSigning)
}

// --- Code quality (P1-2 step 10): `./gradlew check` runs the tests, Spotless (ktlint) and detekt. ---

// Formatting: ktlint through Spotless, style in /.editorconfig. `./gradlew spotlessApply` fixes what it can.
// Spotless does not pass ktlint_* settings from .editorconfig on to ktlint, so they are listed here.
val ktlintRuleSwitches = mapOf(
    // IntelliJ IDEA's default Kotlin style (what the code was written in), not ktlint's stricter "ktlint_official".
    "ktlint_code_style" to "intellij_idea",
    // Composables are PascalCase functions.
    "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
    // Long single-line Compose modifier chains and parameter lists are kept as written.
    "ktlint_standard_function-signature" to "disabled",
    "ktlint_standard_multiline-expression-wrapping" to "disabled",
    "ktlint_standard_chain-method-continuation" to "disabled",
    // IntelliJ's own import layout (star import from 5 names of one package) is allowed.
    "ktlint_standard_no-wildcard-imports" to "disabled",
)
spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
            .setEditorConfigPath(rootProject.file(".editorconfig"))
            .editorConfigOverride(ktlintRuleSwitches)
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
            .setEditorConfigPath(rootProject.file(".editorconfig"))
            .editorConfigOverride(ktlintRuleSwitches)
    }
}

// Static analysis: detekt's default rules plus config/detekt/detekt.yml. Findings that existed when it was added are
// listed in detekt-baseline.xml (regenerate only deliberately: ./gradlew :composeApp:detektBaseline); new code must be clean.
detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    baseline = file("detekt-baseline.xml")
    source.setFrom(
        "src/commonMain/kotlin",
        "src/androidMain/kotlin",
        "src/iosMain/kotlin",
        "src/commonTest/kotlin",
        "src/androidUnitTest/kotlin",
    )
    parallel = true
}
// detekt 1.23 runs its own Kotlin 2.0 compiler; keep the project's Kotlin version out of its classpath.
configurations.matching { it.name == "detekt" }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") useVersion(libs.versions.detekt.kotlin.get())
    }
}
