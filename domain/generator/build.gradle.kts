plugins { id("org.jetbrains.kotlin.jvm") }
kotlin { jvmToolchain(17) }
dependencies {
    api(project(":core:model"))
    testImplementation(kotlin("test"))
    testImplementation(project(":domain:catalog"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}
tasks.test { useJUnitPlatform() }
tasks.register<JavaExec>("exportWebFixtures") {
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("ru.bodybuild.domain.generator.ExportWebFixturesKt")
    args(rootProject.file("web/tests/android-fixtures.json").absolutePath)
}
