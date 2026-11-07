package com.seldia.music.data.extractor

data class AudioStreamInfo(
    val url: String,
    val mimeType: String,
    val bitrate: Int,
    val format: AudioFormat
)

enum class AudioFormat {
    M4A_AAC,
    WEBM_OPUS,
    UNKNOWN
}

data class AudioTrackInfo(
    val id: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val audioStreams: List<AudioStreamInfo>
)