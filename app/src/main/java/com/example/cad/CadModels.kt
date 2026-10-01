package com.example.cad

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min

sealed interface CadEntity {
    val layer: String
    val color: Long
    val composeColor: Color
        get() = Color(color.toInt())
}

data class CadPoint(
    val x: Double,
    val y: Double,
    val z: Double = 0.0,
    override val layer: String = "0",
    override val color: Long = 0xFFE53935, // Red
    val label: String = ""
) : CadEntity

data class CadLine(
    val startX: Double,
    val startY: Double,
    val startZ: Double = 0.0,
    val endX: Double,
    val endY: Double,
    val endZ: Double = 0.0,
    override val layer: String = "0",
    override val color: Long = 0xFFFFFFFF
) : CadEntity

data class CadPolyline(
    val vertices: List<Pair<Double, Double>>,
    val isClosed: Boolean = false,
    override val layer: String = "0",
    override val color: Long = 0xFF00E5FF // Cyan
) : CadEntity

data class CadCircle(
    val centerX: Double,
    val centerY: Double,
    val radius: Double,
    override val layer: String = "0",
    override val color: Long = 0xFFFFD600 // Yellow
) : CadEntity

data class CadArc(
    val centerX: Double,
    val centerY: Double,
    val radius: Double,
    val startAngle: Double,
    val endAngle: Double,
    override val layer: String = "0",
    override val color: Long = 0xFFFFD600
) : CadEntity

data class CadText(
    val x: Double,
    val y: Double,
    val text: String,
    val height: Double = 2.0,
    val rotation: Double = 0.0,
    override val layer: String = "0",
    override val color: Long = 0xFF69F0AE // Green
) : CadEntity

data class CadBounds(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double
) {
    fun width(): Double = max(0.1, maxX - minX)
    fun height(): Double = max(0.1, maxY - minY)
    fun centerX(): Double = (minX + maxX) / 2.0
    fun centerY(): Double = (minY + maxY) / 2.0

    companion object {
        fun fromEntities(entities: List<CadEntity>): CadBounds {
            if (entities.isEmpty()) return CadBounds(0.0, 0.0, 100.0, 100.0)
            var minX = Double.POSITIVE_INFINITY
            var minY = Double.POSITIVE_INFINITY
            var maxX = Double.NEGATIVE_INFINITY
            var maxY = Double.NEGATIVE_INFINITY

            for (e in entities) {
                when (e) {
                    is CadPoint -> {
                        minX = min(minX, e.x)
                        maxX = max(maxX, e.x)
                        minY = min(minY, e.y)
                        maxY = max(maxY, e.y)
                    }
                    is CadLine -> {
                        minX = min(minX, min(e.startX, e.endX))
                        maxX = max(maxX, max(e.startX, e.endX))
                        minY = min(minY, min(e.startY, e.endY))
                        maxY = max(maxY, max(e.startY, e.endY))
                    }
                    is CadPolyline -> {
                        for ((x, y) in e.vertices) {
                            minX = min(minX, x)
                            maxX = max(maxX, x)
                            minY = min(minY, y)
                            maxY = max(maxY, y)
                        }
                    }
                    is CadCircle -> {
                        minX = min(minX, e.centerX - e.radius)
                        maxX = max(maxX, e.centerX + e.radius)
                        minY = min(minY, e.centerY - e.radius)
                        maxY = max(maxY, e.centerY + e.radius)
                    }
                    is CadArc -> {
                        minX = min(minX, e.centerX - e.radius)
                        maxX = max(maxX, e.centerX + e.radius)
                        minY = min(minY, e.centerY - e.radius)
                        maxY = max(maxY, e.centerY + e.radius)
                    }
                    is CadText -> {
                        minX = min(minX, e.x)
                        maxX = max(maxX, e.x + e.height * max(1, e.text.length))
                        minY = min(minY, e.y)
                        maxY = max(maxY, e.y + e.height)
                    }
                }
            }
            if (minX.isInfinite() || maxX.isInfinite()) {
                return CadBounds(0.0, 0.0, 100.0, 100.0)
            }
            // Add slight margin
            val padX = max(1.0, (maxX - minX) * 0.05)
            val padY = max(1.0, (maxY - minY) * 0.05)
            return CadBounds(minX - padX, minY - padY, maxX + padX, maxY + padY)
        }
    }
}

data class CadLayer(
    val name: String,
    val color: Long,
    val isVisible: Boolean = true
) {
    val composeColor: Color
        get() = Color(color.toInt())
}

data class CadDrawing(
    val title: String,
    val layers: Map<String, CadLayer>,
    val entities: List<CadEntity>,
    val bounds: CadBounds,
    val format: String = "DXF",
    val description: String = ""
)
