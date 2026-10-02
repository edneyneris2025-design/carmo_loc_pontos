package com.example.ui.points

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SurveyPoint
import com.example.data.model.SurveyProject
import com.example.location.GpsStatus
import com.example.util.CoordinateUtils
import com.example.util.LatLon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PointsListScreen(
    currentProject: SurveyProject?,
    points: List<SurveyPoint>,
    gpsStatus: GpsStatus,
    onAddPointGps: (name: String, code: String, notes: String) -> Unit,
    onAddManualPoint: (name: String, code: String, easting: Double, northing: Double, altitude: Double, notes: String) -> Unit,
    onUpdatePoint: (SurveyPoint) -> Unit,
    onDeletePoint: (SurveyPoint) -> Unit,
    onExportDxf: () -> Unit,
    onExportCsv: () -> Unit,
    onExportTxt: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var showManualAddDialog by remember { mutableStateOf(false) }
    var editingPoint by remember { mutableStateOf<SurveyPoint?>(null) }
    var selectedDetailPoint by remember { mutableStateOf<SurveyPoint?>(null) }

    val filteredPoints = remember(points, searchQuery) {
        if (searchQuery.isBlank()) points
        else points.filter {
            it.pointName.contains(searchQuery, ignoreCase = true) ||
                    it.code.contains(searchQuery, ignoreCase = true) ||
                    it.notes.contains(searchQuery, ignoreCase = true)
        }
    }

    // Geometry summary
    val polygonPoints = remember(points) { points.map { Pair(it.easting, it.northing) } }
    val areaM2 = remember(polygonPoints) { CoordinateUtils.calculatePolygonArea(polygonPoints) }
    val perimeterM = remember(polygonPoints) { CoordinateUtils.calculatePolygonPerimeter(polygonPoints) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = currentProject?.name ?: "Pontos UTM",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${points.size} pontos cadastrados • Fuso ${currentProject?.utmZone ?: 24}${currentProject?.hemisphere ?: 'S'} • SIRGAS 2000",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onExportTxt, modifier = Modifier.testTag("export_txt_button")) {
                        Icon(Icons.Default.Description, contentDescription = "Exportar TXT")
                    }
                    IconButton(onClick = onExportDxf, modifier = Modifier.testTag("export_dxf_button")) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Exportar DXF", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onExportCsv, modifier = Modifier.testTag("export_csv_button")) {
                        Icon(Icons.Default.TableChart, contentDescription = "Exportar CSV")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showManualAddDialog = true },
                icon = { Icon(Icons.Default.AddLocationAlt, contentDescription = null) },
                text = { Text("Ponto Manual") },
                modifier = Modifier.testTag("add_manual_point_fab")
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Summary Card (Perimeter & Area for Topography)
            if (points.size >= 3) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Área da Poligonal",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = CoordinateUtils.formatArea(areaM2),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Divider(modifier = Modifier.height(36.dp).width(1.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Perímetro Fechado",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = String.format(Locale.US, "%.2f m", perimeterM),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("points_search_field"),
                placeholder = { Text("Buscar ponto ou código (ex: P001, Marco)...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpar busca")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            if (filteredPoints.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Nenhum ponto encontrado" else "Nenhum ponto cadastrado",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Tente outro termo de busca" else "Vá até a aba 'Mapa & GPS' para cadastrar pontos sequenciais com o GPS ou clique em 'Ponto Manual'.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredPoints, key = { it.id }) { point ->
                        PointCard(
                            point = point,
                            onCardClick = { selectedDetailPoint = point },
                            onEditClick = { editingPoint = point },
                            onDeleteClick = { onDeletePoint(point) }
                        )
                    }
                }
            }
        }
    }

    // Manual Point Add Dialog
    if (showManualAddDialog) {
        ManualPointDialog(
            defaultZone = currentProject?.utmZone ?: 24,
            defaultHemisphere = currentProject?.hemisphere ?: 'S',
            nextSeq = (points.maxOfOrNull { it.sequenceNumber } ?: 0) + 1,
            onDismiss = { showManualAddDialog = false },
            onConfirm = { name, code, e, n, z, notes ->
                onAddManualPoint(name, code, e, n, z, notes)
                showManualAddDialog = false
            }
        )
    }

    // Edit Point Dialog
    editingPoint?.let { pt ->
        EditPointDialog(
            point = pt,
            onDismiss = { editingPoint = null },
            onConfirm = { updated ->
                onUpdatePoint(updated)
                editingPoint = null
            }
        )
    }

    // Detail Point Dialog
    selectedDetailPoint?.let { pt ->
        PointDetailDialog(
            point = pt,
            onDismiss = { selectedDetailPoint = null },
            onEdit = {
                selectedDetailPoint = null
                editingPoint = pt
            }
        )
    }
}

@Composable
fun PointCard(
    point: SurveyPoint,
    onCardClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        onClick = onCardClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("point_card_${point.sequenceNumber}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sequence Number Badge
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = String.format(Locale.US, "#%02d", point.sequenceNumber),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = point.pointName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text(point.code, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "E: ${String.format(Locale.US, "%.3f m", point.easting)}  •  N: ${String.format(Locale.US, "%.3f m", point.northing)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Cota Z: ${String.format(Locale.US, "%.2f m", point.altitude)}  •  Precisão: ±${String.format(Locale.US, "%.1f m", point.accuracy)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onEditClick) {
                Icon(Icons.Default.Edit, contentDescription = "Editar Ponto", tint = MaterialTheme.colorScheme.outline)
            }
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Default.Delete, contentDescription = "Excluir Ponto", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun ManualPointDialog(
    defaultZone: Int,
    defaultHemisphere: Char,
    nextSeq: Int,
    onDismiss: () -> Unit,
    onConfirm: (name: String, code: String, easting: Double, northing: Double, altitude: Double, notes: String) -> Unit
) {
    var name by remember { mutableStateOf(String.format(Locale.US, "P%03d", nextSeq)) }
    var code by remember { mutableStateOf("Vértice") }
    var eastingText by remember { mutableStateOf("") }
    var northingText by remember { mutableStateOf("") }
    var altitudeText by remember { mutableStateOf("0.0") }
    var notes by remember { mutableStateOf("") }

    val commonCodes = listOf("Vértice", "Marco", "Cerca", "Eixo", "Talude", "Poste", "Edificação", "Árvore")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cadastrar Ponto UTM Manual") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do Ponto") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick Code selection
                Text("Código / Tipo:", style = MaterialTheme.typography.labelSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    commonCodes.take(4).forEach { c ->
                        FilterChip(
                            selected = code == c,
                            onClick = { code = c },
                            label = { Text(c, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                OutlinedTextField(
                    value = eastingText,
                    onValueChange = { eastingText = it },
                    label = { Text("Coordenada Este X (m)") },
                    placeholder = { Text("ex: 334512.45") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = northingText,
                    onValueChange = { northingText = it },
                    label = { Text("Coordenada Norte Y (m)") },
                    placeholder = { Text("ex: 7392104.12") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = altitudeText,
                    onValueChange = { altitudeText = it },
                    label = { Text("Altitude / Cota Z (m)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Observações (opcional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val e = eastingText.toDoubleOrNull() ?: 0.0
                    val n = northingText.toDoubleOrNull() ?: 0.0
                    val z = altitudeText.toDoubleOrNull() ?: 0.0
                    onConfirm(name, code, e, n, z, notes)
                },
                enabled = eastingText.toDoubleOrNull() != null && northingText.toDoubleOrNull() != null
            ) {
                Text("Salvar Ponto")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun EditPointDialog(
    point: SurveyPoint,
    onDismiss: () -> Unit,
    onConfirm: (SurveyPoint) -> Unit
) {
    var name by remember { mutableStateOf(point.pointName) }
    var code by remember { mutableStateOf(point.code) }
    var eastingText by remember { mutableStateOf(point.easting.toString()) }
    var northingText by remember { mutableStateOf(point.northing.toString()) }
    var altitudeText by remember { mutableStateOf(point.altitude.toString()) }
    var notes by remember { mutableStateOf(point.notes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Ponto ${point.pointName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do Ponto") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Código / Descrição") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = eastingText,
                    onValueChange = { eastingText = it },
                    label = { Text("Este X (m)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = northingText,
                    onValueChange = { northingText = it },
                    label = { Text("Norte Y (m)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = altitudeText,
                    onValueChange = { altitudeText = it },
                    label = { Text("Cota Z (m)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val e = eastingText.toDoubleOrNull() ?: point.easting
                    val n = northingText.toDoubleOrNull() ?: point.northing
                    val z = altitudeText.toDoubleOrNull() ?: point.altitude
                    onConfirm(
                        point.copy(
                            pointName = name,
                            code = code,
                            easting = e,
                            northing = n,
                            altitude = z,
                            notes = notes
                        )
                    )
                }
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun PointDetailDialog(
    point: SurveyPoint,
    onDismiss: () -> Unit,
    onEdit: () -> Unit
) {
    val latLon = remember(point) { LatLon(point.latitude, point.longitude) }
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "${point.pointName} (#${point.sequenceNumber})")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Código: ${point.code}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Divider()
                Text(
                    text = "Coordenadas UTM:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text("• Fuso: ${point.utmZone}${point.hemisphere} (SIRGAS 2000)")
                Text("• Este (X): ${String.format(Locale.US, "%.4f m", point.easting)}")
                Text("• Norte (Y): ${String.format(Locale.US, "%.4f m", point.northing)}")
                Text("• Cota (Z): ${String.format(Locale.US, "%.3f m", point.altitude)}")
                Divider()
                Text(
                    text = "Coordenadas Geodésicas:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text("• Lat: ${latLon.toDmsLatitude()} (${String.format(Locale.US, "%.7f°", point.latitude)})")
                Text("• Lon: ${latLon.toDmsLongitude()} (${String.format(Locale.US, "%.7f°", point.longitude)})")
                Text("• Precisão GPS: ±${String.format(Locale.US, "%.2f m", point.accuracy)}")
                Text("• Data/Hora: ${sdf.format(Date(point.timestamp))}")
                if (point.notes.isNotBlank()) {
                    Text("• Notas: ${point.notes}")
                }
            }
        },
        confirmButton = {
            Button(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Editar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Fechar") }
        }
    )
}
