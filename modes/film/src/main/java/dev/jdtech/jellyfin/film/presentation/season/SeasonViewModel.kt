package dev.jdtech.jellyfin.film.presentation.season

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.models.isDownloaded
import dev.jdtech.jellyfin.models.isDownloading
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.utils.Downloader
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.ItemFields
import timber.log.Timber

@HiltViewModel
class SeasonViewModel
@Inject
constructor(private val repository: JellyfinRepository, private val downloader: Downloader) :
    ViewModel() {
    private val _state = MutableStateFlow(SeasonState())
    val state = _state.asStateFlow()

    private val eventsChannel = Channel<SeasonEvent>()
    val events = eventsChannel.receiveAsFlow()

    lateinit var seasonId: UUID

    fun loadSeason(seasonId: UUID) {
        this.seasonId = seasonId
        viewModelScope.launch {
            try {
                val season = repository.getSeason(seasonId)
                val episodes =
                    repository.getEpisodes(
                        seriesId = season.seriesId,
                        seasonId = seasonId,
                        fields = listOf(ItemFields.OVERVIEW, ItemFields.CAN_DOWNLOAD),
                    )
                _state.emit(_state.value.copy(season = season, episodes = episodes))
            } catch (e: Exception) {
                _state.emit(_state.value.copy(error = e))
            }
        }
    }

    fun onAction(action: SeasonAction) {
        when (action) {
            is SeasonAction.MarkAsPlayed -> {
                viewModelScope.launch {
                    repository.markAsPlayed(seasonId)
                    loadSeason(seasonId)
                }
            }
            is SeasonAction.UnmarkAsPlayed -> {
                viewModelScope.launch {
                    repository.markAsUnplayed(seasonId)
                    loadSeason(seasonId)
                }
            }
            is SeasonAction.MarkAsFavorite -> {
                viewModelScope.launch {
                    repository.markAsFavorite(seasonId)
                    loadSeason(seasonId)
                }
            }
            is SeasonAction.UnmarkAsFavorite -> {
                viewModelScope.launch {
                    repository.unmarkAsFavorite(seasonId)
                    loadSeason(seasonId)
                }
            }
            is SeasonAction.ToggleEpisodeSelection -> toggleEpisodeSelection(action.episode)
            is SeasonAction.ClearSelection -> {
                _state.update { it.copy(selectedEpisodeIds = emptySet()) }
            }
            is SeasonAction.DownloadSelected -> downloadSelected(action.storageIndex)
            else -> Unit
        }
    }

    private fun toggleEpisodeSelection(episode: FindroidEpisode) {
        if (!episode.isSelectableForDownload()) return
        _state.update { state ->
            val selected =
                if (episode.id in state.selectedEpisodeIds) state.selectedEpisodeIds - episode.id
                else state.selectedEpisodeIds + episode.id
            state.copy(selectedEpisodeIds = selected)
        }
    }

    /**
     * Downloads the selected episodes one after another, in the order they appear in the season.
     * Every episode goes through the same [Downloader.downloadItem] call as a single download from
     * the episode screen. The batch stops at the first error (for example not enough storage).
     */
    private fun downloadSelected(storageIndex: Int) {
        val currentState = _state.value
        if (currentState.isQueueingDownloads) return
        val episodes = currentState.episodes.filter { it.id in currentState.selectedEpisodeIds }
        if (episodes.isEmpty()) return

        _state.update { it.copy(selectedEpisodeIds = emptySet(), isQueueingDownloads = true) }

        viewModelScope.launch {
            var queued = 0
            for (episode in episodes) {
                when (val result = downloadEpisode(episode.id, storageIndex)) {
                    is EpisodeDownloadResult.Queued -> queued++
                    is EpisodeDownloadResult.Skipped -> Unit
                    is EpisodeDownloadResult.Failed -> {
                        eventsChannel.send(SeasonEvent.DownloadError(result.error))
                        break
                    }
                }
            }
            _state.update { it.copy(isQueueingDownloads = false) }
            if (queued > 0) {
                eventsChannel.send(SeasonEvent.DownloadsQueued(queued))
            }
            loadSeason(seasonId)
        }
    }

    private suspend fun downloadEpisode(episodeId: UUID, storageIndex: Int): EpisodeDownloadResult {
        return try {
            // Fetch the full item, like the episode screen does, so the downloader gets all the
            // metadata and media sources it needs.
            val episode = repository.getEpisode(episodeId)
            // The episode may have been downloaded in the meantime
            if (!episode.isSelectableForDownload()) return EpisodeDownloadResult.Skipped
            val (downloadId, errorText) =
                downloader.downloadItem(
                    item = episode,
                    sourceId = episode.sources.first().id,
                    storageIndex = storageIndex,
                )
            if (downloadId == -1L) {
                EpisodeDownloadResult.Failed(
                    errorText ?: UiText.StringResource(CoreR.string.unknown_error)
                )
            } else {
                EpisodeDownloadResult.Queued
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e)
            EpisodeDownloadResult.Failed(
                e.message?.let { UiText.DynamicString(it) }
                    ?: UiText.StringResource(CoreR.string.unknown_error)
            )
        }
    }

    private sealed interface EpisodeDownloadResult {
        data object Queued : EpisodeDownloadResult

        data object Skipped : EpisodeDownloadResult

        data class Failed(val error: UiText) : EpisodeDownloadResult
    }
}

fun FindroidEpisode.isSelectableForDownload(): Boolean =
    canDownload && !isDownloaded() && !isDownloading()
