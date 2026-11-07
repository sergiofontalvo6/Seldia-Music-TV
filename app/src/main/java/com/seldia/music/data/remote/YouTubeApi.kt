package com.seldia.music.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

/* ---- Modelos de YouTube Data API v3 ---- */

data class YouTubeSearchResponse(
    @SerializedName("items") val items: List<YouTubeSearchItem>?
)

data class YouTubeSearchItem(
    @SerializedName("id") val id: YouTubeItemId?,
    @SerializedName("snippet") val snippet: YouTubeSnippet?
)

data class YouTubeItemId(
    @SerializedName("videoId") val videoId: String?
)

data class YouTubeSnippet(
    @SerializedName("title") val title: String?,
    @SerializedName("channelTitle") val channelTitle: String?,
    @SerializedName("thumbnails") val thumbnails: YouTubeThumbnails?
)

data class YouTubeThumbnails(
    @SerializedName("high") val high: YouTubeThumbnail?,
    @SerializedName("medium") val medium: YouTubeThumbnail?,
    @SerializedName("default") val default: YouTubeThumbnail?
)

data class YouTubeThumbnail(
    @SerializedName("url") val url: String?
)

/* ---- Interfaz Retrofit ---- */

interface YouTubeApi {

    @GET("youtube/v3/search")
    suspend fun search(
        @Query("part") part: String = "snippet",
        @Query("q") q: String,
        @Query("type") type: String = "video",
        @Query("maxResults") maxResults: Int = 25,
        @Query("key") key: String
    ): YouTubeSearchResponse
}
