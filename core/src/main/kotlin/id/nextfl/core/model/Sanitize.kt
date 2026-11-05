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
            masterFx = fx(doc.masterFx),
            tracks = tracks,
            clips = clips,
        )
    }

    private fun track(track: Track): Track = track.withFx(fx(track.fx)).copy(
        name = track.name.take(MAX_NAME),
        color = Defaults.migrateTrackColor(track.color),
        volumeDb = track.volumeDb.coerceIn(AudioMath.MIN_DB, 6f),
        pan = track.pan.coerceIn(-1f, 1f),
    )

    fun fx(chain: FxChain): FxChain = chain.copy(
        eq = chain.eq.copy(
            lowCut = chain.eq.lowCut.coerceIn(FxRanges.EQ_LOW_CUT),
            lowGain = chain.eq.lowGain.coerceIn(FxRanges.EQ_GAIN),
            midGain = chain.eq.midGain.coerceIn(FxRanges.EQ_GAIN),
            midFreq = chain.eq.midFreq.coerceIn(FxRanges.EQ_MID_FREQ),
            highGain = chain.eq.highGain.coerceIn(FxRanges.EQ_GAIN),
            highCut = chain.eq.highCut.coerceIn(FxRanges.EQ_HIGH_CUT),
        ),
        stereoizer = chain.stereoizer.copy(
            strength = chain.stereoizer.strength.coerceIn(FxRanges.UNIT),
            timbre = chain.stereoizer.timbre.coerceIn(FxRanges.UNIT),
            focus = chain.stereoizer.focus.coerceIn(FxRanges.UNIT),
            lrDelay = chain.stereoizer.lrDelay.coerceIn(FxRanges.BIPOLAR),
            pan = chain.stereoizer.pan.coerceIn(FxRanges.BIPOLAR),
        ),
        reverb = reverb(chain.reverb),
        graphicEq = chain.graphicEq.copy(
            gains = List(GraphicEqSettings.BANDS) { band -> chain.graphicEq.gains.getOrElse(band) { 0f }.coerceIn(FxRanges.GRAPHIC_GAIN) },
        ),
        leveller = chain.leveller.copy(
            gainDb = chain.leveller.gainDb.coerceIn(FxRanges.LEVELLER_GAIN),
            pan = chain.leveller.pan.coerceIn(FxRanges.BIPOLAR),
        ),
    )

    private fun reverb(settings: ReverbSettings): ReverbSettings = settings.copy(
        mix = settings.mix.coerceIn(FxRanges.UNIT),
        decay = settings.decay.coerceIn(FxRanges.REVERB_DECAY),
        preDelay = settings.preDelay.coerceIn(FxRanges.REVERB_PRE_DELAY),
        tone = settings.tone.coerceIn(FxRanges.REVERB_TONE),
        damping = settings.damping.coerceIn(FxRanges.UNIT),
        earlyReflections = settings.earlyReflections.coerceIn(FxRanges.UNIT),
        roomSize = settings.roomSize.coerceIn(FxRanges.UNIT),
        lowCut = settings.lowCut.coerceIn(FxRanges.REVERB_LOW_CUT),
        diffusion = settings.diffusion.coerceIn(FxRanges.UNIT),
        width = settings.width.coerceIn(FxRanges.UNIT),
        dryDb = settings.dryDb.coerceIn(FxRanges.REVERB_TRIM_DB),
        wetDb = settings.wetDb.coerceIn(FxRanges.REVERB_TRIM_DB),
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
        syncMs = settings.syncMs.coerceIn(0, VisualSettings.MAX_SYNC_MS),
        rippleAmount = settings.rippleAmount.coerceIn(FxRanges.UNIT),
        pendulumAmount = settings.pendulumAmount.coerceIn(FxRanges.UNIT),
        glitchAmount = settings.glitchAmount.coerceIn(FxRanges.UNIT),
        particleColor = if (settings.particleColor == VisualSettings.LEGACY_PARTICLE) VisualSettings.WHITE else settings.particleColor,
        backgroundDim = settings.backgroundDim.coerceIn(0f, 0.95f),
        backgroundBlur = settings.backgroundBlur.coerceIn(0f, 20f),
        particleCount = settings.particleCount.coerceIn(0, 200),
        cometCount = settings.cometCount.coerceIn(0, VisualSettings.MAX_COMETS),
    )
}
