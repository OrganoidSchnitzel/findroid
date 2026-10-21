package dev.jdtech.jellyfin.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.jdtech.jellyfin.api.JellyfinApi
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.toFindroidEpisode
import dev.jdtech.jellyfin.models.toFindroidMovie
import dev.jdtech.jellyfin.settings.domain.AppPreferences

@HiltWorker
class SyncWorker
@AssistedInject
constructor(
    @Assisted private val context: Context,
    @Assisted private val workerParams: WorkerParameters,
    val database: ServerDatabaseDao,
    val appPreferences: AppPreferences,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val jellyfinApi =
            JellyfinApi(
                androidContext = context.applicationContext,
                requestTimeout = appPreferences.getValue(appPreferences.requestTimeout),
                connectTimeout = appPreferences.getValue(appPreferences.connectTimeout),
                socketTimeout = appPreferences.getValue(appPreferences.socketTimeout),
            )

        val servers = database.getServers()
        var allSynced = true

        for (server in servers) {
            val serverWithAddressesAndUsers =
                database.getServerWithAddressesAndUsers(server.id) ?: continue
            val serverAddress =
                serverWithAddressesAndUsers.addresses.firstOrNull {
                    it.id == server.currentServerAddressId
                } ?: continue
            for (user in serverWithAddressesAndUsers.users) {
                jellyfinApi.apply {
                    api.update(baseUrl = serverAddress.address, accessToken = user.accessToken)
                    userId = user.id
                }
                val movies =
                    database.getMoviesByServerId(server.id).map {
                        it.toFindroidMovie(database, user.id)
                    }
                val episodes =
                    database.getEpisodesByServerId(server.id).map {
                        it.toFindroidEpisode(database, user.id)
                    }

                val moviesSynced = pushPendingUserData(database, jellyfinApi, user.id, movies)
                val episodesSynced = pushPendingUserData(database, jellyfinApi, user.id, episodes)
                if (!moviesSynced || !episodesSynced) allSynced = false
            }
        }

        return when {
            allSynced -> Result.success()
            runAttemptCount < MAX_RETRIES -> Result.retry()
            else -> Result.failure()
        }
    }

    companion object {
        private const val MAX_RETRIES = 5
    }
}
