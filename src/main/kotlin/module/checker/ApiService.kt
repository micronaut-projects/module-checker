package module.checker

import io.micronaut.cache.annotation.Cacheable
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
open class ApiService {

    companion object {
        var count = 0
    }

    fun getCount() = count

    @Inject
    lateinit var api: GithubApiClient

    fun <T> call(call: () -> T): T {
        count++
        return call()
    }

    @Cacheable(cacheNames = ["repos"])
    open fun fetchRepos(pageSize: Int, page: Int): List<GithubRepo?> = call { api.fetchRepos(pageSize, page) }

    @Cacheable(cacheNames = ["file"])
    open fun file(query: QueryBean): String? = call { api.file(query) }

    @Cacheable(cacheNames = ["actions"])
    open fun actions(query: QueryBean): GithubActions? = call { api.actions(query) }
}