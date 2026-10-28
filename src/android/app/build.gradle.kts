plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); jacoco }

android {
    namespace = "br.com.lume.launcher"
    compileSdk = 35
    buildToolsVersion = "36.0.0"
    testBuildType = "release"
    defaultConfig {
        applicationId = "br.com.lume.launcher"
        minSdk = 30
        targetSdk = 35
        versionCode = 31
        versionName = "0.4.5"
        testInstrumentationRunner = "br.com.lume.launcher.LocaleSmokeInstrumentation"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    bundle { language { enableSplit = false } }
    buildTypes {
        getByName("release") {
            // Personal evaluation certificate. Preserve the key for compatible updates; never publish it.
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            isMinifyEnabled = false
        }
    }
    lint { abortOnError = true; checkReleaseBuilds = true }
}

dependencies { testImplementation("junit:junit:4.13.2") }
jacoco { toolVersion = "0.8.12" }
tasks.withType<Test>().configureEach {
    extensions.configure<JacocoTaskExtension> { isIncludeNoLocationClasses = true; excludes = listOf("jdk.internal.*") }
}
val ruleClasses = fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) { include("br/com/lume/launcher/core/**") }
tasks.register<JacocoReport>("rulesCoverage") {
    dependsOn("testDebugUnitTest")
    executionData(layout.buildDirectory.file("jacoco/testDebugUnitTest.exec"))
    classDirectories.setFrom(ruleClasses)
    sourceDirectories.setFrom(files("src/main/java"))
    reports { xml.required.set(true); html.required.set(true) }
}
tasks.register<JacocoCoverageVerification>("verifyRulesCoverage") {
    dependsOn("rulesCoverage")
    executionData(layout.buildDirectory.file("jacoco/testDebugUnitTest.exec"))
    classDirectories.setFrom(ruleClasses)
    violationRules { rule { limit { counter = "LINE"; minimum = "0.90".toBigDecimal() }; limit { counter = "BRANCH"; minimum = "0.90".toBigDecimal() } } }
}
