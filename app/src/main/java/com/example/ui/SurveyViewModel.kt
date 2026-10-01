package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cad.DxfExporter
import com.example.data.db.AppDatabase
import com.example.data.model.SurveyPoint
import com.example.data.model.SurveyProject
import com.example.data.model.TrackPoint
import com.example.data.repository.SurveyRepository
import com.example.location.AveragingSession
import com.example.location.GpsManager
import com.example.location.GpsStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SurveyTab(val title: String) {
    MAP_GPS("Mapa & GPS"),
    POINTS("Pontos UTM"),
    CAD_VIEWER("CAD DXF/DWG"),
    PROJECTS("Projetos")
}

class SurveyViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val repository = SurveyRepository(database.surveyDao())
    val gpsManager = GpsManager(application)

    val gpsStatus: StateFlow<GpsStatus> = gpsManager.gpsStatus
    val averagingSession: StateFlow<AveragingSession> = gpsManager.averagingSession

    val projects: StateFlow<List<SurveyProject>> = repository.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeProjectId = MutableStateFlow(1L)
    val activeProjectId: StateFlow<Long> = _activeProjectId.asStateFlow()

    private val _currentTab = MutableStateFlow(SurveyTab.MAP_GPS)
    val currentTab: StateFlow<SurveyTab> = _currentTab.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    val currentProject: StateFlow<SurveyProject?> = _activeProjectId
        .flatMapLatest { id -> repository.getProject(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentPoints: StateFlow<List<SurveyPoint>> = _activeProjectId
        .flatMapLatest { id -> repository.getPoints(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentTrajectory: StateFlow<List<TrackPoint>> = _activeProjectId
        .flatMapLatest { id -> repository.getTrajectory(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Start GPS updates and trajectory hook
        gpsManager.startLocationUpdates { loc, utm ->
            val prjId = _activeProjectId.value
            if (prjId > 0) {
                viewModelScope.launch {
                    repository.addTrackPoint(
                        projectId = prjId,
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        altitude = loc.altitude,
                        accuracy = loc.accuracy
                    )
                }
            }
        }

        // Initialize default project if none exists
        viewModelScope.launch {
            projects.take(2).collect { list ->
                if (list.isEmpty()) {
                    val defaultId = repository.createProject(
                        name = "Levantamento Principal",
                        description = "Projeto topográfico inicial de campo com coordenadas UTM.",
                        utmZone = 23,
                        hemisphere = 'S'
                    )
                    _activeProjectId.value = defaultId
                } else if (_activeProjectId.value == 1L && list.isNotEmpty()) {
                    _activeProjectId.value = list.first().id
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        gpsManager.stopLocationUpdates()
    }

    fun setTab(tab: SurveyTab) {
        _currentTab.value = tab
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun selectProject(projectId: Long) {
        _activeProjectId.value = projectId
        gpsManager.resetTrajectoryStats()
    }

    fun createProject(name: String, description: String, zone: Int, hemisphere: Char) {
        viewModelScope.launch {
            val id = repository.createProject(name, description, zone, hemisphere)
            _activeProjectId.value = id
            _snackbarMessage.value = "Projeto '$name' criado com sucesso!"
        }
    }

    fun deleteProject(project: SurveyProject) {
        viewModelScope.launch {
            repository.deleteProject(project)
            _snackbarMessage.value = "Projeto '${project.name}' removido."
        }
    }

    fun syncProject(project: SurveyProject) {
        viewModelScope.launch {
            repository.markProjectSynced(project.id)
            _snackbarMessage.value = "Projeto '${project.name}' sincronizado offline com sucesso!"
        }
    }

    fun addPointFromGps(name: String, code: String, notes: String) {
        val prjId = _activeProjectId.value
        val gps = gpsStatus.value
        if (!gps.hasFix || gps.latitude == 0.0) {
            _snackbarMessage.value = "Aguardando sinal GPS estável..."
            return
        }

        viewModelScope.launch {
            val pointId = repository.addPoint(
                projectId = prjId,
                pointName = name,
                code = code,
                latitude = gps.latitude,
                longitude = gps.longitude,
                altitude = gps.altitude,
                accuracy = gps.accuracy,
                notes = notes
            )
            _snackbarMessage.value = "Ponto $name registrado com sucesso! (UTM ${gps.utm.shortZoneString()})"
        }
    }

    fun addManualPoint(name: String, code: String, easting: Double, northing: Double, altitude: Double, notes: String) {
        val prj = currentProject.value ?: return
        viewModelScope.launch {
            repository.addManualPoint(
                projectId = prj.id,
                name = name,
                code = code,
                easting = easting,
                northing = northing,
                altitude = altitude,
                utmZone = prj.utmZone,
                hemisphere = prj.hemisphere,
                notes = notes
            )
            _snackbarMessage.value = "Ponto manual $name adicionado!"
        }
    }

    fun saveAveragedPoint(name: String, code: String, notes: String) {
        val prjId = _activeProjectId.value
        val avg = averagingSession.value
        if (avg.collectedSamples == 0) return

        viewModelScope.launch {
            repository.addPoint(
                projectId = prjId,
                pointName = name,
                code = code,
                latitude = avg.meanLatitude,
                longitude = avg.meanLongitude,
                altitude = avg.meanAltitude,
                accuracy = avg.horizontalStdDev.toFloat(),
                notes = notes
            )
            gpsManager.cancelAveraging()
            _snackbarMessage.value = "Ponto médio $name salvo (σ = ${String.format(Locale.US, "%.2f m", avg.horizontalStdDev)})"
        }
    }

    fun updatePoint(point: SurveyPoint) {
        viewModelScope.launch {
            repository.updatePoint(point)
            _snackbarMessage.value = "Ponto ${point.pointName} atualizado."
        }
    }

    fun deletePoint(point: SurveyPoint) {
        viewModelScope.launch {
            repository.deletePoint(point)
            _snackbarMessage.value = "Ponto ${point.pointName} removido."
        }
    }

    fun toggleTrajectory(enabled: Boolean) {
        gpsManager.setTrajectoryRecording(enabled)
        _snackbarMessage.value = if (enabled) "Gravação de trajetória iniciada." else "Gravação de trajetória pausada."
    }

    fun clearTrajectory() {
        val prjId = _activeProjectId.value
        viewModelScope.launch {
            repository.clearTrajectory(prjId)
            gpsManager.resetTrajectoryStats()
            _snackbarMessage.value = "Trajetória limpa."
        }
    }

    fun exportDxfToUri(context: Context, uri: Uri) {
        val prj = currentProject.value ?: return
        val pts = currentPoints.value
        val track = currentTrajectory.value
        viewModelScope.launch {
            try {
                val content = DxfExporter.generateDxfContent(prj, pts, track)
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(content.toByteArray(Charsets.UTF_8))
                }
                _snackbarMessage.value = "Arquivo DXF salvo com sucesso!"
            } catch (e: Exception) {
                _snackbarMessage.value = "Erro ao exportar DXF: ${e.message}"
            }
        }
    }

    fun exportCsvToUri(context: Context, uri: Uri) {
        val pts = currentPoints.value
        viewModelScope.launch {
            try {
                val content = DxfExporter.generateCsv(pts)
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(content.toByteArray(Charsets.UTF_8))
                }
                _snackbarMessage.value = "Arquivo CSV salvo com sucesso!"
            } catch (e: Exception) {
                _snackbarMessage.value = "Erro ao exportar CSV: ${e.message}"
            }
        }
    }

    fun shareDxfFile(context: Context) {
        val prj = currentProject.value ?: return
        val pts = currentPoints.value
        val track = currentTrajectory.value

        viewModelScope.launch {
            try {
                val content = DxfExporter.generateDxfContent(prj, pts, track)
                val fileName = "${prj.name.replace(" ", "_")}_UTM.dxf"
                val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val file = File(exportDir, fileName)
                file.writeText(content, Charsets.UTF_8)

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/dxf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Levantamento Topográfico DXF: ${prj.name}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Compartilhar DXF Topográfico"))
            } catch (e: Exception) {
                _snackbarMessage.value = "Erro ao compartilhar DXF: ${e.message}"
            }
        }
    }

    fun shareCsvFile(context: Context) {
        val prj = currentProject.value ?: return
        val pts = currentPoints.value

        viewModelScope.launch {
            try {
                val content = DxfExporter.generateCsv(pts)
                val fileName = "${prj.name.replace(" ", "_")}_Pontos_UTM.csv"
                val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val file = File(exportDir, fileName)
                file.writeText(content, Charsets.UTF_8)

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Pontos Topográficos CSV: ${prj.name}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Compartilhar Tabela CSV"))
            } catch (e: Exception) {
                _snackbarMessage.value = "Erro ao compartilhar CSV: ${e.message}"
            }
        }
    }

    fun exportTxtToUri(context: Context, uri: Uri) {
        val prj = currentProject.value ?: return
        val pts = currentPoints.value
        viewModelScope.launch {
            try {
                val content = DxfExporter.generateTxtReport(prj, pts)
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(content.toByteArray(Charsets.UTF_8))
                }
                _snackbarMessage.value = "Arquivo TXT de pontos salvo com sucesso!"
            } catch (e: Exception) {
                _snackbarMessage.value = "Erro ao exportar TXT: ${e.message}"
            }
        }
    }

    fun shareTxtFile(context: Context) {
        val prj = currentProject.value ?: return
        val pts = currentPoints.value

        viewModelScope.launch {
            try {
                val content = DxfExporter.generateTxtReport(prj, pts)
                val fileName = "${prj.name.replace(" ", "_")}_Relatorio_Pontos.txt"
                val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val file = File(exportDir, fileName)
                file.writeText(content, Charsets.UTF_8)

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Relatório Topográfico TXT: ${prj.name}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Compartilhar Relatório TXT"))
            } catch (e: Exception) {
                _snackbarMessage.value = "Erro ao compartilhar TXT: ${e.message}"
            }
        }
    }
}
