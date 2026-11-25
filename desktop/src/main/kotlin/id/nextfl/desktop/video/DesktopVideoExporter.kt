package id.nextfl.desktop.video

import android.graphics.Typeface
import id.nextfl.app.platform.ExportTarget
import id.nextfl.app.platform.VideoExporter
import id.nextfl.app.platform.VideoRequest
import id.nextfl.app.video.VideoFrames
import id.nextfl.core.pcm.MappedPcmSource
import id.nextfl.desktop.data.DesktopExporter
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/** Media Foundation MP4 export. */
class DesktopVideoExporter(
    private val exporter: DesktopExporter,
    private val sampleRate: Int,
    private val typeface: () -> Typeface,
) : VideoExporter {

    override suspend fun export(
        request: VideoRequest,
        track: (ExportTarget) -> ExportTarget,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean,
    ): ExportTarget {
        val (width, height) = VideoFrames.frameSize(request.aspect, SHORT_SIDE)
        val output = exporter.createVideo(request.displayName)
        track(output)
        // COM needs one fixed thread
        Executors.newSingleThreadExecutor { Thread(it, "video-render").apply { isDaemon = true } }.asCoroutineDispatcher().use { renderThread ->
            withContext(renderThread) {
                MappedPcmSource(request.mix, sampleRate).use { audio ->
                    DesktopVideoRenderer(width, height, audio, request.settings, request.backgrounds, request.logos, typeface())
                        .render(output.part, onProgress, isCancelled)
                }
            }
        }
        return output
    }

    private companion object {
        const val SHORT_SIDE = 1080
    }
}
