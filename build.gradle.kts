// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.aboutlibraries) apply false
}

val lintAsmVersion = libs.versions.lintAsm.get()

subprojects {
    val constraintHandler = dependencies.constraints
    configurations.matching { it.name == "androidLintTool" }.configureEach {
        listOf("asm", "asm-analysis", "asm-commons", "asm-tree").forEach { module ->
            dependencyConstraints.add(
                constraintHandler.create("org.ow2.asm:$module:$lintAsmVersion").apply {
                    because("Support JVM 27 bytecode analysis in Lint")
                }
            )
        }
    }
}
