package com.example.ui.map

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SurveyPoint
import com.example.data.model.SurveyProject
import com.example.data.model.TrackPoint
import com.example.location.AveragingSession
import com.example.location.GpsStatus
import com.example.util.CoordinateUtils
import com.example.util.LatLon
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapGpsScreen(
    currentProject: SurveyProject?,
    points: List<SurveyPoint>,
    trajectory: List<TrackPoint>,
    gpsStatus: GpsStatus,
    averagingSession: AveragingSession,
    onAddPoint: (name: String, code: String, notes: String) -> Unit,
    onStartAveraging: (samples: Int) -> Unit,
    onCancelAveraging: () -> Unit,
    onSaveAveragedPoint: (name: String, code: String, notes: String) -> Unit,
    onToggleTrajectory: (Boolean) -> Unit,
    onClearTrajectory: () -> Unit,
    onExportTxt: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var mapSource by remember { mutableStateOf(MapTileSource.SATELLITE) }
    var centerGpsTrigger by remember { mutableIntStateOf(1) }
    var zoomInTrigger by remember { mutableIntStateOf(0) }
    var zoomOutTrigger by remember { mutableIntStateOf(0) }
    var selectedPoint by remember { mutableStateOf<SurveyPoint?>(null) }
    var showQuickPointSheet by remember { mutableStateOf(false) }
    var showAveragingDialog by remember { mutableStateOf(false) }
    var showClearTrackDialog by remember { mutableStateOf(false) }

    // Quick point fields
    val nextSeq = (points.maxOfOrNull { it.sequenceNumber } ?: 0) + 1
    var pointName by remember(nextSeq) { mutableStateOf(String.format(Locale.US, "P%03d", nextSeq)) }
    var pointCode by remember { mutableStateOf("Vértice") }
    var pointNotes by remember { mutableStateOf("") }

    val commonCodes = listOf("Vértice", "Marco", "Cerca", "Eixo", "Talude", "Poste", "Edificação", "Árvore")

    // Location permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            centerGpsTrigger++
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Integrated Satellite Map View
        SatelliteMapView(
            modifier = Modifier.fillMaxSize(),
            gpsStatus = gpsStatus,
            points = points,
            trajectory = trajectory,
            mapSource = mapSource,
            centerOnGpsTrigger = centerGpsTrigger,
            zoomInTrigger = zoomInTrigger,
            zoomOutTrigger = zoomOutTrigger,
            selectedPoint = selectedPoint,
            onPointClick = { selectedPoint = it }
        )

        // 2. Real-time Telemetry HUD Bar (Top)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .align(Alignment.TopCenter),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = currentProject?.name ?: "Projeto Topográfico",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Fuso: ${gpsStatus.utm.shortZoneString()} • SIRGAS 2000",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Accuracy Quality Badge
                    Surface(
                        color = when {
                            gpsStatus.accuracy in 0.1f..3.0f -> Color(0xFF10B981)
                            gpsStatus.accuracy in 3.0f..6.0f -> Color(0xFFF59E0B)
                            else -> Color(0xFFEF4444)
                        },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = gpsStatus.accuracyQuality,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(8.dp))

                // UTM Coordinate values display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("ESTE (X)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (gpsStatus.hasFix) String.format(Locale.US, "%.3f m", gpsStatus.utm.easting) else "--",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column {
                        Text("NORTE (Y)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (gpsStatus.hasFix) String.format(Locale.US, "%.3f m", gpsStatus.utm.northing) else "--",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column {
                        Text("COTA (Z)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (gpsStatus.hasFix) String.format(Locale.US, "%.2f m", gpsStatus.altitude) else "--",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Trajectory tracking live telemetry
                if (gpsStatus.isTrackingActive || trajectory.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (gpsStatus.isTrackingActive) Color(0xFF00E5FF) else Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (gpsStatus.isTrackingActive) "Gravando Trajetória" else "Trajetória Pausada",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (gpsStatus.isTrackingActive) Color(0xFF00E5FF) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Dist: ${String.format(Locale.US, "%.1f m", gpsStatus.totalTrackDistance)} • Nós: ${trajectory.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 3. Floating Map Controls (Right Side)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Zoom In (+) Button
            FloatingActionButton(
                onClick = { zoomInTrigger++ },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(46.dp)
                    .testTag("map_zoom_in_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Aumentar Zoom (+)")
            }

            // Zoom Out (-) Button
            FloatingActionButton(
                onClick = { zoomOutTrigger++ },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(46.dp)
                    .testTag("map_zoom_out_button")
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Diminuir Zoom (-)")
            }

            // Export Points Text Format (TXT) Button
            FloatingActionButton(
                onClick = onExportTxt,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .size(46.dp)
                    .testTag("map_export_txt_button")
            ) {
                Icon(Icons.Default.Description, contentDescription = "Exportar Pontos TXT")
            }

            // Recenter on GPS
            FloatingActionButton(
                onClick = { centerGpsTrigger++ },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(46.dp)
                    .testTag("center_gps_button")
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Centralizar no GPS")
            }

            // Layer Switcher
            FloatingActionButton(
                onClick = {
                    mapSource = when (mapSource) {
                        MapTileSource.SATELLITE -> MapTileSource.STREET
                        MapTileSource.STREET -> MapTileSource.TOPO
                        MapTileSource.TOPO -> MapTileSource.SATELLITE
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(46.dp)
                    .testTag("map_layer_button")
            ) {
                Icon(
                    imageVector = when (mapSource) {
                        MapTileSource.SATELLITE -> Icons.Default.Satellite
                        MapTileSource.STREET -> Icons.Default.Map
                        MapTileSource.TOPO -> Icons.Default.Terrain
                    },
                    contentDescription = "Alternar Camada: ${mapSource.title}"
                )
            }

            // Trajectory recording toggle button
            FloatingActionButton(
                onClick = { onToggleTrajectory(!gpsStatus.isTrackingActive) },
                containerColor = if (gpsStatus.isTrackingActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (gpsStatus.isTrackingActive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .size(46.dp)
                    .testTag("toggle_trajectory_button")
            ) {
                Icon(
                    imageVector = if (gpsStatus.isTrackingActive) Icons.Default.Pause else Icons.Default.Timeline,
                    contentDescription = if (gpsStatus.isTrackingActive) "Pausar Trajetória" else "Gravar Trajetória"
                )
            }

            // Clear Trajectory
            if (trajectory.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { showClearTrackDialog = true },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(46.dp)
                ) {
                    Icon(Icons.Default.LayersClear, contentDescription = "Limpar Trajetória")
                }
            }
        }

        // 4. Primary Surveying Bottom Action Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            shape = RoundedCornerShape(20.dp),
            shadowElevation = 10.dp
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Quick Point GPS Register Button
                    Button(
                        onClick = { showQuickPointSheet = true },
                        modifier = Modifier
                            .weight(1.3f)
                            .height(52.dp)
                            .testTag("register_point_gps_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.AddLocation, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Cadastrar Ponto (GPS)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    // Topography Averaging Button
                    FilledTonalButton(
                        onClick = {
                            onStartAveraging(15)
                            showAveragingDialog = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("point_averaging_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ShutterSpeed, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Média GPS", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    // Quick Point Registration Bottom Sheet / Dialog
    if (showQuickPointSheet) {
        AlertDialog(
            onDismissRequest = { showQuickPointSheet = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AddLocation, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cadastrar Ponto Sequencial")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = pointName,
                        onValueChange = { pointName = it },
                        label = { Text("Nome do Ponto") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("point_name_input")
                    )

                    Text("Código / Tipo:", style = MaterialTheme.typography.labelSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        commonCodes.take(4).forEach { c ->
                            FilterChip(
                                selected = pointCode == c,
                                onClick = { pointCode = c },
                                label = { Text(c, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Coordenadas Atuais:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text("• Este (X): ${String.format(Locale.US, "%.3f m", gpsStatus.utm.easting)}")
                            Text("• Norte (Y): ${String.format(Locale.US, "%.3f m", gpsStatus.utm.northing)}")
                            Text("• Cota (Z): ${String.format(Locale.US, "%.2f m", gpsStatus.altitude)}")
                            Text("• Precisão: ±${String.format(Locale.US, "%.2f m", gpsStatus.accuracy)}")
                        }
                    }

                    OutlinedTextField(
                        value = pointNotes,
                        onValueChange = { pointNotes = it },
                        label = { Text("Observação (opcional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddPoint(pointName, pointCode, pointNotes)
                        showQuickPointSheet = false
                    },
                    modifier = Modifier.testTag("confirm_save_point_button")
                ) {
                    Text("Salvar Ponto")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickPointSheet = false }) { Text("Cancelar") }
            }
        )
    }

    // Topographic Multi-Sample Averaging Dialog
    if (showAveragingDialog) {
        val progress = if (averagingSession.targetSamples > 0) {
            averagingSession.collectedSamples.toFloat() / averagingSession.targetSamples
        } else 0f
        val isFinished = averagingSession.collectedSamples >= averagingSession.targetSamples

        AlertDialog(
            onDismissRequest = {
                onCancelAveraging()
                showAveragingDialog = false
            },
            icon = { Icon(Icons.Default.HourglassBottom, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Média de Precisão Topográfica") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isFinished) "Coleta de amostras concluída com sucesso!"
                        else "Mantenha o aparelho imóvel sobre o marco enquanto as amostras são calculadas...",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Amostras: ${averagingSession.collectedSamples}/${averagingSession.targetSamples}",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Desvio Padrão σ: ±${String.format(Locale.US, "%.2f m", averagingSession.horizontalStdDev)}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (isFinished) {
                        HorizontalDivider()
                        Text("Coordenada Média Resultante (UTM 24S SIRGAS 2000):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text("• Este (X): ${String.format(Locale.US, "%.4f m", averagingSession.utm.easting)}")
                        Text("• Norte (Y): ${String.format(Locale.US, "%.4f m", averagingSession.utm.northing)}")
                        Text("• Cota (Z): ${String.format(Locale.US, "%.3f m", averagingSession.meanAltitude)}")

                        OutlinedTextField(
                            value = pointName,
                            onValueChange = { pointName = it },
                            label = { Text("Nome do Ponto") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                if (isFinished) {
                    Button(
                        onClick = {
                            onSaveAveragedPoint(pointName, pointCode, "Média de ${averagingSession.targetSamples} épocas (σ = ${String.format(Locale.US, "%.2f m", averagingSession.horizontalStdDev)})")
                            showAveragingDialog = false
                        }
                    ) {
                        Text("Salvar Ponto Médio")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onCancelAveraging()
                        showAveragingDialog = false
                    }
                ) {
                    Text(if (isFinished) "Fechar" else "Cancelar")
                }
            }
        )
    }

    // Clear Track confirmation dialog
    if (showClearTrackDialog) {
        AlertDialog(
            onDismissRequest = { showClearTrackDialog = false },
            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Limpar Trajetória?") },
            text = { Text("Deseja apagar os ${trajectory.size} nós da trajetória atual deste projeto?") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearTrajectory()
                        showClearTrackDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Limpar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearTrackDialog = false }) { Text("Cancelar") }
            }
        )
    }

    // Selected Point Details bottom dialog
    selectedPoint?.let { pt ->
        val latLon = remember(pt) { LatLon(pt.latitude, pt.longitude) }
        AlertDialog(
            onDismissRequest = { selectedPoint = null },
            icon = { Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("${pt.pointName} (${pt.code})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("• Este (X): ${String.format(Locale.US, "%.3f m", pt.easting)}", fontWeight = FontWeight.Bold)
                    Text("• Norte (Y): ${String.format(Locale.US, "%.3f m", pt.northing)}", fontWeight = FontWeight.Bold)
                    Text("• Cota (Z): ${String.format(Locale.US, "%.2f m", pt.altitude)}")
                    Text("• Fuso UTM: ${pt.utmZone}${pt.hemisphere}")
                    Text("• Lat/Lon: ${latLon.toDmsLatitude()}, ${latLon.toDmsLongitude()}")
                    Text("• Precisão: ±${String.format(Locale.US, "%.2f m", pt.accuracy)}")
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedPoint = null }) { Text("Fechar") }
            }
        )
    }
}
