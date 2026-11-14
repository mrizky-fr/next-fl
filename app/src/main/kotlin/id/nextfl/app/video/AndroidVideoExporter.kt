package id.nextfl.app.video

import android.graphics.Typeface
import id.nextfl.app.data.MediaExporter
import id.nextfl.app.platform.ExportTarget
import id.nextfl.app.platform.VideoExporter
import id.nextfl.app.platform.VideoRequest
import id.nextfl.core.pcm.MappedPcmSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/** MediaCodec MP4 with template visuals. */
class AndroidVideoExporter(
    private val exporter: MediaExporter,
    private val sampleRate: Int,
    private val typeface: () -> Typeface,
) : VideoExporter {

    override suspend fun export(
        request: VideoRequest,
        track: (ExportTarget) -> ExportTarget,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean,
    ): ExportTarget {
        val plan = withContext(Dispatchers.Default) { VideoPlan.resolve(request.aspect) }
        val output = track(exporter.createVideo(request.displayName))
        // GL needs one fixed thread
        Executors.newSingleThreadExecutor { Thread(it, "video-render") }.asCoroutineDispatcher().use { renderThread ->
            withContext(renderThread) {
                MappedPcmSource(request.mix, sampleRate).use { audio ->
                    exporter.openDescriptor(output).use { descriptor ->
                        VideoRenderer(plan, audio, request.settings, request.backgrounds, request.logos, typeface())
                            .render(descriptor.fileDescriptor, onProgress, isCancelled)
                    }
                }
            }
        }
        return output
    }
}
