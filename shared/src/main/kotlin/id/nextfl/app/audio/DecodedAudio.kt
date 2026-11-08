package id.nextfl.app.audio

import id.nextfl.core.pcm.WaveformPeaks

class AudioDecodeException(message: String) : Exception(message)

class DecodedAudio(val frames: Int, val peaks: WaveformPeaks)
