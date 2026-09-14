package cc.kowx712.wishexport.model

/**
 * Configuration for detecting wish URLs from different games.
 *
 * All HoYoverse games use the same authentication mechanism:
 * - mihoyo.com or hoyoverse.com domains
 * - authkey parameter for API authentication
 *
 * No log tag filtering needed - the URL patterns are specific enough.
 */
data class GameConfig(
    val gameNames: List<String>,
    val urlPattern: Regex
) {
    companion object {
        private const val UNIVERSAL_LINK_REGEX = "https://\\S+\\.(?:mihoyo|hoyoverse)\\.com/\\S*authkey=\\S+"

        val SUPPORTED_CONFIGS = listOf(
            GameConfig(
                gameNames = listOf(
                    "Genshin Impact",
                    "Honkai: Star Rail",
                    "Zenless Zone Zero"
                ),
                urlPattern = Regex(
                    UNIVERSAL_LINK_REGEX,
                    RegexOption.IGNORE_CASE
                )
            )
        )
    }
}
