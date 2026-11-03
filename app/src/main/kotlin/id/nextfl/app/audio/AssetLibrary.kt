package id.nextfl.app.audio

import id.nextfl.core.dsp.TimeStretcher
import id.nextfl.core.edit.clipSpan
import id.nextfl.core.engine.SourceResolver
import id.nextfl.core.model.AssetMap
import id.nextfl.core.model.ProjectDoc
import id.nextfl.core.pcm.MappedPcmSource
import id.nextfl.core.pcm.PcmFileWriter
import id.nextfl.core.pcm.PcmSource
import id.nextfl.core.pcm.WaveformPeaks
import id.nextfl.app.data.StoragePaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil

class LoadedAsset(val source: MappedPcmSource, val peaks: WaveformPeaks)

/** Decoded and stretched audio. */
class AssetLibrary(
    private val paths: StoragePaths,
    private val sampleRate: Int,
    private val scope: CoroutineScope,
) : SourceResolver {
    private val loaded = ConcurrentHashMap<String, LoadedAsset>()
    private val stretched = ConcurrentHashMap<String, MappedPcmSource>()
    private val jobs = ConcurrentHashMap<String, Job>()
    private val mutableVersion = MutableStateFlow(0)
    private val mutablePending = MutableStateFlow<Set<String>>(emptySet())

    /** Bumps when sources change. */
    val version: StateFlow<Int> = mutableVersion.asStateFlow()
    val pendingStretches: StateFlow<Set<String>> = mutablePending.asStateFlow()

    override fun original(assetId: String): PcmSource? = loaded[assetId]?.source

    override fun stretched(assetId: String, rate: Double): PcmSource? = stretched[key(assetId, rate)]

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
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        loaded.values.forEach { it.source.close() }
        loaded.clear()
        stretched.values.forEach { it.close() }
        stretched.clear()
        paths.clearAssets()
        mutablePending.value = emptySet()
        bump()
    }

    fun isPending(assetId: String, rate: Double) = key(assetId, rate) in mutablePending.value

    /** Starts needed jobs, drops stale. */
    fun syncStretchJobs(doc: ProjectDoc, assets: AssetMap) {
        val needed = doc.clips.mapNotNull { clip ->
            val rate = clipSpan(clip, assets, doc.bpm).rate
            if (clip.keepPitch && !TimeStretcher.isUnity(rate) && loaded.containsKey(clip.assetId)) clip.assetId to rate else null
        }.associateBy { (assetId, rate) -> key(assetId, rate) }

        jobs.keys.filterNot { it in needed }.forEach { jobs.remove(it)?.cancel() }
        stretched.keys.filterNot { it in needed }.forEach { stretched.remove(it)?.close() }
        needed.forEach { (key, request) ->
            if (!stretched.containsKey(key) && !jobs.containsKey(key)) start(key, request.first, request.second)
        }
        mutablePending.value = jobs.keys.toSet()
    }

    /** Waits for variant, for export. */
    suspend fun ensureStretched(assetId: String, rate: Double): PcmSource? {
        stretched[key(assetId, rate)]?.let { return it }
        jobs[key(assetId, rate)]?.join()
        stretched[key(assetId, rate)]?.let { return it }
        val asset = loaded[assetId] ?: return null
        return createStretched(asset.source, assetId, rate) { true }?.also { stretched[key(assetId, rate)] = it }
    }

    private fun start(key: String, assetId: String, rate: Double) {
        val asset = loaded[assetId] ?: return
        jobs[key] = scope.launch(Dispatchers.Default) {
            val source = createStretched(asset.source, assetId, rate) { isActive }
            if (source != null && isActive) {
                stretched[key] = source
                bump()
            }
        }.also { job ->
            job.invokeOnCompletion {
                jobs.remove(key, job)
                mutablePending.update { pending -> pending - key }
            }
        }
    }

    private fun createStretched(source: PcmSource, assetId: String, rate: Double, active: () -> Boolean): MappedPcmSource? {
        val target = paths.stretched(assetId, rate)
        val expectedFrames = ceil(source.frames / rate).toLong()
        if (target.exists() && target.length() == expectedFrames * BYTES_PER_FRAME) return MappedPcmSource(target, sampleRate)
        val temp = File(target.path + ".tmp")
        val finished = PcmFileWriter(temp).use { writer ->
            TimeStretcher(sampleRate).stretch(source, rate, writer, isCancelled = { !active() })
        }
        if (!finished || !temp.renameTo(target)) {
            temp.delete()
            return null
        }
        return MappedPcmSource(target, sampleRate)
    }

    private fun bump() = mutableVersion.update { it + 1 }

    companion object {
        private const val BYTES_PER_FRAME = 4L

        fun key(assetId: String, rate: Double) = "$assetId@${String.format(Locale.ROOT, "%.4f", rate)}"
    }
}
