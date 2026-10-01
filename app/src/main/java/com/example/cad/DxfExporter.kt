package com.example.cad

import com.example.data.model.SurveyPoint
import com.example.data.model.SurveyProject
import com.example.data.model.TrackPoint
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DxfExporter {

    /**
     * Generates a fully compliant AutoCAD R12/2000 ASCII DXF file
     */
    fun generateDxfContent(
        project: SurveyProject,
        points: List<SurveyPoint>,
        trackPoints: List<TrackPoint> = emptyList(),
        includeElevation: Boolean = true,
        includeLabels: Boolean = true,
        connectSequentialPoints: Boolean = true,
        includeTrajectory: Boolean = true
    ): String {
        val sb = StringBuilder()

        // 1. HEADER SECTION
        sb.append("0\nSECTION\n")
        sb.append("2\nHEADER\n")
        sb.append("9\n\$ACADVER\n1\nAC1009\n") // AutoCAD Release 11/12 (broadest compatibility)
        sb.append("9\n\$INSUNITS\n70\n6\n")   // 6 = Meters
        sb.append("9\n\$MEASUREMENT\n70\n1\n") // 1 = Metric
        sb.append("0\nENDSEC\n")

        // 2. TABLES SECTION (LAYERS)
        sb.append("0\nSECTION\n")
        sb.append("2\nTABLES\n")
        sb.append("0\nTABLE\n")
        sb.append("2\nLAYER\n")
        sb.append("70\n6\n") // Number of layers

        // Layers definition (Color 1=Red, 2=Yellow, 3=Green, 4=Cyan, 5=Blue, 6=Magenta, 7=White)
        appendLayer(sb, "0", 7)
        appendLayer(sb, "PONTOS_UTM", 1)         // Red
        appendLayer(sb, "NOMES_PONTOS", 3)       // Green
        appendLayer(sb, "COTAS_ALTITUDE", 4)     // Cyan
        appendLayer(sb, "DESCRICAO_CODIGO", 7)   // White
        appendLayer(sb, "POLIGONAL_SEQUENCIAL", 2) // Yellow
        appendLayer(sb, "TRAJETORIA_GPS", 6)     // Magenta

        sb.append("0\nENDTAB\n")
        sb.append("0\nENDSEC\n")

        // 3. BLOCKS SECTION (empty placeholder)
        sb.append("0\nSECTION\n")
        sb.append("2\nBLOCKS\n")
        sb.append("0\nENDSEC\n")

        // 4. ENTITIES SECTION
        sb.append("0\nSECTION\n")
        sb.append("2\nENTITIES\n")

        // Add Points and Point Text Annotations
        for (pt in points) {
            val x = String.format(Locale.US, "%.4f", pt.easting)
            val y = String.format(Locale.US, "%.4f", pt.northing)
            val z = String.format(Locale.US, "%.4f", pt.altitude)

            // CAD POINT entity
            sb.append("0\nPOINT\n")
            sb.append("8\nPONTOS_UTM\n")
            sb.append("62\n1\n") // Color Red
            sb.append("10\n$x\n20\n$y\n30\n$z\n")

            if (includeLabels) {
                // TEXT for Point Name
                sb.append("0\nTEXT\n")
                sb.append("8\nNOMES_PONTOS\n")
                sb.append("62\n3\n")
                val textX = String.format(Locale.US, "%.4f", pt.easting + 1.2)
                val textY = String.format(Locale.US, "%.4f", pt.northing + 1.2)
                sb.append("10\n$textX\n20\n$textY\n30\n$z\n")
                sb.append("40\n1.8\n") // Text height
                sb.append("1\n${pt.pointName}\n")

                if (includeElevation) {
                    // TEXT for Elevation (Z)
                    sb.append("0\nTEXT\n")
                    sb.append("8\nCOTAS_ALTITUDE\n")
                    sb.append("62\n4\n")
                    val zTextY = String.format(Locale.US, "%.4f", pt.northing - 1.0)
                    sb.append("10\n$textX\n20\n$zTextY\n30\n$z\n")
                    sb.append("40\n1.4\n")
                    sb.append("1\nZ=${String.format(Locale.US, "%.2f", pt.altitude)}\n")
                }

                if (pt.code.isNotBlank()) {
                    // TEXT for Code
                    sb.append("0\nTEXT\n")
                    sb.append("8\nDESCRICAO_CODIGO\n")
                    sb.append("62\n7\n")
                    val codeY = String.format(Locale.US, "%.4f", pt.northing - 2.8)
                    sb.append("10\n$textX\n20\n$codeY\n30\n$z\n")
                    sb.append("40\n1.2\n")
                    sb.append("1\n${pt.code}\n")
                }
            }
        }

        // Connect sequential points with a Polyline
        if (connectSequentialPoints && points.size >= 2) {
            sb.append("0\nPOLYLINE\n")
            sb.append("8\nPOLIGONAL_SEQUENCIAL\n")
            sb.append("62\n2\n") // Yellow
            sb.append("66\n1\n") // Vertices follow flag
            sb.append("70\n0\n") // 0 = Open polyline (1 = Closed)

            for (pt in points) {
                val x = String.format(Locale.US, "%.4f", pt.easting)
                val y = String.format(Locale.US, "%.4f", pt.northing)
                val z = String.format(Locale.US, "%.4f", pt.altitude)
                sb.append("0\nVERTEX\n")
                sb.append("8\nPOLIGONAL_SEQUENCIAL\n")
                sb.append("10\n$x\n20\n$y\n30\n$z\n")
            }
            sb.append("0\nSEQEND\n")
        }

        // Trajectory polyline
        if (includeTrajectory && trackPoints.size >= 2) {
            sb.append("0\nPOLYLINE\n")
            sb.append("8\nTRAJETORIA_GPS\n")
            sb.append("62\n6\n") // Magenta
            sb.append("66\n1\n")
            sb.append("70\n0\n")

            for (tp in trackPoints) {
                val x = String.format(Locale.US, "%.4f", tp.easting)
                val y = String.format(Locale.US, "%.4f", tp.northing)
                val z = String.format(Locale.US, "%.4f", tp.altitude)
                sb.append("0\nVERTEX\n")
                sb.append("8\nTRAJETORIA_GPS\n")
                sb.append("10\n$x\n20\n$y\n30\n$z\n")
            }
            sb.append("0\nSEQEND\n")
        }

        sb.append("0\nENDSEC\n")
        sb.append("0\nEOF\n")

        return sb.toString()
    }

    private fun appendLayer(sb: StringBuilder, name: String, colorAci: Int) {
        sb.append("0\nLAYER\n")
        sb.append("2\n$name\n")
        sb.append("70\n0\n")
        sb.append("62\n$colorAci\n")
        sb.append("6\nCONTINUOUS\n")
    }

    /**
     * Generates a Topographic CSV file (Ponto, Este, Norte, Cota, Código, Lat, Lon)
     */
    fun generateCsv(points: List<SurveyPoint>): String {
        val sb = StringBuilder()
        sb.append("PONTO;ESTE_X_M;NORTE_Y_M;COTA_Z_M;CODIGO;ZONA_UTM;LATITUDE;LONGITUDE;PRECISAO_M;DATA_HORA;NOTAS\n")
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        for (pt in points) {
            val dateStr = sdf.format(Date(pt.timestamp))
            sb.append("${pt.pointName};")
            sb.append(String.format(Locale.US, "%.3f;", pt.easting))
            sb.append(String.format(Locale.US, "%.3f;", pt.northing))
            sb.append(String.format(Locale.US, "%.3f;", pt.altitude))
            sb.append("${pt.code};")
            sb.append("${pt.utmZone}${pt.hemisphere};")
            sb.append(String.format(Locale.US, "%.7f;", pt.latitude))
            sb.append(String.format(Locale.US, "%.7f;", pt.longitude))
            sb.append(String.format(Locale.US, "%.2f;", pt.accuracy))
            sb.append("$dateStr;")
            sb.append("${pt.notes.replace(";", ",")}\n")
        }
        return sb.toString()
    }

    /**
     * Generates a Topographic TXT report and PENZD format for total stations / CAD
     */
    fun generateTxtReport(project: SurveyProject, points: List<SurveyPoint>): String {
        val sb = StringBuilder()
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

        sb.append("================================================================================\n")
        sb.append("               RELATÓRIO TOPOGRÁFICO DE PONTOS UTM E GEODÉSICAS\n")
        sb.append("================================================================================\n")
        sb.append("Projeto: ${project.name}\n")
        if (project.description.isNotBlank()) {
            sb.append("Descrição: ${project.description}\n")
        }
        sb.append("Datum Geodésico: ${project.datum}\n")
        sb.append("Fuso UTM: ${project.utmZone}${project.hemisphere}\n")
        sb.append("Data de Emissão: ${sdf.format(Date())}\n")
        sb.append("Quantidade de Pontos: ${points.size}\n")

        val polygonPoints = points.map { Pair(it.easting, it.northing) }
        val areaM2 = com.example.util.CoordinateUtils.calculatePolygonArea(polygonPoints)
        val perimeterM = com.example.util.CoordinateUtils.calculatePolygonPerimeter(polygonPoints)
        if (points.size >= 3) {
            sb.append("Área Estimada: ${com.example.util.CoordinateUtils.formatArea(areaM2)}\n")
            sb.append("Perímetro Estimado: ${String.format(Locale.US, "%.2f m", perimeterM)}\n")
        }
        sb.append("--------------------------------------------------------------------------------\n")
        sb.append(String.format("%-8s | %-13s | %-13s | %-10s | %-12s | %-10s\n", "PONTO", "ESTE X (m)", "NORTE Y (m)", "COTA Z (m)", "CÓDIGO", "PRECISÃO"))
        sb.append("--------------------------------------------------------------------------------\n")

        for (pt in points) {
            sb.append(
                String.format(
                    Locale.US,
                    "%-8s | %13.3f | %13.3f | %10.3f | %-12s | ±%-8.2fm\n",
                    pt.pointName,
                    pt.easting,
                    pt.northing,
                    pt.altitude,
                    pt.code,
                    pt.accuracy
                )
            )
        }

        sb.append("--------------------------------------------------------------------------------\n")
        sb.append("COORDENADAS GEODÉSICAS (LATITUDE / LONGITUDE):\n")
        sb.append("--------------------------------------------------------------------------------\n")
        for (pt in points) {
            val latLon = com.example.util.LatLon(pt.latitude, pt.longitude)
            sb.append("${pt.pointName}: Lat ${latLon.toDmsLatitude()} (${String.format(Locale.US, "%.7f°", pt.latitude)}), Lon ${latLon.toDmsLongitude()} (${String.format(Locale.US, "%.7f°", pt.longitude)})\n")
        }

        sb.append("\n--------------------------------------------------------------------------------\n")
        sb.append("FORMATO PENZD (PONTO,ESTE,NORTE,COTA,DESCRICAO):\n")
        sb.append("Compatível com AutoCAD Civil 3D, QGIS, Estação Total e GPS RTK:\n")
        sb.append("--------------------------------------------------------------------------------\n")
        for (pt in points) {
            sb.append("${pt.pointName},${String.format(Locale.US, "%.3f", pt.easting)},${String.format(Locale.US, "%.3f", pt.northing)},${String.format(Locale.US, "%.3f", pt.altitude)},${pt.code}\n")
        }
        sb.append("================================================================================\n")

        return sb.toString()
    }

    /**
     * Converts current survey project to a CadDrawing for direct visualization
     */
    fun toCadDrawing(project: SurveyProject, points: List<SurveyPoint>, trackPoints: List<TrackPoint>): CadDrawing {
        val entities = mutableListOf<CadEntity>()
        val layers = mutableMapOf<String, CadLayer>()

        layers["PONTOS_UTM"] = CadLayer("PONTOS_UTM", 0xFFFF5252)
        layers["NOMES_PONTOS"] = CadLayer("NOMES_PONTOS", 0xFF69F0AE)
        layers["COTAS_ALTITUDE"] = CadLayer("COTAS_ALTITUDE", 0xFF40C4FF)
        layers["POLIGONAL_SEQUENCIAL"] = CadLayer("POLIGONAL_SEQUENCIAL", 0xFFFFD740)
        layers["TRAJETORIA_GPS"] = CadLayer("TRAJETORIA_GPS", 0xFFE040FB)

        // Points
        for (pt in points) {
            entities.add(
                CadPoint(
                    x = pt.easting,
                    y = pt.northing,
                    z = pt.altitude,
                    layer = "PONTOS_UTM",
                    color = 0xFFFF5252,
                    label = pt.pointName
                )
            )
            entities.add(
                CadText(
                    x = pt.easting + 1.2,
                    y = pt.northing + 1.2,
                    text = pt.pointName,
                    height = 2.0,
                    layer = "NOMES_PONTOS",
                    color = 0xFF69F0AE
                )
            )
            entities.add(
                CadText(
                    x = pt.easting + 1.2,
                    y = pt.northing - 1.2,
                    text = String.format(Locale.US, "Z=%.2f", pt.altitude),
                    height = 1.4,
                    layer = "COTAS_ALTITUDE",
                    color = 0xFF40C4FF
                )
            )
        }

        // Sequential polyline
        if (points.size >= 2) {
            val vertices = points.map { Pair(it.easting, it.northing) }
            entities.add(
                CadPolyline(
                    vertices = vertices,
                    isClosed = false,
                    layer = "POLIGONAL_SEQUENCIAL",
                    color = 0xFFFFD740
                )
            )
        }

        // Trajectory
        if (trackPoints.size >= 2) {
            val trackVertices = trackPoints.map { Pair(it.easting, it.northing) }
            entities.add(
                CadPolyline(
                    vertices = trackVertices,
                    isClosed = false,
                    layer = "TRAJETORIA_GPS",
                    color = 0xFFE040FB
                )
            )
        }

        val bounds = CadBounds.fromEntities(entities)
        return CadDrawing(
            title = project.name,
            layers = layers,
            entities = entities,
            bounds = bounds,
            format = "DXF Topo",
            description = "${points.size} pontos, ${trackPoints.size} nós de rastro. Fuso ${project.utmZone}${project.hemisphere}"
        )
    }
}
