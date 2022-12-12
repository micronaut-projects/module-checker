package module.checker

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class ProjectProperties(val content: String)
