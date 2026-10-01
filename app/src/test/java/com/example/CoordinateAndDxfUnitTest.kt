package com.example

import com.example.cad.DxfExporter
import com.example.cad.DxfParser
import com.example.data.model.SurveyPoint
import com.example.data.model.SurveyProject
import com.example.data.model.TrackPoint
import com.example.util.CoordinateUtils
import com.example.util.UtmCoordinate
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class CoordinateAndDxfUnitTest {

    @Test
    fun testUtmConversionAndReversibility() {
        // Test São Paulo / Brazil coordinate (Zone 23S)
        val lat = -23.550520
        val lon = -46.633308

        val zone = CoordinateUtils.calculateZone(lon)
        assertEquals(23, zone)

        val utm = CoordinateUtils.toUtm(lat, lon)
        assertEquals(23, utm.zone)
        assertEquals('S', utm.hemisphere)
        assertTrue("Easting should be approx 333470m", utm.easting in 330000.0..340000.0)
        assertTrue("Northing should be approx 7394780m", utm.northing in 7380000.0..7410000.0)

        // Convert back to Lat/Lon
        val latLon = CoordinateUtils.toLatLon(utm)
        assertEquals(lat, latLon.latitude, 0.0001)
        assertEquals(lon, latLon.longitude, 0.0001)
    }

    @Test
    fun testPolygonAreaAndDistance() {
        // 100m x 100m square
        val square = listOf(
            Pair(500000.0, 7000000.0),
            Pair(500100.0, 7000000.0),
            Pair(500100.0, 7000100.0),
            Pair(500000.0, 7000100.0)
        )

        val area = CoordinateUtils.calculatePolygonArea(square)
        assertEquals(10000.0, area, 0.01) // 100x100 = 10,000 m² = 1.0 ha

        val perimeter = CoordinateUtils.calculatePolygonPerimeter(square)
        assertEquals(400.0, perimeter, 0.01)

        val dist = CoordinateUtils.distance(500000.0, 7000000.0, 500030.0, 7000040.0)
        assertEquals(50.0, dist, 0.01) // 3-4-5 triangle
    }

    @Test
    fun testDxfGenerationAndParsing() {
        val project = SurveyProject(
            id = 1,
            name = "Projeto Teste",
            utmZone = 23,
            hemisphere = 'S'
        )

        val points = listOf(
            SurveyPoint(
                id = 1,
                projectId = 1,
                sequenceNumber = 1,
                pointName = "P001",
                code = "Marco",
                latitude = -23.55,
                longitude = -46.63,
                altitude = 750.0,
                accuracy = 1.2f,
                utmZone = 23,
                hemisphere = 'S',
                easting = 333000.0,
                northing = 7394000.0
            ),
            SurveyPoint(
                id = 2,
                projectId = 1,
                sequenceNumber = 2,
                pointName = "P002",
                code = "Vértice",
                latitude = -23.551,
                longitude = -46.631,
                altitude = 752.5,
                accuracy = 1.0f,
                utmZone = 23,
                hemisphere = 'S',
                easting = 333100.0,
                northing = 7394100.0
            )
        )

        val track = listOf(
            TrackPoint(
                id = 1,
                projectId = 1,
                latitude = -23.55,
                longitude = -46.63,
                altitude = 750.0,
                easting = 333000.0,
                northing = 7394000.0
            ),
            TrackPoint(
                id = 2,
                projectId = 1,
                latitude = -23.5505,
                longitude = -46.6305,
                altitude = 751.0,
                easting = 333050.0,
                northing = 7394050.0
            )
        )

        val dxf = DxfExporter.generateDxfContent(project, points, track)
        assertTrue(dxf.contains("SECTION"))
        assertTrue(dxf.contains("ENTITIES"))
        assertTrue(dxf.contains("PONTOS_UTM"))
        assertTrue(dxf.contains("POLIGONAL_SEQUENCIAL"))
        assertTrue(dxf.contains("TRAJETORIA_GPS"))
        assertTrue(dxf.contains("EOF"))

        // Test Parsing
        val parsed = DxfParser.parseDxf(ByteArrayInputStream(dxf.toByteArray(Charsets.UTF_8)), "teste.dxf")
        assertTrue("Entities should be parsed", parsed.entities.isNotEmpty())
        assertTrue("Layers should include PONTOS_UTM", parsed.layers.containsKey("PONTOS_UTM"))

        // Test TXT Report Generation
        val txt = DxfExporter.generateTxtReport(project, points)
        assertTrue(txt.contains("RELATÓRIO TOPOGRÁFICO"))
        assertTrue(txt.contains("Projeto Teste"))
        assertTrue(txt.contains("P001"))
        assertTrue(txt.contains("333000.000"))
        assertTrue(txt.contains("FORMATO PENZD"))
        assertTrue(txt.contains("P001,333000.000,7394000.000,750.000,Marco"))
    }
}
