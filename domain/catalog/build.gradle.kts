plugins { id("org.jetbrains.kotlin.jvm") }
kotlin { jvmToolchain(17) }
dependencies {
    api(project(":core:model"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}

tasks.register<JavaExec>("exportWebCatalog") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("ru.bodybuild.domain.catalog.ExportWebCatalogKt")
    args(rootProject.file("web/public/catalog.json").absolutePath)
}
