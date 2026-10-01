package com.example.ui.cad

import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cad.*
import com.example.data.model.SurveyPoint
import com.example.data.model.SurveyProject
import com.example.data.model.TrackPoint
import com.example.util.CoordinateUtils
import java.util.Locale
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CadViewerScreen(
    currentProject: SurveyProject?,
    projectPoints: List<SurveyPoint>,
    projectTrajectory: List<TrackPoint>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Active CAD Drawing
    var drawing by remember {
        mutableStateOf(
            if (currentProject != null && (projectPoints.isNotEmpty() || projectTrajectory.isNotEmpty())) {
                DxfExporter.toCadDrawing(currentProject, projectPoints, projectTrajectory)
            } else {
                DxfParser.createSampleTopographyDrawing()
            }
        )
    }

    // Refresh when project points change if viewing current project
    LaunchedEffect(currentProject, projectPoints.size, projectTrajectory.size) {
        if (currentProject != null && (projectPoints.isNotEmpty() || projectTrajectory.isNotEmpty())) {
            drawing = DxfExporter.toCadDrawing(currentProject, projectPoints, projectTrajectory)
        }
    }

    // Layer visibility state
    val layerVisibility = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(drawing) {
        layerVisibility.clear()
        drawing.layers.keys.forEach { layerVisibility[it] = true }
    }

    // Extract all CAD points for the points inspector
    val cadPoints = remember(drawing) {
        drawing.entities.filterIsInstance<CadPoint>()
    }

    // CAD Theme: Dark (AutoCAD Model Space) or Light (Paper Space)
    var isDarkTheme by remember { mutableStateOf(true) }

    // Measurement Mode
    var isMeasuring by remember { mutableStateOf(false) }
    var measurePt1 by remember { mutableStateOf<Offset?>(null) }
    var measurePt2 by remember { mutableStateOf<Offset?>(null) }

    // Selected CAD Point
    var selectedCadPoint by remember { mutableStateOf<CadPoint?>(null) }

    // Crosshair inspection coordinates
    var inspectedUtmX by remember { mutableDoubleStateOf(0.0) }
    var inspectedUtmY by remember { mutableDoubleStateOf(0.0) }
    var hasInspectedPoint by remember { mutableStateOf(false) }

    // Transform State (Pan & Zoom)
    var scale by remember { mutableFloatStateOf(1.0f) }
    var panX by remember { mutableFloatStateOf(0.0f) }
    var panY by remember { mutableFloatStateOf(0.0f) }

    // Fit to view trigger
    var fitTrigger by remember { mutableIntStateOf(1) }

    // Dialogs
    var showLayersDialog by remember { mutableStateOf(false) }
    var showPointsListDialog by remember { mutableStateOf(false) }
    var showDwgInfoDialog by remember { mutableStateOf<DxfParser.DwgInfo?>(null) }

    // External File Picker for DXF / DWG
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                val cr = context.contentResolver
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Arquivo_CAD"
                val isDwg = name.endsWith(".dwg", ignoreCase = true)

                if (isDwg) {
                    val inputCheck = cr.openInputStream(uri)
                    val dwgInfo = inputCheck?.use { DxfParser.inspectDwg(it) }
                    showDwgInfoDialog = dwgInfo ?: DxfParser.DwgInfo("AC_DWG", "Formato AutoCAD DWG detectado")
                } else {
                    val inputStream = cr.openInputStream(uri)
                    if (inputStream != null) {
                        val parsed = inputStream.use { DxfParser.parseDxf(it, name) }
                        drawing = parsed
                        selectedCadPoint = null
                        fitTrigger++
                    }
                }
            } catch (_: Exception) {}
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = drawing.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "${cadPoints.size} pontos • ${drawing.entities.size} entidades CAD",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Points list inspector button
                    IconButton(
                        onClick = { showPointsListDialog = true },
                        modifier = Modifier.testTag("cad_points_list_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (cadPoints.isNotEmpty()) {
                                    Badge { Text("${cadPoints.size}") }
                                }
                            }
                        ) {
                            Icon(Icons.Default.PinDrop, contentDescription = "Ver Pontos CAD")
                        }
                    }
                    // Measure tool toggle
                    IconButton(
                        onClick = {
                            isMeasuring = !isMeasuring
                            measurePt1 = null
                            measurePt2 = null
                        },
                        modifier = Modifier.testTag("cad_measure_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Straighten,
                            contentDescription = "Medir Distância",
                            tint = if (isMeasuring) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // Layers toggle
                    IconButton(
                        onClick = { showLayersDialog = true },
                        modifier = Modifier.testTag("cad_layers_button")
                    ) {
                        Icon(Icons.Default.Layers, contentDescription = "Camadas CAD")
                    }
                    // Fit to View (Zoom Extents)
                    IconButton(
                        onClick = { fitTrigger++ },
                        modifier = Modifier.testTag("cad_zoom_extents_button")
                    ) {
                        Icon(Icons.Default.ZoomOutMap, contentDescription = "Ajustar à Tela")
                    }
                    // Theme toggle
                    IconButton(onClick = { isDarkTheme = !isDarkTheme }) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Alternar Tema CAD"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(if (isDarkTheme) Color(0xFF0F172A) else Color(0xFFF8FAFC))
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val boxWidth = constraints.maxWidth.toFloat().coerceAtLeast(100f)
                val boxHeight = constraints.maxHeight.toFloat().coerceAtLeast(100f)

                // Calculate Fit To View safely
                LaunchedEffect(fitTrigger, drawing) {
                    val b = drawing.bounds
                    val drawingW = b.width().toFloat()
                    val drawingH = b.height().toFloat()
                    if (drawingW > 0f && drawingH > 0f) {
                        val scaleX = (boxWidth * 0.82f) / drawingW
                        val scaleY = (boxHeight * 0.82f) / drawingH
                        val newScale = min(scaleX, scaleY).coerceIn(0.0001f, 1000.0f)
                        scale = newScale

                        // Center in canvas (CAD Y is inverted to match real-world North-up)
                        panX = (boxWidth / 2.0f) - (b.centerX().toFloat() * scale)
                        panY = (boxHeight / 2.0f) + (b.centerY().toFloat() * scale)
                    }
                }

                // Coordinate conversion helpers with finite safety checks
                fun screenToCad(screenX: Float, screenY: Float): Pair<Double, Double> {
                    val safeScale = if (scale.isFinite() && scale > 0f) scale else 1.0f
                    val cadX = (screenX - panX) / safeScale
                    val cadY = (panY - screenY) / safeScale
                    return Pair(cadX.toDouble(), cadY.toDouble())
                }

                fun cadToScreen(cadX: Double, cadY: Double): Offset {
                    val safeScale = if (scale.isFinite() && scale > 0f) scale else 1.0f
                    val sx = (panX + cadX.toFloat() * safeScale)
                    val sy = (panY - cadY.toFloat() * safeScale)
                    return Offset(sx, sy)
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val nextScale = (scale * zoom).coerceIn(0.00005f, 2000.0f)
                                if (nextScale.isFinite() && nextScale > 0f) {
                                    scale = nextScale
                                    panX += pan.x
                                    panY += pan.y
                                }
                            }
                        }
                        .pointerInput(isMeasuring, cadPoints) {
                            detectTapGestures { tapOffset ->
                                val (cx, cy) = screenToCad(tapOffset.x, tapOffset.y)
                                inspectedUtmX = cx
                                inspectedUtmY = cy
                                hasInspectedPoint = true

                                // Check if user tapped near any CAD point
                                var tappedPoint: CadPoint? = null
                                for (pt in cadPoints) {
                                    val screenPt = cadToScreen(pt.x, pt.y)
                                    val d = hypot((tapOffset.x - screenPt.x).toDouble(), (tapOffset.y - screenPt.y).toDouble())
                                    if (d < 38.0) {
                                        tappedPoint = pt
                                        break
                                    }
                                }
                                selectedCadPoint = tappedPoint

                                if (isMeasuring) {
                                    if (measurePt1 == null || measurePt2 != null) {
                                        measurePt1 = tapOffset
                                        measurePt2 = null
                                    } else {
                                        measurePt2 = tapOffset
                                    }
                                }
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height

                    // 1. Draw CAD Grid
                    val gridColor = if (isDarkTheme) Color(0x18FFFFFF) else Color(0x18000000)
                    val gridSpacing = 50f
                    var gx = panX % gridSpacing
                    while (gx < w) {
                        drawLine(gridColor, Offset(gx, 0f), Offset(gx, h), 1f)
                        gx += gridSpacing
                    }
                    var gy = panY % gridSpacing
                    while (gy < h) {
                        drawLine(gridColor, Offset(0f, gy), Offset(w, gy), 1f)
                        gy += gridSpacing
                    }

                    // 2. Render CAD Entities safely
                    for (entity in drawing.entities) {
                        if (layerVisibility[entity.layer] == false) continue

                        val entityColor = entity.composeColor

                        when (entity) {
                            is CadPoint -> {
                                val ptScreen = cadToScreen(entity.x, entity.y)
                                if (ptScreen.x.isFinite() && ptScreen.y.isFinite()) {
                                    val isSelected = selectedCadPoint == entity

                                    if (isSelected) {
                                        drawCircle(Color(0xFFFFEB3B), radius = 14f, center = ptScreen)
                                    }

                                    // Point crosshair
                                    val arm = 8f
                                    drawLine(entityColor, Offset(ptScreen.x - arm, ptScreen.y), Offset(ptScreen.x + arm, ptScreen.y), 2f)
                                    drawLine(entityColor, Offset(ptScreen.x, ptScreen.y - arm), Offset(ptScreen.x, ptScreen.y + arm), 2f)

                                    // Center circle
                                    drawCircle(entityColor, radius = 5f, center = ptScreen)
                                    drawCircle(Color.White, radius = 2f, center = ptScreen)

                                    // Point label if available
                                    if (entity.label.isNotBlank()) {
                                        drawIntoCanvas { canvas ->
                                            val pPaint = Paint().apply {
                                                color = if (isDarkTheme) android.graphics.Color.WHITE else android.graphics.Color.BLACK
                                                textSize = 28f
                                                isAntiAlias = true
                                                isFakeBoldText = true
                                            }
                                            canvas.nativeCanvas.drawText(entity.label, ptScreen.x + 10f, ptScreen.y - 8f, pPaint)
                                        }
                                    }
                                }
                            }
                            is CadLine -> {
                                val p1 = cadToScreen(entity.startX, entity.startY)
                                val p2 = cadToScreen(entity.endX, entity.endY)
                                if (p1.x.isFinite() && p1.y.isFinite() && p2.x.isFinite() && p2.y.isFinite()) {
                                    drawLine(entityColor, p1, p2, strokeWidth = 2f)
                                }
                            }
                            is CadPolyline -> {
                                if (entity.vertices.size >= 2) {
                                    val path = Path()
                                    val first = cadToScreen(entity.vertices.first().first, entity.vertices.first().second)
                                    if (first.x.isFinite() && first.y.isFinite()) {
                                        path.moveTo(first.x, first.y)
                                        for (i in 1 until entity.vertices.size) {
                                            val p = cadToScreen(entity.vertices[i].first, entity.vertices[i].second)
                                            if (p.x.isFinite() && p.y.isFinite()) {
                                                path.lineTo(p.x, p.y)
                                            }
                                        }
                                        if (entity.isClosed) {
                                            path.close()
                                        }
                                        drawPath(path, entityColor, style = Stroke(width = 2.4f))
                                    }
                                }
                            }
                            is CadCircle -> {
                                val center = cadToScreen(entity.centerX, entity.centerY)
                                val rPx = (entity.radius.toFloat() * scale).coerceAtLeast(1f)
                                if (center.x.isFinite() && center.y.isFinite() && rPx.isFinite()) {
                                    drawCircle(entityColor, radius = rPx, center = center, style = Stroke(1.8f))
                                }
                            }
                            is CadArc -> {
                                val center = cadToScreen(entity.centerX, entity.centerY)
                                val rPx = (entity.radius.toFloat() * scale).coerceAtLeast(1f)
                                if (center.x.isFinite() && center.y.isFinite() && rPx.isFinite()) {
                                    val sweep = if (entity.endAngle >= entity.startAngle) {
                                        (entity.endAngle - entity.startAngle).toFloat()
                                    } else {
                                        (360.0 - entity.startAngle + entity.endAngle).toFloat()
                                    }
                                    drawArc(
                                        color = entityColor,
                                        startAngle = -entity.endAngle.toFloat(),
                                        sweepAngle = sweep,
                                        useCenter = false,
                                        topLeft = Offset(center.x - rPx, center.y - rPx),
                                        size = Size(rPx * 2f, rPx * 2f),
                                        style = Stroke(1.8f)
                                    )
                                }
                            }
                            is CadText -> {
                                val pos = cadToScreen(entity.x, entity.y)
                                val textSz = (entity.height.toFloat() * scale).coerceIn(10f, 60f)
                                if (pos.x.isFinite() && pos.y.isFinite()) {
                                    drawIntoCanvas { canvas ->
                                        val paint = Paint().apply {
                                            color = if (isDarkTheme) android.graphics.Color.WHITE else android.graphics.Color.BLACK
                                            textSize = textSz
                                            isAntiAlias = true
                                        }
                                        canvas.nativeCanvas.drawText(entity.text, pos.x, pos.y, paint)
                                    }
                                }
                            }
                        }
                    }

                    // 3. Draw Measurement line & label
                    val p1 = measurePt1
                    val p2 = measurePt2
                    if (p1 != null && p2 != null) {
                        drawLine(Color(0xFFFFEB3B), p1, p2, strokeWidth = 3f)
                        drawCircle(Color(0xFFFFEB3B), 6f, p1)
                        drawCircle(Color(0xFFFFEB3B), 6f, p2)

                        val (cad1X, cad1Y) = screenToCad(p1.x, p1.y)
                        val (cad2X, cad2Y) = screenToCad(p2.x, p2.y)
                        val dist = CoordinateUtils.distance(cad1X, cad1Y, cad2X, cad2Y)
                        val az = CoordinateUtils.azimuth(cad1X, cad1Y, cad2X, cad2Y)
                        val mid = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)

                        drawIntoCanvas { canvas ->
                            val mPaint = Paint().apply {
                                color = android.graphics.Color.YELLOW
                                textSize = 34f
                                isAntiAlias = true
                                isFakeBoldText = true
                                setShadowLayer(4f, 1f, 1f, android.graphics.Color.BLACK)
                            }
                            canvas.nativeCanvas.drawText(
                                String.format(Locale.US, "%.2f m (Az %.1f°)", dist, az),
                                mid.x + 10f,
                                mid.y - 10f,
                                mPaint
                            )
                        }
                    } else if (p1 != null) {
                        drawCircle(Color(0xFFFFEB3B), 6f, p1)
                    }
                }
            }

            // Empty project helper banner
            if (drawing.entities.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Architecture,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Nenhum Ponto Registrado",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Cadastre pontos sequenciais com GPS na aba 'Mapa & GPS' ou abra um arquivo CAD.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(onClick = {
                            drawing = DxfParser.createSampleTopographyDrawing()
                            fitTrigger++
                        }) {
                            Text("Carregar Amostra Topográfica (DXF)")
                        }
                    }
                }
            }

            // Bottom Floating HUD Overlay (Point Details or Cursor Coordinates)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
            ) {
                // If a CAD Point is selected, show its full card
                selectedCadPoint?.let { pt ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        shape = RoundedCornerShape(12.dp),
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(pt.composeColor)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = pt.label.takeIf { it.isNotBlank() } ?: "Ponto CAD",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Camada: ${pt.layer}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "E: ${String.format(Locale.US, "%.3f m", pt.x)}  •  N: ${String.format(Locale.US, "%.3f m", pt.y)}  •  Z: ${String.format(Locale.US, "%.2f m", pt.z)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            IconButton(onClick = { selectedCadPoint = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Fechar")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Cursor Crosshair Coordinates
                if (hasInspectedPoint && selectedCadPoint == null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(10.dp),
                        shadowElevation = 6.dp
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(
                                text = "Cursor CAD (UTM)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "E (X): ${String.format(Locale.US, "%.3f m", inspectedUtmX)}  •  N (Y): ${String.format(Locale.US, "%.3f m", inspectedUtmY)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Bottom Action buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        modifier = Modifier.testTag("cad_open_file_button")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Abrir DXF/DWG")
                    }

                    if (currentProject != null) {
                        FilledTonalButton(
                            onClick = {
                                drawing = DxfExporter.toCadDrawing(currentProject, projectPoints, projectTrajectory)
                                selectedCadPoint = null
                                fitTrigger++
                            },
                            modifier = Modifier.testTag("cad_view_current_project_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Projeto Atual")
                        }
                    }
                }
            }
        }
    }

    // Points List Dialog (Visualizador de Todos os Pontos do Desenho CAD)
    if (showPointsListDialog) {
        AlertDialog(
            onDismissRequest = { showPointsListDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PinDrop, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pontos CAD (${cadPoints.size})")
                }
            },
            text = {
                if (cadPoints.isEmpty()) {
                    Text("Nenhum ponto 'POINT' encontrado neste desenho CAD.")
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(cadPoints) { pt ->
                            Card(
                                onClick = {
                                    selectedCadPoint = pt
                                    showPointsListDialog = false
                                    // Center on this point
                                    panX = 0f // reset and pan to point
                                    fitTrigger++
                                },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(pt.composeColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = pt.label.take(3).ifEmpty { "PT" },
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = pt.label.ifBlank { "Ponto (${pt.layer})" },
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "E: ${String.format(Locale.US, "%.3f", pt.x)}  N: ${String.format(Locale.US, "%.3f", pt.y)}  Z: ${String.format(Locale.US, "%.2f", pt.z)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPointsListDialog = false }) {
                    Text("Fechar")
                }
            }
        )
    }

    // Layers Manager Dialog
    if (showLayersDialog) {
        AlertDialog(
            onDismissRequest = { showLayersDialog = false },
            title = { Text("Camadas CAD (${drawing.layers.size})") },
            text = {
                Column {
                    drawing.layers.values.forEach { layer ->
                        val isVisible = layerVisibility[layer.name] ?: true
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(layer.composeColor, RoundedCornerShape(4.dp))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = layer.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Switch(
                                checked = isVisible,
                                onCheckedChange = { layerVisibility[layer.name] = it }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLayersDialog = false }) {
                    Text("Concluir")
                }
            }
        )
    }

    // DWG Information Dialog
    showDwgInfoDialog?.let { dwg ->
        AlertDialog(
            onDismissRequest = { showDwgInfoDialog = null },
            icon = { Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Arquivo DWG Detectado") },
            text = {
                Column {
                    Text(
                        text = "Versão: ${dwg.versionDescription} (${dwg.headerCode})",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "O formato DWG é binário proprietário da Autodesk. Para visualização geométrica completa com edição e conversão de coordenadas UTM, exporte seu desenho como DXF (Drawing Exchange Format) no AutoCAD, Civil 3D ou QGIS.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "O TopoUTM GPS renderiza DXF nativamente com todas as camadas, cotas e coordenadas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showDwgInfoDialog = null
                    drawing = DxfParser.createSampleTopographyDrawing()
                    fitTrigger++
                }) {
                    Text("Ver Exemplo DXF")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDwgInfoDialog = null }) {
                    Text("Fechar")
                }
            }
        )
    }
}
