package dev.jdtech.jellyfin.film.presentation.season

import dev.jdtech.jellyfin.models.UiText

sealed interface SeasonEvent {
    data class DownloadsQueued(val count: Int) : SeasonEvent

    data class DownloadError(val error: UiText) : SeasonEvent
}
