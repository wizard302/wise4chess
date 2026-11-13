import javax.inject.Inject
import org.gradle.process.ExecOperations

// Ships Stockfish as native executables. Android only lets an app run files that were
// installed as part of it, so each binary is packaged as "lib<name>.so" among the native
// libraries and started as a separate process.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.wizard302.wise4chess.engines.stockfish"
    compileSdk = 37
    ndkVersion = "30.0.16248370"

    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core:engine"))
}

/** An Android ABI with the matching Stockfish build target and NDK compiler. */
data class EngineTarget(val abi: String, val arch: String, val compiler: String) : java.io.Serializable

val engineTargets = listOf(
    // Plain ARMv8: the dot-product build would be faster but does not run on older phones.
    EngineTarget("arm64-v8a", "armv8", "aarch64-linux-android26-clang++"),
    EngineTarget("armeabi-v7a", "armv7-neon", "armv7a-linux-androideabi26-clang++"),
    EngineTarget("x86_64", "x86-64-sse41-popcnt", "x86_64-linux-android26-clang++"),
)

/**
 * Builds Stockfish with its own Makefile for every ABI. The sources are copied out of the
 * submodule first, so patches never touch it. Needs `make` and `git` on the build machine.
 */
abstract class BuildStockfishTask @Inject constructor(
    private val execOperations: ExecOperations,
    private val fileOperations: FileSystemOperations,
) : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Internal
    abstract val sourceRoot: DirectoryProperty

    /** Applied in order with `git apply`. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val patches: ConfigurableFileCollection

    /** The network the engine expects; embedded into the binary. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val network: RegularFileProperty

    @get:Input
    abstract val targets: ListProperty<EngineTarget>

    /** File name of the binary, "lib….so". */
    @get:Input
    abstract val libraryName: Property<String>

    @get:Input
    abstract val ndkVersion: Property<String>

    @get:Internal
    abstract val ndkDirectory: DirectoryProperty

    @get:Internal
    abstract val workDirectory: DirectoryProperty

    /** jniLibs layout: one directory per ABI. */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun build() {
        val host = when {
            System.getProperty("os.name").startsWith("Mac") -> "darwin-x86_64"
            System.getProperty("os.name").startsWith("Windows") -> "windows-x86_64"
            else -> "linux-x86_64"
        }
        val toolchain = ndkDirectory.get().dir("toolchains/llvm/prebuilt/$host/bin").asFile
        check(toolchain.isDirectory) { "NDK ${ndkVersion.get()} not found; install it with the SDK Manager" }
        val path = toolchain.absolutePath + File.pathSeparator + System.getenv("PATH")
        val jobs = Runtime.getRuntime().availableProcessors()

        fileOperations.delete { delete(outputDirectory) }
        for (target in targets.get()) {
            val work = workDirectory.get().dir(target.abi).asFile
            fileOperations.sync {
                from(sourceRoot) {
                    include("src/**", "scripts/**")
                }
                into(work)
            }
            for (patch in patches.files.sortedBy { it.name }) {
                execOperations.exec {
                    workingDir = work
                    // The copy sits inside this project's own repository. Left to find that
                    // repository, git would resolve the patch against its root and skip every
                    // file without a word; the ceiling makes it treat the copy as plain files.
                    environment("GIT_CEILING_DIRECTORIES", work.parentFile.absolutePath)
                    commandLine("git", "apply", patch.absolutePath)
                }
            }
            // The engine must have been built for the network we ship, or it would quietly
            // fetch and embed the full-size default one.
            val networkName = network.get().asFile.name
            check(File(work, "src/evaluate.h").readText().contains(networkName)) {
                "The Stockfish sources do not expect $networkName: a patch is missing or did not apply"
            }
            // With the network already in place the Makefile verifies it instead of downloading.
            network.get().asFile.copyTo(File(work, "src/$networkName"), overwrite = true)
            execOperations.exec {
                workingDir = File(work, "src")
                environment("PATH", path)
                commandLine(
                    "make", "-j$jobs", "build",
                    "ARCH=${target.arch}", "COMP=ndk", "COMPCXX=${target.compiler}",
                )
            }
            val output = outputDirectory.get().dir(target.abi).file(libraryName.get()).asFile
            output.parentFile.mkdirs()
            execOperations.exec {
                commandLine(File(toolchain, "llvm-strip").absolutePath, "-o", output.absolutePath, File(work, "src/stockfish").absolutePath)
            }
        }
    }
}

val stockfishSource = rootProject.layout.projectDirectory.dir("third_party/stockfish")

// Stockfish 19 with Lichess's small-network patch: under 1 MB of network instead of 75 MB, so
// it can live inside the APK.
val buildStockfishLite = tasks.register<BuildStockfishTask>("buildStockfishLite") {
    sourceRoot.set(stockfishSource)
    sources.from(fileTree(stockfishSource) { include("src/**", "scripts/**") })
    patches.from(fileTree("patches/lite") { include("*.patch") })
    network.set(layout.projectDirectory.file("nets/nn-61e7af4bb97d.nnue"))
    targets.set(engineTargets)
    libraryName.set("libstockfish_lite.so")
    ndkVersion.set(android.ndkVersion)
    ndkDirectory.set(androidComponents.sdkComponents.ndkDirectory)
    workDirectory.set(layout.buildDirectory.dir("stockfish/lite/work"))
    outputDirectory.set(layout.buildDirectory.dir("stockfish/lite/jniLibs"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(buildStockfishLite, BuildStockfishTask::outputDirectory)
    }
}
