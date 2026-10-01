package com.example.cad

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Locale

object DxfParser {

    /**
     * Map AutoCAD Color Index (ACI 1-7) to ARGB color
     */
    fun aciToColor(aci: Int): Long {
        return when (aci) {
            1 -> 0xFFFF5252 // Red
            2 -> 0xFFFFD740 // Yellow
            3 -> 0xFF69F0AE // Green
            4 -> 0xFF40C4FF // Cyan
            5 -> 0xFF448AFF // Blue
            6 -> 0xFFE040FB // Magenta
            7 -> 0xFFECEFF1 // White / Off-white
            8 -> 0xFF78909C // Dark Gray
            9 -> 0xFFB0BEC5 // Light Gray
            else -> 0xFF00E5FF // Default Cyan
        }
    }

    /**
     * Checks if the stream is a binary DWG file and extracts version header
     */
    fun inspectDwg(inputStream: InputStream): DwgInfo? {
        val buffer = ByteArray(16)
        val read = inputStream.read(buffer)
        if (read >= 6) {
            val header = String(buffer, 0, 6)
            if (header.startsWith("AC")) {
                val versionName = when (header) {
                    "AC1015" -> "AutoCAD 2000/2000i/2002"
                    "AC1018" -> "AutoCAD 2004/2005/2006"
                    "AC1021" -> "AutoCAD 2007/2008/2009"
                    "AC1024" -> "AutoCAD 2010/2011/2012"
                    "AC1027" -> "AutoCAD 2013/2014/2015/2016/2017"
                    "AC1032" -> "AutoCAD 2018/2021/2024+"
                    "AC1009" -> "AutoCAD Release 11/12"
                    else -> "AutoCAD DWG ($header)"
                }
                return DwgInfo(header, versionName)
            }
        }
        return null
    }

    data class DwgInfo(
        val headerCode: String,
        val versionDescription: String
    )

    /**
     * Parses an ASCII DXF stream into a CadDrawing
     */
    fun parseDxf(inputStream: InputStream, fileName: String = "Desenho.dxf"): CadDrawing {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val entities = mutableListOf<CadEntity>()
        val layers = mutableMapOf<String, CadLayer>()

        var currentSection = ""
        var currentTable = ""

        // Group code / value pair reading
        while (true) {
            val codeLine = reader.readLine() ?: break
            val valLine = reader.readLine() ?: break

            val code = codeLine.trim().toIntOrNull() ?: continue
            val value = valLine.trim()

            if (code == 0 && value == "SECTION") {
                val nextCode = reader.readLine()?.trim()?.toIntOrNull()
                val nextVal = reader.readLine()?.trim() ?: ""
                if (nextCode == 2) {
                    currentSection = nextVal
                }
                continue
            }

            if (code == 0 && value == "ENDSEC") {
                currentSection = ""
                continue
            }

            if (currentSection == "TABLES") {
                if (code == 0 && value == "TABLE") {
                    val nextCode = reader.readLine()?.trim()?.toIntOrNull()
                    val nextVal = reader.readLine()?.trim() ?: ""
                    if (nextCode == 2) {
                        currentTable = nextVal
                    }
                    continue
                }
                if (currentTable == "LAYER" && code == 2) {
                    val layerName = value
                    var layerColor = 0xFFFFFFFF
                    // Read layer attributes until next entity (code 0)
                    while (true) {
                        reader.mark(512)
                        val subCodeLine = reader.readLine() ?: break
                        val subValLine = reader.readLine() ?: break
                        val subCode = subCodeLine.trim().toIntOrNull() ?: break
                        val subVal = subValLine.trim()
                        if (subCode == 0) {
                            reader.reset()
                            break
                        }
                        if (subCode == 62) {
                            val aci = subVal.toIntOrNull() ?: 7
                            layerColor = aciToColor(aci)
                        }
                    }
                    layers[layerName] = CadLayer(layerName, layerColor)
                }
                continue
            }

            if (currentSection == "ENTITIES") {
                if (code == 0) {
                    val entityType = value.uppercase(Locale.US)
                    when (entityType) {
                        "POINT" -> parsePoint(reader)?.let { entities.add(it) }
                        "LINE" -> parseLine(reader)?.let { entities.add(it) }
                        "LWPOLYLINE" -> parseLwPolyline(reader)?.let { entities.add(it) }
                        "POLYLINE" -> parsePolyline(reader)?.let { entities.add(it) }
                        "CIRCLE" -> parseCircle(reader)?.let { entities.add(it) }
                        "ARC" -> parseArc(reader)?.let { entities.add(it) }
                        "TEXT", "MTEXT" -> parseText(reader)?.let { entities.add(it) }
                    }
                }
            }
        }

        // Default layer if none parsed
        if (layers.isEmpty()) {
            layers["0"] = CadLayer("0", 0xFFFFFFFF)
        }

        val bounds = CadBounds.fromEntities(entities)
        return CadDrawing(
            title = fileName,
            layers = layers,
            entities = entities,
            bounds = bounds,
            format = "DXF",
            description = "${entities.size} entidades CAD carregadas"
        )
    }

    private fun parsePoint(reader: BufferedReader): CadPoint? {
        var x = 0.0
        var y = 0.0
        var z = 0.0
        var layer = "0"
        var color = 0xFFFF5252

        while (true) {
            reader.mark(512)
            val cLine = reader.readLine() ?: break
            val vLine = reader.readLine() ?: break
            val c = cLine.trim().toIntOrNull() ?: break
            val v = vLine.trim()

            if (c == 0) {
                reader.reset()
                break
            }
            when (c) {
                8 -> layer = v
                10 -> x = v.toDoubleOrNull() ?: x
                20 -> y = v.toDoubleOrNull() ?: y
                30 -> z = v.toDoubleOrNull() ?: z
                62 -> color = aciToColor(v.toIntOrNull() ?: 1)
            }
        }
        return CadPoint(x, y, z, layer, color)
    }

    private fun parseLine(reader: BufferedReader): CadLine? {
        var startX = 0.0
        var startY = 0.0
        var startZ = 0.0
        var endX = 0.0
        var endY = 0.0
        var endZ = 0.0
        var layer = "0"
        var color = 0xFFFFFFFF

        while (true) {
            reader.mark(512)
            val cLine = reader.readLine() ?: break
            val vLine = reader.readLine() ?: break
            val c = cLine.trim().toIntOrNull() ?: break
            val v = vLine.trim()

            if (c == 0) {
                reader.reset()
                break
            }
            when (c) {
                8 -> layer = v
                10 -> startX = v.toDoubleOrNull() ?: startX
                20 -> startY = v.toDoubleOrNull() ?: startY
                30 -> startZ = v.toDoubleOrNull() ?: startZ
                11 -> endX = v.toDoubleOrNull() ?: endX
                21 -> endY = v.toDoubleOrNull() ?: endY
                31 -> endZ = v.toDoubleOrNull() ?: endZ
                62 -> color = aciToColor(v.toIntOrNull() ?: 7)
            }
        }
        return CadLine(startX, startY, startZ, endX, endY, endZ, layer, color)
    }

    private fun parseLwPolyline(reader: BufferedReader): CadPolyline? {
        val vertices = mutableListOf<Pair<Double, Double>>()
        var isClosed = false
        var layer = "0"
        var color = 0xFF00E5FF
        var currentX: Double? = null

        while (true) {
            reader.mark(512)
            val cLine = reader.readLine() ?: break
            val vLine = reader.readLine() ?: break
            val c = cLine.trim().toIntOrNull() ?: break
            val v = vLine.trim()

            if (c == 0) {
                reader.reset()
                break
            }
            when (c) {
                8 -> layer = v
                70 -> isClosed = ((v.toIntOrNull() ?: 0) and 1) != 0
                62 -> color = aciToColor(v.toIntOrNull() ?: 4)
                10 -> currentX = v.toDoubleOrNull()
                20 -> {
                    val y = v.toDoubleOrNull()
                    if (currentX != null && y != null) {
                        vertices.add(Pair(currentX, y))
                        currentX = null
                    }
                }
            }
        }
        return if (vertices.isNotEmpty()) CadPolyline(vertices, isClosed, layer, color) else null
    }

    private fun parsePolyline(reader: BufferedReader): CadPolyline? {
        val vertices = mutableListOf<Pair<Double, Double>>()
        var isClosed = false
        var layer = "0"
        var color = 0xFFFFD740

        while (true) {
            val cLine = reader.readLine() ?: break
            val vLine = reader.readLine() ?: break
            val c = cLine.trim().toIntOrNull() ?: break
            val v = vLine.trim()

            if (c == 0 && v == "SEQEND") {
                break
            }
            if (c == 8) layer = v
            if (c == 70) isClosed = ((v.toIntOrNull() ?: 0) and 1) != 0
            if (c == 62) color = aciToColor(v.toIntOrNull() ?: 2)

            if (c == 0 && v == "VERTEX") {
                var vx = 0.0
                var vy = 0.0
                while (true) {
                    reader.mark(512)
                    val scLine = reader.readLine() ?: break
                    val svLine = reader.readLine() ?: break
                    val sc = scLine.trim().toIntOrNull() ?: break
                    val sv = svLine.trim()
                    if (sc == 0) {
                        reader.reset()
                        break
                    }
                    if (sc == 10) vx = sv.toDoubleOrNull() ?: vx
                    if (sc == 20) vy = sv.toDoubleOrNull() ?: vy
                }
                vertices.add(Pair(vx, vy))
            }
        }
        return if (vertices.isNotEmpty()) CadPolyline(vertices, isClosed, layer, color) else null
    }

    private fun parseCircle(reader: BufferedReader): CadCircle? {
        var x = 0.0
        var y = 0.0
        var r = 1.0
        var layer = "0"
        var color = 0xFFFFD600

        while (true) {
            reader.mark(512)
            val cLine = reader.readLine() ?: break
            val vLine = reader.readLine() ?: break
            val c = cLine.trim().toIntOrNull() ?: break
            val v = vLine.trim()

            if (c == 0) {
                reader.reset()
                break
            }
            when (c) {
                8 -> layer = v
                10 -> x = v.toDoubleOrNull() ?: x
                20 -> y = v.toDoubleOrNull() ?: y
                40 -> r = v.toDoubleOrNull() ?: r
                62 -> color = aciToColor(v.toIntOrNull() ?: 2)
            }
        }
        return CadCircle(x, y, r, layer, color)
    }

    private fun parseArc(reader: BufferedReader): CadArc? {
        var x = 0.0
        var y = 0.0
        var r = 1.0
        var startA = 0.0
        var endA = 360.0
        var layer = "0"
        var color = 0xFFFFD600

        while (true) {
            reader.mark(512)
            val cLine = reader.readLine() ?: break
            val vLine = reader.readLine() ?: break
            val c = cLine.trim().toIntOrNull() ?: break
            val v = vLine.trim()

            if (c == 0) {
                reader.reset()
                break
            }
            when (c) {
                8 -> layer = v
                10 -> x = v.toDoubleOrNull() ?: x
                20 -> y = v.toDoubleOrNull() ?: y
                40 -> r = v.toDoubleOrNull() ?: r
                50 -> startA = v.toDoubleOrNull() ?: startA
                51 -> endA = v.toDoubleOrNull() ?: endA
                62 -> color = aciToColor(v.toIntOrNull() ?: 2)
            }
        }
        return CadArc(x, y, r, startA, endA, layer, color)
    }

    private fun parseText(reader: BufferedReader): CadText? {
        var x = 0.0
        var y = 0.0
        var text = ""
        var height = 2.0
        var rot = 0.0
        var layer = "0"
        var color = 0xFF69F0AE

        while (true) {
            reader.mark(512)
            val cLine = reader.readLine() ?: break
            val vLine = reader.readLine() ?: break
            val c = cLine.trim().toIntOrNull() ?: break
            val v = vLine.trim()

            if (c == 0) {
                reader.reset()
                break
            }
            when (c) {
                8 -> layer = v
                10 -> x = v.toDoubleOrNull() ?: x
                20 -> y = v.toDoubleOrNull() ?: y
                40 -> height = v.toDoubleOrNull() ?: height
                50 -> rot = v.toDoubleOrNull() ?: rot
                1 -> text = v
                62 -> color = aciToColor(v.toIntOrNull() ?: 3)
            }
        }
        return if (text.isNotBlank()) CadText(x, y, text, height, rot, layer, color) else null
    }

    /**
     * Creates a rich topography sample CAD drawing for instant inspection and testing
     */
    fun createSampleTopographyDrawing(): CadDrawing {
        val entities = mutableListOf<CadEntity>()
        val layers = mutableMapOf<String, CadLayer>()

        layers["CURVAS_NIVEL_MESTRAS"] = CadLayer("CURVAS_NIVEL_MESTRAS", 0xFFFF8F00)
        layers["CURVAS_NIVEL_INTERM"] = CadLayer("CURVAS_NIVEL_INTERM", 0xFFFFB300)
        layers["DIVISA_POLIGONAL"] = CadLayer("DIVISA_POLIGONAL", 0xFF00E676)
        layers["PONTOS_VERTICES"] = CadLayer("PONTOS_VERTICES", 0xFFFF1744)
        layers["TEXTOS_COTAS"] = CadLayer("TEXTOS_COTAS", 0xFF00E5FF)
        layers["EDIFICACOES"] = CadLayer("EDIFICACOES", 0xFFE040FB)

        // Base UTM reference: E: 334500, N: 7392100 (typical Brazilian UTM zone 23S)
        val baseX = 334500.0
        val baseY = 7392100.0

        // Boundary property line (Polygonal)
        val boundary = listOf(
            Pair(baseX + 0.0, baseY + 0.0),
            Pair(baseX + 180.0, baseY + 30.0),
            Pair(baseX + 240.0, baseY + 190.0),
            Pair(baseX + 110.0, baseY + 260.0),
            Pair(baseX + 15.0, baseY + 180.0),
            Pair(baseX + 0.0, baseY + 0.0)
        )
        entities.add(CadPolyline(boundary, isClosed = true, layer = "DIVISA_POLIGONAL", color = 0xFF00E676))

        // Vertices V1 to V5
        val vNames = listOf("M-01", "M-02", "M-03", "M-04", "M-05")
        for (i in 0 until 5) {
            val pt = boundary[i]
            entities.add(CadPoint(pt.first, pt.second, 745.0 + i * 2.5, "PONTOS_VERTICES", 0xFFFF1744, vNames[i]))
            entities.add(CadCircle(pt.first, pt.second, 2.5, "PONTOS_VERTICES", 0xFFFF1744))
            entities.add(CadText(pt.first + 3.0, pt.second + 3.0, "${vNames[i]} (Z=${745 + i * 2})", 2.2, 0.0, "TEXTOS_COTAS", 0xFF00E5FF))
        }

        // Contour lines (Curvas de Nível)
        val contour1 = listOf(
            Pair(baseX + 20.0, baseY + 40.0),
            Pair(baseX + 70.0, baseY + 60.0),
            Pair(baseX + 130.0, baseY + 75.0),
            Pair(baseX + 190.0, baseY + 80.0)
        )
        entities.add(CadPolyline(contour1, false, "CURVAS_NIVEL_MESTRAS", 0xFFFF8F00))
        entities.add(CadText(baseX + 100.0, baseY + 70.0, "745.00m", 1.8, 15.0, "CURVAS_NIVEL_MESTRAS", 0xFFFF8F00))

        val contour2 = listOf(
            Pair(baseX + 30.0, baseY + 90.0),
            Pair(baseX + 80.0, baseY + 115.0),
            Pair(baseX + 140.0, baseY + 130.0),
            Pair(baseX + 210.0, baseY + 135.0)
        )
        entities.add(CadPolyline(contour2, false, "CURVAS_NIVEL_INTERM", 0xFFFFB300))

        val contour3 = listOf(
            Pair(baseX + 35.0, baseY + 140.0),
            Pair(baseX + 85.0, baseY + 165.0),
            Pair(baseX + 130.0, baseY + 185.0),
            Pair(baseX + 175.0, baseY + 200.0)
        )
        entities.add(CadPolyline(contour3, false, "CURVAS_NIVEL_MESTRAS", 0xFFFF8F00))
        entities.add(CadText(baseX + 90.0, baseY + 170.0, "750.00m", 1.8, 20.0, "CURVAS_NIVEL_MESTRAS", 0xFFFF8F00))

        // Building footprint
        val house = listOf(
            Pair(baseX + 60.0, baseY + 100.0),
            Pair(baseX + 85.0, baseY + 100.0),
            Pair(baseX + 85.0, baseY + 120.0),
            Pair(baseX + 60.0, baseY + 120.0),
            Pair(baseX + 60.0, baseY + 100.0)
        )
        entities.add(CadPolyline(house, true, "EDIFICACOES", 0xFFE040FB))
        entities.add(CadText(baseX + 63.0, baseY + 108.0, "SEDE PRINCIPAL", 1.6, 0.0, "EDIFICACOES", 0xFFE040FB))

        val bounds = CadBounds.fromEntities(entities)
        return CadDrawing(
            title = "Exemplo_Topografia_Gleba.dxf",
            layers = layers,
            entities = entities,
            bounds = bounds,
            format = "DXF (Amostra)",
            description = "Gleba topográfica demonstrativa com curvas de nível, vértices e poligonal cadastral."
        )
    }
}
