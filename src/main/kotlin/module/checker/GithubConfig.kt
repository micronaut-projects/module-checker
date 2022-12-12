package module.checker

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Requires

@ConfigurationProperties(GithubConfig.PREFIX)
@Requires(property = GithubConfig.PREFIX)
class GithubConfig {
    var organization: String? = null
    var username: String? = null
    var token: String? = null

    companion object {
        const val PREFIX = "github"
        const val GITHUB_API_URL = "https://api.github.com"
    }
}