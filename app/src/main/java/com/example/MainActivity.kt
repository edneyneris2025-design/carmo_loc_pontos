package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.SurveyTab
import com.example.ui.SurveyViewModel
import com.example.ui.cad.CadViewerScreen
import com.example.ui.map.MapGpsScreen
import com.example.ui.points.PointsListScreen
import com.example.ui.projects.ProjectsScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: SurveyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppScreen(viewModel: SurveyViewModel) {
    val context = LocalContext.current
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val activeProjectId by viewModel.activeProjectId.collectAsStateWithLifecycle()
    val currentProject by viewModel.currentProject.collectAsStateWithLifecycle()
    val points by viewModel.currentPoints.collectAsStateWithLifecycle()
    val trajectory by viewModel.currentTrajectory.collectAsStateWithLifecycle()
    val gpsStatus by viewModel.gpsStatus.collectAsStateWithLifecycle()
    val averagingSession by viewModel.averagingSession.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    // Back handling for sub-tabs
    BackHandler(enabled = currentTab != SurveyTab.MAP_GPS) {
        viewModel.setTab(SurveyTab.MAP_GPS)
    }

    // Export DXF File Picker
    val exportDxfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/dxf")
    ) { uri ->
        if (uri != null) {
            viewModel.exportDxfToUri(context, uri)
        }
    }

    // Export CSV File Picker
    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            viewModel.exportCsvToUri(context, uri)
        }
    }

    // Export TXT File Picker
    val exportTxtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            viewModel.exportTxtToUri(context, uri)
        }
    }

    // Export Dialog State
    var showExportDxfDialog by remember { mutableStateOf(false) }
    var showExportCsvDialog by remember { mutableStateOf(false) }
    var showExportTxtDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("main_navigation_bar"),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = currentTab == SurveyTab.MAP_GPS,
                    onClick = { viewModel.setTab(SurveyTab.MAP_GPS) },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == SurveyTab.MAP_GPS) Icons.Filled.Map else Icons.Outlined.Map,
                            contentDescription = "Mapa & GPS"
                        )
                    },
                    label = { Text("Mapa & GPS") },
                    modifier = Modifier.testTag("tab_map_gps")
                )
                NavigationBarItem(
                    selected = currentTab == SurveyTab.POINTS,
                    onClick = { viewModel.setTab(SurveyTab.POINTS) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (points.isNotEmpty()) {
                                    Badge { Text("${points.size}") }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (currentTab == SurveyTab.POINTS) Icons.Filled.FormatListNumbered else Icons.Outlined.FormatListNumbered,
                                contentDescription = "Pontos UTM"
                            )
                        }
                    },
                    label = { Text("Pontos") },
                    modifier = Modifier.testTag("tab_points")
                )
                NavigationBarItem(
                    selected = currentTab == SurveyTab.CAD_VIEWER,
                    onClick = { viewModel.setTab(SurveyTab.CAD_VIEWER) },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == SurveyTab.CAD_VIEWER) Icons.Filled.Architecture else Icons.Outlined.Architecture,
                            contentDescription = "CAD DXF/DWG"
                        )
                    },
                    label = { Text("CAD DXF") },
                    modifier = Modifier.testTag("tab_cad")
                )
                NavigationBarItem(
                    selected = currentTab == SurveyTab.PROJECTS,
                    onClick = { viewModel.setTab(SurveyTab.PROJECTS) },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == SurveyTab.PROJECTS) Icons.Filled.Folder else Icons.Outlined.Folder,
                            contentDescription = "Projetos"
                        )
                    },
                    label = { Text("Projetos") },
                    modifier = Modifier.testTag("tab_projects")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                SurveyTab.MAP_GPS -> {
                    MapGpsScreen(
                        currentProject = currentProject,
                        points = points,
                        trajectory = trajectory,
                        gpsStatus = gpsStatus,
                        averagingSession = averagingSession,
                        onAddPoint = { name, code, notes ->
                            viewModel.addPointFromGps(name, code, notes)
                        },
                        onStartAveraging = { samples ->
                            viewModel.gpsManager.startAveraging(samples)
                        },
                        onCancelAveraging = {
                            viewModel.gpsManager.cancelAveraging()
                        },
                        onSaveAveragedPoint = { name, code, notes ->
                            viewModel.saveAveragedPoint(name, code, notes)
                        },
                        onToggleTrajectory = { enabled ->
                            viewModel.toggleTrajectory(enabled)
                        },
                        onClearTrajectory = {
                            viewModel.clearTrajectory()
                        },
                        onExportTxt = { showExportTxtDialog = true }
                    )
                }

                SurveyTab.POINTS -> {
                    PointsListScreen(
                        currentProject = currentProject,
                        points = points,
                        gpsStatus = gpsStatus,
                        onAddPointGps = { name, code, notes ->
                            viewModel.addPointFromGps(name, code, notes)
                        },
                        onAddManualPoint = { name, code, easting, northing, altitude, notes ->
                            viewModel.addManualPoint(name, code, easting, northing, altitude, notes)
                        },
                        onUpdatePoint = { viewModel.updatePoint(it) },
                        onDeletePoint = { viewModel.deletePoint(it) },
                        onExportDxf = { showExportDxfDialog = true },
                        onExportCsv = { showExportCsvDialog = true },
                        onExportTxt = { showExportTxtDialog = true }
                    )
                }

                SurveyTab.CAD_VIEWER -> {
                    CadViewerScreen(
                        currentProject = currentProject,
                        projectPoints = points,
                        projectTrajectory = trajectory
                    )
                }

                SurveyTab.PROJECTS -> {
                    ProjectsScreen(
                        projects = projects,
                        activeProjectId = activeProjectId,
                        gpsStatus = gpsStatus,
                        onSelectProject = { viewModel.selectProject(it) },
                        onCreateProject = { name, desc, zone, hemisphere ->
                            viewModel.createProject(name, desc, zone, hemisphere)
                        },
                        onDeleteProject = { viewModel.deleteProject(it) },
                        onSyncProject = { viewModel.syncProject(it) }
                    )
                }
            }
        }
    }

    // Export DXF Dialog
    if (showExportDxfDialog) {
        val prjName = currentProject?.name?.replace(" ", "_") ?: "Levantamento"
        AlertDialog(
            onDismissRequest = { showExportDxfDialog = false },
            icon = { Icon(Icons.Default.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Exportar Desenho CAD (DXF)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("O arquivo DXF será gerado com camadas padrão AutoCAD:")
                    Text("• PONTOS_UTM (Pontos com coordenadas X, Y, Z)")
                    Text("• NOMES_PONTOS & COTAS_ALTITUDE")
                    Text("• POLIGONAL_SEQUENCIAL (Polilinha)")
                    Text("• TRAJETORIA_GPS (Nós do percurso)")
                    Text("Total de pontos a exportar: ${points.size}", fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExportDxfDialog = false
                        exportDxfLauncher.launch("${prjName}_UTM.dxf")
                    }
                ) {
                    Text("Salvar Arquivo .DXF")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExportDxfDialog = false
                        viewModel.shareDxfFile(context)
                    }
                ) {
                    Text("Compartilhar...")
                }
            }
        )
    }

    // Export CSV Dialog
    if (showExportCsvDialog) {
        val prjName = currentProject?.name?.replace(" ", "_") ?: "Levantamento"
        AlertDialog(
            onDismissRequest = { showExportCsvDialog = false },
            icon = { Icon(Icons.Default.TableChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Exportar Tabela de Pontos (CSV)") },
            text = {
                Text("Gera planilha com Ponto, Coordenadas Este X, Norte Y, Cota Z, Código, Fuso UTM, Lat/Long e precisão.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExportCsvDialog = false
                        exportCsvLauncher.launch("${prjName}_Pontos_UTM.csv")
                    }
                ) {
                    Text("Salvar Arquivo .CSV")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExportCsvDialog = false
                        viewModel.shareCsvFile(context)
                    }
                ) {
                    Text("Compartilhar...")
                }
            }
        )
    }

    // Export TXT Dialog
    if (showExportTxtDialog) {
        val prjName = currentProject?.name?.replace(" ", "_") ?: "Levantamento"
        AlertDialog(
            onDismissRequest = { showExportTxtDialog = false },
            icon = { Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Exportar Pontos em Formato Texto (TXT)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Gera relatório topográfico com cabeçalho completo e formato padrão PENZD (Ponto, Este, Norte, Cota, Descrição):")
                    Text("• Tabela com coordenadas UTM métricas e Geodésicas")
                    Text("• Formato compatível com Estação Total, GPS RTK e AutoCAD Civil 3D")
                    Text("Total de pontos a exportar: ${points.size}", fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExportTxtDialog = false
                        exportTxtLauncher.launch("${prjName}_Pontos_UTM.txt")
                    }
                ) {
                    Text("Salvar Arquivo .TXT")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExportTxtDialog = false
                        viewModel.shareTxtFile(context)
                    }
                ) {
                    Text("Compartilhar...")
                }
            }
        )
    }
}
