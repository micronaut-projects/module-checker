package module.checker

import io.micronaut.configuration.picocli.PicocliRunner
import jakarta.inject.Inject
import org.fusesource.jansi.Ansi
import org.fusesource.jansi.Ansi.ansi
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.lang.Appendable
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private const val REQUIRED_SETTINGS_VERSION = "6.2.0"
private const val REQUIRED_MICRONAUT_VERSION = "4.0.0-SNAPSHOT"
private const val PAGE_SIZE = 50

@Command(
    name = "module-checker", description = ["..."],
    mixinStandardHelpOptions = true
)
class ModuleCheckerCommand : Runnable {

    @Inject
    lateinit var api: GithubApiClient

    @Option(names = ["-m", "--markdown"], description = ["outputs markdown instead of ANSI text"])
    private var markdown: Boolean = false

    override fun run() {
        val skipRepos = listOf(
            "micronaut-core",
            "micronaut-starter-ui",
            "micronaut-lambda-todo",
            "micronaut-project-template",
            "micronaut-build-plugins",
            "micronaut-build",
            "micronaut-comparisons",
            "micronaut-crac-tests",
            "micronaut-docs",
            "micronaut-docs-deploy",
            "micronaut-docs-index",
            "micronaut-examples",
            "micronaut-fuzzing",
            "micronaut-guides",
            "micronaut-guides-old",
            "micronaut-guides-poc",
            "micronaut-maven-plugin",
            "micronaut-oauth2",
            "micronaut-profiles",
            "micronaut-platform",
        )
        if (markdown) {
            println()
            println()
            println("---")
            println()
            println("### Run at ${ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME)}")
            println()
            println("| | Repository | Project Version | Settings Version | Status | Micronaut Version |")
            println("| --- | --- | --- | --- | --- | --- |")
        }
        repos()
            .filterNotNull()
            .filter { !it.archived }
            .filter { it.name.startsWith("micronaut-") }
            .filter { !skipRepos.contains(it.name) }
            .map { process(it) }
            .sortedBy { it.first }
            .forEach { println(it.second) }
        if (markdown) {
            println("")
            println("---")
            println("### Experimental dependency graph")
            println("")
            println("Only includes repositories that use importMicronautCatalog, and excludes micronaut-core as that's a given.")
            println("Directly reciprocal dependencies are marked in red.")
            println("")
            println("```mermaid")
            println("graph LR")
            val dependencyMap = repos()
                .filterNotNull()
                .filter { !it.archived }
                .filter { it.name.startsWith("micronaut-") }
                .filter { !skipRepos.contains(it.name) }
                .groupBy({ it.name }, { extractDependencySet(it) })
                .mapValues { it.value.flatten().toSet() }

            val frequency = dependencyMap.values.flatMap { it.toList() }.groupingBy { it }.eachCount()

            val reciprocal = mutableListOf<Int>()

            var idx = 0
            dependencyMap.forEach { (name, dependencies) ->
                dependencies.forEach {
                    println("    $name -${"-".repeat(frequency.get(it) ?:1)}> $it")
                    if (dependencyMap[it]?.contains(name) == true) {
                        reciprocal.add(idx)
                    }
                    idx++
                }
            }
            if (reciprocal.isNotEmpty()) {
                println("    linkStyle ${reciprocal.joinToString(",")} stroke:red, stroke-width:4px")
            }
            println("```")
        }
    }

    private fun extractDependencySet(repo: GithubRepo) =
        (api.file(QueryBean(repo.name, "settings.gradle.kts")).let { file ->
            file?.lines()?.filter { it.contains("importMicronautCatalog(\"") }
                ?.map { it.substringAfter("importMicronautCatalog(\"").substringBefore("\")") }
                ?.toSet()
        } ?: api.file(QueryBean(repo.name, "settings.gradle")).let { file ->
            file?.lines()?.filter { it.contains("importMicronautCatalog(\"") }
                ?.map { it.substringAfter("importMicronautCatalog(\"").substringBefore("\")") }
                ?.toSet()
        } ?: emptySet());

    private fun repos(): Sequence<GithubRepo?> {
        var pageNo = 1
        return generateSequence(api.fetchRepos(PAGE_SIZE, pageNo++)) {
            if (it.isNotEmpty()) {
                api.fetchRepos(PAGE_SIZE, pageNo++)
            } else {
                null
            }
        }.flatten()
    }

    private fun process(repo: GithubRepo, width: Int = 40): Pair<String, Appendable?> {
        try {
            val props = properties(repo)
            val projectVersion = projectVersion(props)
            val version = micronautVersion(props, repo)
            val actions = api.actions(QueryBean(repo.name))
            val latestJavaCi = actions?.latestJavaCi()
            val settingsVersion = settingsVersion(repo)

            return repo.name to (if (markdown) markdownOutput(projectVersion, version, repo, settingsVersion, latestJavaCi) else ansiOutput(projectVersion, version, repo, width, latestJavaCi, settingsVersion))
        } catch (e: Exception) {
            return repo.name to StringBuilder("Exception " + e.javaClass.simpleName + " for repo " + repo.name);
        }
    }

    private fun markdownOutput(projectVersion: String, version: String?, repo: GithubRepo, settingsVersion: String?, latestJavaCi: String?) =
        StringBuilder("| ${if (settingsVersion == REQUIRED_SETTINGS_VERSION && version == REQUIRED_MICRONAUT_VERSION && latestJavaCi == "success") "💚" else ""}" +
                " | [${repo.name}](https://github.com/micronaut-projects/${repo.name})" +
                " | $projectVersion" +
                " | ${if (settingsVersion == REQUIRED_SETTINGS_VERSION) "✅" else ""} $settingsVersion" +
                " | [![Build Status](https://github.com/micronaut-projects/${repo.name}/workflows/Java%20CI/badge.svg)](https://github.com/micronaut-projects/${repo.name}/actions)" +
                " | ${if (version == REQUIRED_MICRONAUT_VERSION) "✅" else ""} $version |")

    private fun ansiOutput(
        projectVersion: String,
        version: String?,
        repo: GithubRepo,
        width: Int,
        latestJavaCi: String?,
        settingsVersion: String?
    ): Ansi? = ansi()
        .fg(if (version == REQUIRED_MICRONAUT_VERSION) Ansi.Color.GREEN else Ansi.Color.RED)
        .a(repo.name.padEnd(width))
        .a("\t")
        .a("[$projectVersion]")
        .a("\t")
        .a(settingsVersion)
        .a("\t")
        .apply {
            if (latestJavaCi == "success") {
                it.a("✅")
            } else if (latestJavaCi == "failure") {
                it.a("❌")
            } else {
                it.a("❔")
            }
            it.reset()
        }
        .a("\t")
        .a(version)

    fun settingsVersion(repo: GithubRepo) =
        api.file(QueryBean(repo.name, "settings.gradle"))?.let { settings ->
            Regex("id[( ][\"']io.micronaut.build.shared.settings[\"'][)]? version [\"']([^'\"]+)[\"']").find(settings)?.groups?.get(1)?.value
        } ?: api.file(QueryBean(repo.name, "settings.gradle.kts"))?.let { settings ->
            Regex("id\\(\"io.micronaut.build.shared.settings\"\\) version \"([^\"]+)\"").find(settings)?.groups?.get(1)?.value
        } ?: "unknown"

    fun properties(repo: GithubRepo) = api.file(QueryBean(repo.name, "gradle.properties"))

    fun projectVersion(properties: String?) =
        properties?.let { props ->
            Regex("projectVersion=(.+)").find(props)?.groups?.get(1)?.value
        } ?: "unknown"

    fun micronautVersion(properties: String?, repo: GithubRepo) =
        properties?.let {
            Regex("micronautVersion=(.+)").find(it)?.groups?.get(1)?.value
        } ?: api.file(QueryBean(repo.name, "gradle/libs.versions.toml"))?.let {
            Regex("micronaut[\\s]*=[\\s]*[\"'](.+)[\"']").find(it)?.groups?.get(1)?.value
                ?: "UNKNOWN"
        }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            PicocliRunner.run(ModuleCheckerCommand::class.java, *args)
        }
    }

}
