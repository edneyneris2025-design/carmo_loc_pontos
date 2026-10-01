package com.example.ui.map

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.data.model.SurveyPoint
import com.example.data.model.TrackPoint
import com.example.location.GpsStatus
import kotlinx.coroutines.launch
import kotlin.math.*

@Composable
fun SatelliteMapView(
    modifier: Modifier = Modifier,
    gpsStatus: GpsStatus,
    points: List<SurveyPoint>,
    trajectory: List<TrackPoint>,
    mapSource: MapTileSource = MapTileSource.SATELLITE,
    centerOnGpsTrigger: Int = 0,
    zoomInTrigger: Int = 0,
    zoomOutTrigger: Int = 0,
    selectedPoint: SurveyPoint? = null,
    onPointClick: (SurveyPoint) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tileManager = remember { TileManager(context) }

    // Map Center coordinates (Lat, Lon)
    var centerLat by remember { mutableDoubleStateOf(gpsStatus.latitude.takeIf { it != 0.0 } ?: -23.5505) }
    var centerLon by remember { mutableDoubleStateOf(gpsStatus.longitude.takeIf { it != 0.0 } ?: -46.6333) }
    var zoomLevel by remember { mutableFloatStateOf(17.0f) }

    // Follow GPS mode
    var followGps by remember { mutableStateOf(true) }

    // Zoom buttons handling
    LaunchedEffect(zoomInTrigger) {
        if (zoomInTrigger > 0) {
            zoomLevel = (zoomLevel + 1.0f).coerceIn(4f, 19.5f)
        }
    }

    LaunchedEffect(zoomOutTrigger) {
        if (zoomOutTrigger > 0) {
            zoomLevel = (zoomLevel - 1.0f).coerceIn(4f, 19.5f)
        }
    }

    // Auto-center when trigger changes or GPS updates if followGps is true
    LaunchedEffect(centerOnGpsTrigger) {
        if (gpsStatus.hasFix && gpsStatus.latitude != 0.0) {
            centerLat = gpsStatus.latitude
            centerLon = gpsStatus.longitude
            followGps = true
        }
    }

    LaunchedEffect(gpsStatus.latitude, gpsStatus.longitude) {
        if (followGps && gpsStatus.hasFix && gpsStatus.latitude != 0.0) {
            centerLat = gpsStatus.latitude
            centerLon = gpsStatus.longitude
        }
    }

    // Tile cache in state
    val tileBitmaps = remember { mutableStateMapOf<String, Bitmap>() }

    // Request visible tiles
    val currentIntZoom = zoomLevel.toInt().coerceIn(2, 19)
    LaunchedEffect(centerLat, centerLon, currentIntZoom, mapSource) {
        val centerTileX = TileManager.lonToTileX(centerLon, currentIntZoom)
        val centerTileY = TileManager.latToTileY(centerLat, currentIntZoom)
        val minX = (centerTileX - 2).toInt()
        val maxX = (centerTileX + 2).toInt()
        val minY = (centerTileY - 3).toInt()
        val maxY = (centerTileY + 3).toInt()

        for (tx in minX..maxX) {
            for (ty in minY..maxY) {
                val key = "${mapSource.name}_${currentIntZoom}_${tx}_${ty}"
                if (!tileBitmaps.containsKey(key)) {
                    scope.launch {
                        val bmp = tileManager.getTile(currentIntZoom, tx, ty, mapSource)
                        if (bmp != null) {
                            tileBitmaps[key] = bmp
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    followGps = false
                    zoomLevel = (zoomLevel * zoom).coerceIn(4f, 19.5f)

                    val intZ = zoomLevel.toInt().coerceIn(2, 19)
                    val factor = 256.0 * (1 shl intZ)
                    val deltaLon = -pan.x / factor * 360.0
                    centerLon = (centerLon + deltaLon).coerceIn(-180.0, 180.0)

                    val curY = TileManager.latToTileY(centerLat, intZ)
                    val newY = curY - (pan.y / 256.0)
                    centerLat = TileManager.tileYToLat(newY, intZ).coerceIn(-85.0, 85.0)
                }
            }
            .pointerInput(points) {
                detectTapGestures { tapOffset ->
                    // Check if clicked near any point
                    val intZ = zoomLevel.toInt().coerceIn(2, 19)
                    val scaleFactor = 2.0.pow((zoomLevel - intZ).toDouble())
                    val tileSize = 256.0 * scaleFactor
                    val cTileX = TileManager.lonToTileX(centerLon, intZ)
                    val cTileY = TileManager.latToTileY(centerLat, intZ)
                    val w = size.width
                    val h = size.height

                    for (pt in points) {
                        val ptTileX = TileManager.lonToTileX(pt.longitude, intZ)
                        val ptTileY = TileManager.latToTileY(pt.latitude, intZ)
                        val screenX = (w / 2.0 + (ptTileX - cTileX) * tileSize).toFloat()
                        val screenY = (h / 2.0 + (ptTileY - cTileY) * tileSize).toFloat()

                        val dist = hypot((tapOffset.x - screenX).toDouble(), (tapOffset.y - screenY).toDouble())
                        if (dist < 36.0) {
                            onPointClick(pt)
                            break
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height

            val intZ = zoomLevel.toInt().coerceIn(2, 19)
            val subZoomFactor = 2.0.pow((zoomLevel - intZ).toDouble())
            val tileSize = 256.0 * subZoomFactor

            val centerTileX = TileManager.lonToTileX(centerLon, intZ)
            val centerTileY = TileManager.latToTileY(centerLat, intZ)

            // Draw base background (dark grid for field surveying)
            drawRect(Color(0xFF111827))

            // 1. Draw Satellite Map Tiles
            val tilesAcross = ceil(canvasW / tileSize).toInt() + 2
            val tilesDown = ceil(canvasH / tileSize).toInt() + 2

            val startTileX = floor(centerTileX - tilesAcross / 2.0).toInt()
            val endTileX = ceil(centerTileX + tilesAcross / 2.0).toInt()
            val startTileY = floor(centerTileY - tilesDown / 2.0).toInt()
            val endTileY = ceil(centerTileY + tilesDown / 2.0).toInt()

            for (tx in startTileX..endTileX) {
                for (ty in startTileY..endTileY) {
                    val tileScreenX = (canvasW / 2.0 + (tx - centerTileX) * tileSize).toFloat()
                    val tileScreenY = (canvasH / 2.0 + (ty - centerTileY) * tileSize).toFloat()

                    val key = "${mapSource.name}_${intZ}_${tx}_${ty}"
                    val bmp = tileBitmaps[key]
                    if (bmp != null && !bmp.isRecycled) {
                        drawImage(
                            image = bmp.asImageBitmap(),
                            dstOffset = IntOffset(tileScreenX.roundToInt(), tileScreenY.roundToInt()),
                            dstSize = IntSize(tileSize.roundToInt() + 1, tileSize.roundToInt() + 1)
                        )
                    } else {
                        // Drawing placeholder grid while tile loads or when offline
                        drawRect(
                            color = Color(0xFF1E293B),
                            topLeft = Offset(tileScreenX, tileScreenY),
                            size = androidx.compose.ui.geometry.Size(tileSize.toFloat(), tileSize.toFloat())
                        )
                        drawRect(
                            color = Color(0x33475569),
                            topLeft = Offset(tileScreenX, tileScreenY),
                            size = androidx.compose.ui.geometry.Size(tileSize.toFloat(), tileSize.toFloat()),
                            style = Stroke(1f)
                        )
                    }
                }
            }

            // Function to convert LatLon to Screen Offset
            fun toScreen(lat: Double, lon: Double): Offset {
                val ptTileX = TileManager.lonToTileX(lon, intZ)
                val ptTileY = TileManager.latToTileY(lat, intZ)
                val screenX = (canvasW / 2.0 + (ptTileX - centerTileX) * tileSize).toFloat()
                val screenY = (canvasH / 2.0 + (ptTileY - centerTileY) * tileSize).toFloat()
                return Offset(screenX, screenY)
            }

            // 2. Draw Real-time Trajectory Polyline
            if (trajectory.size >= 2) {
                val trackPath = Path()
                val firstPt = toScreen(trajectory.first().latitude, trajectory.first().longitude)
                trackPath.moveTo(firstPt.x, firstPt.y)
                for (i in 1 until trajectory.size) {
                    val p = toScreen(trajectory[i].latitude, trajectory[i].longitude)
                    trackPath.lineTo(p.x, p.y)
                }

                // Glowing outline for trajectory visibility on satellite imagery
                drawPath(
                    path = trackPath,
                    color = Color(0x6600E5FF),
                    style = Stroke(width = 9f)
                )
                drawPath(
                    path = trackPath,
                    color = Color(0xFF00E5FF),
                    style = Stroke(width = 4f)
                )
            }

            // 3. Draw Sequential Survey Line
            if (points.size >= 2) {
                val polyPath = Path()
                val p0 = toScreen(points.first().latitude, points.first().longitude)
                polyPath.moveTo(p0.x, p0.y)
                for (i in 1 until points.size) {
                    val p = toScreen(points[i].latitude, points[i].longitude)
                    polyPath.lineTo(p.x, p.y)
                }
                drawPath(
                    path = polyPath,
                    color = Color(0xFFFFD740),
                    style = Stroke(width = 3.5f)
                )
            }

            // 4. Draw Survey Points Pins & Labels
            val textPaint = Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 32f
                isAntiAlias = true
                isFakeBoldText = true
                setShadowLayer(4f, 1f, 1f, android.graphics.Color.BLACK)
            }

            val badgePaint = Paint().apply {
                color = android.graphics.Color.parseColor("#CC0D1B2A")
                isAntiAlias = true
                style = Paint.Style.FILL
            }

            for (pt in points) {
                val pos = toScreen(pt.latitude, pt.longitude)
                val isSel = pt.id == selectedPoint?.id

                // Point outer glow/halo
                drawCircle(
                    color = if (isSel) Color(0xFFFFEB3B) else Color(0xFFFF1744),
                    radius = if (isSel) 14f else 9f,
                    center = pos
                )
                drawCircle(
                    color = Color.White,
                    radius = if (isSel) 8f else 5f,
                    center = pos
                )

                // Label Badge (P001, P002...)
                val label = pt.pointName
                drawIntoCanvas { canvas ->
                    val labelWidth = textPaint.measureText(label)
                    val bgLeft = pos.x + 14f
                    val bgTop = pos.y - 38f
                    val bgRight = bgLeft + labelWidth + 16f
                    val bgBottom = pos.y - 4f

                    canvas.nativeCanvas.drawRoundRect(bgLeft, bgTop, bgRight, bgBottom, 8f, 8f, badgePaint)
                    canvas.nativeCanvas.drawText(label, bgLeft + 8f, bgBottom - 8f, textPaint)
                }
            }

            // 5. Draw GPS User Position Marker (Real-time)
            if (gpsStatus.hasFix && gpsStatus.latitude != 0.0) {
                val gpsPos = toScreen(gpsStatus.latitude, gpsStatus.longitude)

                // Accuracy circle in pixels:
                // Earth circumference ~ 40,075,000 meters.
                val metersPerPixel = (156543.03392 * cos(Math.toRadians(gpsStatus.latitude))) / (2.0.pow(zoomLevel.toDouble()))
                val accuracyRadiusPx = (gpsStatus.accuracy / metersPerPixel).toFloat().coerceIn(10f, 400f)

                drawCircle(
                    color = Color(0x332196F3),
                    radius = accuracyRadiusPx,
                    center = gpsPos
                )
                drawCircle(
                    color = Color(0x992196F3),
                    radius = accuracyRadiusPx,
                    center = gpsPos,
                    style = Stroke(2f)
                )

                // Heading beam if available
                if (gpsStatus.bearing != 0.0f) {
                    val angleRad = Math.toRadians((gpsStatus.bearing - 90).toDouble())
                    val beamLen = 42f
                    val arrowEnd = Offset(
                        gpsPos.x + (beamLen * cos(angleRad)).toFloat(),
                        gpsPos.y + (beamLen * sin(angleRad)).toFloat()
                    )
                    drawLine(
                        color = Color(0xFF00E5FF),
                        start = gpsPos,
                        end = arrowEnd,
                        strokeWidth = 5f
                    )
                }

                // Core pulsing GPS dot
                drawCircle(
                    color = Color.White,
                    radius = 11f,
                    center = gpsPos
                )
                drawCircle(
                    color = Color(0xFF1E88E5),
                    radius = 8f,
                    center = gpsPos
                )
            }

            // 6. Draw Map Center Crosshair
            val cx = canvasW / 2.0f
            val cy = canvasH / 2.0f
            val crosshairLen = 14f
            drawLine(Color(0x88FFFFFF), Offset(cx - crosshairLen, cy), Offset(cx + crosshairLen, cy), 1.5f)
            drawLine(Color(0x88FFFFFF), Offset(cx, cy - crosshairLen), Offset(cx, cy + crosshairLen), 1.5f)
        }
    }
}
