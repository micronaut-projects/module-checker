package module.checker

import io.micronaut.http.HttpHeaders.ACCEPT
import io.micronaut.http.HttpHeaders.AUTHORIZATION
import io.micronaut.http.HttpHeaders.USER_AGENT
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Headers
import io.micronaut.http.annotation.RequestBean
import io.micronaut.http.client.annotation.Client

@Client(GithubConfig.GITHUB_API_URL)
@Headers(
    Header(name = USER_AGENT, value = "Micronaut HTTP Client"),
    Header(name = ACCEPT, value = "application/vnd.github.v3+json, application/json"),
    Header(name = AUTHORIZATION, value = "Bearer \${github.token}")
)
interface GithubApiClient {

    @Get("/orgs/\${github.organization}/repos?per_page=10&page={page}")
    fun fetchRepos(page: Int): List<GithubRepo?>

    @Get("/repos/\${github.organization}/{repo}/contents/{path}")
    @Header(name = ACCEPT, value = "application/vnd.github.VERSION.raw")
    fun file(@RequestBean query: QueryBean): String?

    @Get("/repos/\${github.organization}/{repo}/actions/runs?branch=master")
    @Header(name = ACCEPT, value = "application/vnd.github+json")
    fun actions(@RequestBean query: QueryBean): GithubActions?
}