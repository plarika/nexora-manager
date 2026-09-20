package app.revanced.manager.domain.repository

import android.os.Parcelable
import androidx.core.net.toUri
import androidx.paging.PagingSource
import androidx.paging.PagingState
import app.revanced.manager.network.api.LegacyPatchApi
import app.revanced.manager.network.dto.RemoteAssetHistory
import app.revanced.manager.network.utils.getOrThrow
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
sealed interface ChangelogSource : Parcelable {
    data object Manager : ChangelogSource
    data class Patches(val url: String, val prerelease: Boolean) : ChangelogSource {
        @IgnoredOnParcel
        val baseUrl by lazy { url.toUri().let { "${it.scheme}://${it.host}" } }
    }
}

class ChangelogsRepository(
    private val api: LegacyPatchApi,
    private val managerUpdateRepository: ManagerUpdateRepository,
    private val source: ChangelogSource,
) : PagingSource<Int, RemoteAssetHistory>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, RemoteAssetHistory> {
        return try {
            val items = when (source) {
                is ChangelogSource.Manager -> managerUpdateRepository.getHistory()

                is ChangelogSource.Patches ->
                    api.getPatchesHistory(source.baseUrl, source.prerelease).getOrThrow()
            }

            LoadResult.Page(
                data = items,
                prevKey = null,
                nextKey = null,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, RemoteAssetHistory>): Int? = null
}
