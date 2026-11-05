package id.nextfl.app.audio

import id.nextfl.core.engine.SourceResolver
import id.nextfl.core.pcm.MappedPcmSource
import id.nextfl.core.pcm.PcmSource
import id.nextfl.core.pcm.WaveformPeaks
import id.nextfl.app.data.StoragePaths
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

class LoadedAsset(val source: MappedPcmSource, val peaks: WaveformPeaks)

/** Decoded audio; pitch is realtime. */
class AssetLibrary(
    private val paths: StoragePaths,
    private val sampleRate: Int,
) : SourceResolver {
    private val loaded = ConcurrentHashMap<String, LoadedAsset>()
    private val mutableVersion = MutableStateFlow(0)

    /** Bumps when sources change. */
    val version: StateFlow<Int> = mutableVersion.asStateFlow()

    override fun original(assetId: String): PcmSource? = loaded[assetId]?.source

    fun peaks(assetId: String): WaveformPeaks? = loaded[assetId]?.peaks

    fun isLoaded(assetId: String) = loaded.containsKey(assetId)

    fun register(assetId: String, asset: LoadedAsset) {
        loaded.put(assetId, asset)?.source?.close()
        bump()
    }

    fun open(assetId: String): LoadedAsset? {
        val file = paths.asset(assetId)
        if (!file.exists() || file.length() == 0L) return null
        val source = MappedPcmSource(file, sampleRate)
        return LoadedAsset(source, WaveformPeaks.compute(source))
    }

    fun remove(assetId: String) {
        loaded.remove(assetId)?.source?.close()
        paths.asset(assetId).delete()
        bump()
    }

    fun clear() {
        loaded.values.forEach { it.source.close() }
        loaded.clear()
        paths.clearAssets()
        bump()
    }

    private fun bump() = mutableVersion.update { it + 1 }
}
