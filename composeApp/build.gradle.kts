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
    alias(libs.plugins.kover)
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
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(libs.compose.ui.test)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.sqldelight.android.driver)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.google.play.services.auth)
        }
        // Device tests (src/androidInstrumentedTest): ./gradlew :composeApp:connectedDebugAndroidTest
        androidInstrumentedTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.ext.junit)
            implementation(libs.androidx.compose.ui.test.junit4)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidUnitTest.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
            // Compose UI tests run on the JVM under Robolectric (see src/androidUnitTest/.../ui/AndroidUiTests.kt).
            implementation(libs.androidx.compose.ui.test.junit4)
            implementation(libs.robolectric)
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
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

// Tests assert the exact English wording of user messages. This turns composeResources/values/strings.xml into a plain
// Kotlin map in the common test sources, so a test can render a UiText on the JVM and on iOS without a resource environment.
val generateEnglishCatalog by tasks.registering {
    val stringsXml = layout.projectDirectory.file("src/commonMain/composeResources/values/strings.xml")
    val outDir = layout.buildDirectory.dir("generated/englishCatalog")
    inputs.file(stringsXml)
    outputs.dir(outDir)
    doLast {
        val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stringsXml.asFile)

        // Only what the Compose resource plugin itself converts: it keeps a backslash before an apostrophe or quote
        // (StringCatalogEscapesTest keeps those out of strings.xml), so the catalog must not hide one.
        fun unescape(raw: String) = raw.replace("\\n", "\n")
        fun literal(text: String) = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$").replace("\n", "\\n") + "\""
        val strings = StringBuilder()
        val plurals = StringBuilder()
        val nodes = doc.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as? org.w3c.dom.Element ?: continue
            val name = node.getAttribute("name")
            when (node.tagName) {
                "string" -> strings.append("        ${literal(name)} to ${literal(unescape(node.textContent))},\n")

                "plurals" -> {
                    val items = node.getElementsByTagName("item")
                    val entries = (0 until items.length).joinToString(", ") {
                        val item = items.item(it) as org.w3c.dom.Element
                        "${literal(item.getAttribute("quantity"))} to ${literal(unescape(item.textContent))}"
                    }
                    plurals.append("        ${literal(name)} to mapOf($entries),\n")
                }
            }
        }
        val file = outDir.get().asFile.resolve("com/habitsheet/testing/EnglishCatalog.kt")
        file.parentFile.mkdirs()
        file.writeText(
            "// GENERATED from composeResources/values/strings.xml by generateEnglishCatalog. Do not edit.\n" +
                "package com.habitsheet.testing\n\n" +
                "internal object EnglishCatalog {\n    val strings: Map<String, String> = mapOf(\n$strings    )\n\n" +
                "    val plurals: Map<String, Map<String, String>> = mapOf(\n$plurals    )\n}\n",
        )
    }
}
kotlin.sourceSets.named("commonTest") { kotlin.srcDir(generateEnglishCatalog.map { it.outputs.files }) }

// Renders the commonMain @Preview functions in Android Studio; debug builds only, never shipped.
dependencies {
    debugImplementation(libs.compose.ui.tooling)
    // Registers the empty ComponentActivity the Compose UI tests launch; debug builds only.
    debugImplementation(libs.androidx.compose.ui.test.manifest)
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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets {
        // The released schemas (1.db ... N.db) are test assets: the on-device migration test opens each of them.
        getByName("androidTest").assets.srcDir("src/commonMain/sqldelight/databases")
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

    testOptions {
        // Robolectric needs the merged manifest and resources (Compose resources are packaged as assets).
        unitTests.isIncludeAndroidResources = true
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
        "src/androidInstrumentedTest/kotlin",
        "src/iosTest/kotlin",
    )
    parallel = true
}
// detekt 1.23 runs its own Kotlin 2.0 compiler; keep the project's Kotlin version out of its classpath.
configurations.matching { it.name == "detekt" }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") useVersion(libs.versions.detekt.kotlin.get())
    }
}

// --- Coverage (P2 steps 1 and 12) ---
// `./gradlew :composeApp:koverHtmlReport` for the report, `python scripts/coverage-by-package.py` for the table
// (after `koverXmlReport`), `./gradlew :composeApp:verifyCoverageFloors` for the floors below; `check` runs it.
//
// Floors are LINE coverage of the JVM unit tests (androidUnitTest + commonTest on the JVM, Robolectric UI tests
// included; device and iOS runs are not counted), set to what was achieved at the end of P2 minus 2 points, so that
// ordinary work passes and a real drop fails. Measured 2026-10-12: domain 99.3, data 99.3, sync 99.6,
// presentation 94.6, ui 72.0, app + platform 21.4 (Android and iOS shell code, covered by device tests), total 77.1.
// Ratchet: when coverage rises, raise the floor to (measured - 2), never lower one to make a build pass.
// Generated code is excluded so the numbers describe code we wrote.
kover {
    reports {
        filters {
            excludes {
                packages(
                    "com.habitsheet.database",
                    "habitsheet.composeapp.generated.resources",
                    "com.habitsheet.resources",
                )
                classes(
                    "*ComposableSingletons*",
                    "com.habitsheet.app.BuildConfig",
                    "*.R",
                    "*.R\$*",
                )
            }
        }
    }
}

// The floors, checked against the Kover XML report (Kover's own rules cannot be limited to a package in this version).
// Package prefixes use '/' as in the report; `check` runs this task.
val coverageFloors = listOf(
    Triple("domain", listOf("com/habitsheet/domain"), 97),
    Triple("data", listOf("com/habitsheet/data"), 97),
    Triple("sync", listOf("com/habitsheet/sync"), 97),
    Triple("presentation", listOf("com/habitsheet/presentation"), 92),
    Triple("ui", listOf("com/habitsheet/ui"), 70),
    Triple("app and platform shell", listOf("com/habitsheet/app", "com/habitsheet/platform"), 19),
    Triple("total", listOf("com/habitsheet"), 75),
)
val verifyCoverageFloors by tasks.registering {
    group = "verification"
    description = "Fails when line coverage of a package group is below its floor (see the comment above kover)."
    dependsOn("koverXmlReport")
    val report = layout.buildDirectory.file("reports/kover/report.xml")
    val floors = coverageFloors
    doLast {
        val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }.newDocumentBuilder().parse(report.get().asFile)
        val packages = doc.getElementsByTagName("package")
        val covered = mutableMapOf<String, LongArray>()
        for (index in 0 until packages.length) {
            val element = packages.item(index) as org.w3c.dom.Element
            val name = element.getAttribute("name")
            val counters = element.childNodes
            for (c in 0 until counters.length) {
                val counter = counters.item(c) as? org.w3c.dom.Element ?: continue
                if (counter.tagName != "counter" || counter.getAttribute("type") != "LINE") continue
                for ((group, prefixes, _) in floors) {
                    if (prefixes.any { name == it || name.startsWith("$it/") }) {
                        val sums = covered.getOrPut(group) { LongArray(2) }
                        sums[0] += counter.getAttribute("missed").toLong()
                        sums[1] += counter.getAttribute("covered").toLong()
                    }
                }
            }
        }
        val failures = mutableListOf<String>()
        floors.forEach { (group, _, floor) ->
            val sums = covered[group] ?: longArrayOf(0, 0)
            val percent = if (sums[0] + sums[1] == 0L) 0.0 else 100.0 * sums[1] / (sums[0] + sums[1])
            val line = "%-24s %5.1f%%  (floor %d%%)".format(group, percent, floor)
            logger.lifecycle(line)
            if (percent < floor) failures += line
        }
        if (failures.isNotEmpty()) throw GradleException("Coverage fell below its floor:\n" + failures.joinToString("\n"))
    }
}
tasks.named("check") { dependsOn(verifyCoverageFloors) }

// Screenshot tests (ScreenshotTests, Roborazzi library without its Gradle plugin: the plugin made every test in the
// module run about twice as slow, which fails LoadSnapshotPerformanceTest). They compare nothing unless asked:
//   -Proborazzi.test.record=true   write or update the goldens
//   -Proborazzi.test.verify=true   fail when a screen differs from its golden
//   -Proborazzi.test.compare=true  write old/new/diff images to build/outputs/roborazzi
tasks.withType<Test>().configureEach {
    // Goldens are read by the tests, so a changed golden must rerun them (otherwise verify is "up to date").
    inputs.files(fileTree("src/androidUnitTest/screenshots")).withPropertyName("screenshotGoldens")
    listOf("record", "verify", "compare").forEach { mode ->
        providers.gradleProperty("roborazzi.test.$mode").orNull?.let { systemProperty("roborazzi.test.$mode", it) }
    }
}
