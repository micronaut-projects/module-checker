package module.checker

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class GithubAction(val name: String, val conclusion: String)

@Serdeable
data class GithubActions(
    @field:JsonProperty("workflow_runs") val runs: List<GithubAction>
) {
    fun latestJavaCi() = runs.firstOrNull { it.name == "Java CI" }?.conclusion ?: "unknown"
}
