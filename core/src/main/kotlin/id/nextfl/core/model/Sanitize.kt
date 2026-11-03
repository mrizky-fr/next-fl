package id.nextfl.core.model

import id.nextfl.core.util.AudioMath

// Clamp values from untrusted storage
object Sanitize {
    private const val MAX_NAME = 120

    fun project(doc: ProjectDoc, assets: AssetMap): ProjectDoc {
        val tracks = doc.tracks.distinctBy { it.id }.map(::track)
        val trackIds = tracks.map { it.id }.toSet()
        val clips = doc.clips
            .distinctBy { it.id }
            .filter { it.trackId in trackIds && it.assetId in assets && it.length > 0 }
            .map(::clip)
        return doc.copy(
            bpm = doc.bpm.coerceIn(ProjectDoc.MIN_BPM, ProjectDoc.MAX_BPM),
            masterDb = doc.masterDb.coerceIn(AudioMath.MIN_DB, 6f),
            tracks = tracks,
            clips = clips,
        )
    }

    private fun track(track: Track): Track = track.copy(
        name = track.name.take(MAX_NAME),
        color = Defaults.migrateTrackColor(track.color),
        volumeDb = track.volumeDb.coerceIn(AudioMath.MIN_DB, 6f),
        pan = track.pan.coerceIn(-1f, 1f),
        eq = track.eq.copy(
            lowCut = track.eq.lowCut.coerceIn(20f, 1000f),
            lowGain = track.eq.lowGain.coerceIn(-18f, 18f),
            midGain = track.eq.midGain.coerceIn(-18f, 18f),
            midFreq = track.eq.midFreq.coerceIn(150f, 8000f),
            highGain = track.eq.highGain.coerceIn(-18f, 18f),
            highCut = track.eq.highCut.coerceIn(1000f, 20_000f),
        ),
        reverb = track.reverb.copy(
            mix = track.reverb.mix.coerceIn(0f, 1f),
            decay = track.reverb.decay.coerceIn(0.3f, 8f),
            preDelay = track.reverb.preDelay.coerceIn(0f, 0.25f),
            tone = track.reverb.tone.coerceIn(1000f, 18_000f),
        ),
    )

    private fun clip(clip: Clip): Clip = clip.copy(
        name = clip.name.take(MAX_NAME),
        startBeat = clip.startBeat.coerceAtLeast(0.0),
        offset = clip.offset.coerceAtLeast(0.0),
        gainDb = clip.gainDb.coerceIn(AudioMath.MIN_DB, 24f),
        fadeIn = clip.fadeIn.coerceAtLeast(0.0),
        fadeOut = clip.fadeOut.coerceAtLeast(0.0),
        speed = clip.speed.coerceIn(0.25, 4.0),
    )

    fun visual(settings: VisualSettings): VisualSettings = settings.copy(
        title = settings.title.take(80),
        sensitivity = settings.sensitivity.coerceIn(0.3f, 2.5f),
        backgroundDim = settings.backgroundDim.coerceIn(0f, 0.95f),
        backgroundBlur = settings.backgroundBlur.coerceIn(0f, 20f),
        particleCount = settings.particleCount.coerceIn(0, 200),
    )
}
