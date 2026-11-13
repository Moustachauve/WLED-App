package ca.cgagnier.wlednativeandroid.model

enum class Branch {
    UNKNOWN,
    BETA,
    STABLE,
    ;

    /**
     * Firmware language code associated with this branch on Illumidel controllers:
     * [BETA] represents English ("en") and [STABLE] (default) represents French ("fr").
     */
    val languageCode: String
        get() = if (this == BETA) "en" else "fr"

    companion object {
        fun fromProductLanguage(product: String?): Branch =
            if (product.equals("en", ignoreCase = true)) BETA else STABLE
    }
}
