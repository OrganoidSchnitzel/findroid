package dev.jdtech.jellyfin.film.presentation.season

import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidSeason
import java.util.UUID

data class SeasonState(
    val season: FindroidSeason? = null,
    val episodes: List<FindroidEpisode> = emptyList(),
    val selectedEpisodeIds: Set<UUID> = emptySet(),
    val isQueueingDownloads: Boolean = false,
    val error: Exception? = null,
) {
    val isSelecting: Boolean
        get() = selectedEpisodeIds.isNotEmpty()
}
