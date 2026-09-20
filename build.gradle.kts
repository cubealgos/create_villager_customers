// Create Fly: Villager Customers — one Gradle project, one jar (docs/spec/04-architecture.md ARCH-DEC-001).
plugins {
    java
    alias(libs.plugins.loom)
}

group = "villager_customers"
version = "1.0.0+26.2"

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") }
        filter { includeGroup("maven.modrinth") }
    }
}

dependencies {
    minecraft(libs.minecraft)
    implementation(libs.fabricLoader)
    implementation(libs.fabricApi)
    implementation(libs.createFly)
    testImplementation(platform(libs.junitBom))
    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitLauncher)
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.test {
    useJUnitPlatform()
}

loom {
    mods {
        create("villager_customers") {
            sourceSet(sourceSets.main.get())
        }
    }
}

// Server-side game tests under src/gametest (docs/spec/operations/testing.md).
fabricApi {
    configureTests {
        createSourceSet = true
        modId = "villager_customers_gametest"
        enableGameTests = true
        enableClientGameTests = false
        eula = true
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") { expand("version" to project.version) }
}

// Every game-test run starts from a fresh world (a lesson from create_civilization).
tasks.named("runGameTest") {
    doFirst { delete(layout.buildDirectory.dir("run/gameTest/world")) }
}

// The pure package: villager_customers.model has no Minecraft imports (docs/spec/operations/testing.md TEST-REQ-002).
val verifyPurePackage by tasks.registering {
    group = "verification"
    description = "Fails when villager_customers.model imports Minecraft, Fabric or Create."
    val sources = layout.projectDirectory.dir("src/main/java/villager_customers/model")
    inputs.dir(sources)
    doLast {
        val bad = sources.asFileTree.filter { it.extension == "java" }.files.flatMap { f ->
            f.readLines().filter { l -> l.startsWith("import net.minecraft") || l.startsWith("import net.fabricmc") || l.startsWith("import com.zurrtum") }
                .map { l -> "${f.name}: $l" }
        }
        if (bad.isNotEmpty()) throw GradleException("pure package imports the game:\n" + bad.joinToString("\n"))
    }
}
tasks.named("check") { dependsOn(verifyPurePackage) }
