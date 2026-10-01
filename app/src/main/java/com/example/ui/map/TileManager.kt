package com.example.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import kotlin.math.*

enum class MapTileSource(val title: String) {
    SATELLITE("Satélite (Esri World Imagery)"),
    STREET("Vias / OpenStreetMap"),
    TOPO("Topográfico (OpenTopoMap)")
}

class TileManager(context: Context) {

    private val cacheDir = File(context.cacheDir, "survey_satellite_tiles").apply { mkdirs() }
    private val httpClient = OkHttpClient.Builder()
        .cache(Cache(cacheDir, 100L * 1024 * 1024)) // 100 MB disk cache for offline viewing
        .build()

    private val memoryCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(150) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    suspend fun getTile(zoom: Int, x: Int, y: Int, source: MapTileSource): Bitmap? = withContext(Dispatchers.IO) {
        val maxTile = (1 shl zoom) - 1
        if (x < 0 || x > maxTile || y < 0 || y > maxTile) return@withContext null

        val cacheKey = "${source.name}_${zoom}_${x}_${y}"
        memoryCache.get(cacheKey)?.let { return@withContext it }

        // Check local disk cache
        val localFile = File(cacheDir, "$cacheKey.png")
        if (localFile.exists()) {
            val bitmap = BitmapFactory.decodeFile(localFile.absolutePath)
            if (bitmap != null) {
                memoryCache.put(cacheKey, bitmap)
                return@withContext bitmap
            }
        }

        // Fetch from network if online
        val url = when (source) {
            MapTileSource.SATELLITE ->
                "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$zoom/$y/$x"
            MapTileSource.STREET ->
                "https://tile.openstreetmap.org/$zoom/$x/$y.png"
            MapTileSource.TOPO ->
                "https://tile.opentopomap.org/$zoom/$x/$y.png"
        }

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "TopoUTM-Surveyor/1.0 (Android)")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val bytes = response.body?.bytes()
                if (bytes != null) {
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) {
                        memoryCache.put(cacheKey, bitmap)
                        try {
                            localFile.writeBytes(bytes)
                        } catch (_: Exception) {}
                        return@withContext bitmap
                    }
                }
            }
        } catch (_: Exception) {
            // Offline: returns null and renders smooth fallback grid
        }
        return@withContext null
    }

    companion object {
        fun lonToTileX(lon: Double, zoom: Int): Double {
            return (lon + 180.0) / 360.0 * (1 shl zoom)
        }

        fun latToTileY(lat: Double, zoom: Int): Double {
            val rad = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
            return (1.0 - asinh(tan(rad)) / Math.PI) / 2.0 * (1 shl zoom)
        }

        fun tileXToLon(x: Double, zoom: Int): Double {
            return x / (1 shl zoom) * 360.0 - 180.0
        }

        fun tileYToLat(y: Double, zoom: Int): Double {
            val n = Math.PI - 2.0 * Math.PI * y / (1 shl zoom)
            return Math.toDegrees(atan(sinh(n)))
        }
    }
}
