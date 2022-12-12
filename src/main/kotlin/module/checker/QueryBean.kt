package module.checker

import io.micronaut.core.annotation.Introspected
import io.micronaut.http.annotation.PathVariable

@Introspected
data class QueryBean(@field:PathVariable val repo: String, @field:PathVariable val path: String? = null)