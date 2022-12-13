package module.checker

import io.micronaut.configuration.picocli.PicocliRunner
import jakarta.inject.Inject
import org.fusesource.jansi.Ansi
import org.fusesource.jansi.Ansi.ansi
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private const val REQUIRED_SETTINGS_VERSION = "6.1.1"
private const val REQUIRED_MICRONAUT_VERSION = "4.0.0-SNAPSHOT"

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
            "micronaut-oauth2",
            "micronaut-profiles",
        )
        val repos = api.fetchRepos(1)
            .filterNotNull()
            .filter { !it.archived }
            .filter { it.name.startsWith("micronaut-") }
            .filter { !skipRepos.contains(it.name) }
        val width = repos.maxOf { it.name.length }
        if (markdown) {
            println()
            println()
            println("---")
            println()
            println("### Run at ${ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME)}")
            println()
            println("| | Repository | Settings Version | Status | Micronaut Version |")
            println("| --- | --- | --- | --- | --- |")
        }
        repos
            .asSequence()
            .sortedBy { it.name }
            .forEach { process(it, width) }
    }

    fun process(repo: GithubRepo, width: Int) {
        try {
            val version = micronautVersion(repo)
            val actions = api.actions(QueryBean(repo.name))
            val latestJavaCi = actions?.latestJavaCi()
            val settingsVersion = settingsVersion(repo)

            println(if (markdown) markdownOutput(version, repo, settingsVersion, latestJavaCi) else ansiOutput(version, repo, width, latestJavaCi, settingsVersion))
        } catch (e: Exception) {
            println("Exception " + e.javaClass.simpleName + " for repo " + repo.name);
        }
    }

    private fun markdownOutput(version: String?, repo: GithubRepo, settingsVersion: String?, latestJavaCi: String?) =
        "| ${if (settingsVersion == REQUIRED_SETTINGS_VERSION && version == REQUIRED_MICRONAUT_VERSION && latestJavaCi == "success") "💚" else ""} | ${repo.name} | ${if (settingsVersion == REQUIRED_SETTINGS_VERSION) "✅" else ""} $settingsVersion | [![Build Status](https://github.com/micronaut-projects/${repo.name}/workflows/Java%20CI/badge.svg)](https://github.com/micronaut-projects/${repo.name}/actions) | ${if (version == REQUIRED_MICRONAUT_VERSION) "✅" else ""} $version |"

    private fun ansiOutput(
        version: String?,
        repo: GithubRepo,
        width: Int,
        latestJavaCi: String?,
        settingsVersion: String?
    ): Ansi? = ansi()
        .fg(if (version == REQUIRED_MICRONAUT_VERSION) Ansi.Color.GREEN else Ansi.Color.RED)
        .a(repo.name.padEnd(width))
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

    fun micronautVersion(repo: GithubRepo) =
        api.file(QueryBean(repo.name, "gradle.properties"))?.let {
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
