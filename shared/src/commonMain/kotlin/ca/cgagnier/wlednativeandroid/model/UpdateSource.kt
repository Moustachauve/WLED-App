package ca.cgagnier.wlednativeandroid.model

import ca.cgagnier.wlednativeandroid.model.wledapi.Info

enum class UpdateSourceType {
    ILLUMIDEL,
    CUSTOM,
}

data class UpdateSourceDefinition(
    val type: UpdateSourceType,
    val brandPattern: String,
    val githubOwner: String,
    val githubRepo: String,
    val product: String? = null,
)

object UpdateSourceRegistry {
    const val ILLUMIDEL_BRAND = "ILLUMIDEL"

    val sources = listOf(
        UpdateSourceDefinition(
            type = UpdateSourceType.ILLUMIDEL,
            brandPattern = ILLUMIDEL_BRAND,
            githubOwner = "Illumidel",
            githubRepo = "App-Releases",
        ),
    )

    fun getSource(info: Info): UpdateSourceDefinition? {
        val brandMatches = sources.filter { it.brandPattern == info.brand }
        return brandMatches.find { it.product == info.product }
            ?: brandMatches.find { it.product == null }
    }
}
