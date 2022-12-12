package module.checker

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class GithubRepo(
    val name: String,
    val url: String,
    @field:JsonProperty("default_branch") val defaultBranch: String
)