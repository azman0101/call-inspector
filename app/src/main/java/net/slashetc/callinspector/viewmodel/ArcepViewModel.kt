package net.slashetc.callinspector.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import net.slashetc.callinspector.data.db.ArcepDatabaseManager
import net.slashetc.callinspector.data.db.DatabaseStats
import net.slashetc.callinspector.data.db.ReporterProfileStore
import net.slashetc.callinspector.data.model.ArcepLookupResult
import net.slashetc.callinspector.data.model.CallLogEntry
import net.slashetc.callinspector.data.model.CallType
import net.slashetc.callinspector.data.repository.CallLogRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.slashetc.callinspector.util.PhoneLines
import net.slashetc.callinspector.util.ReceivingLine
import net.slashetc.callinspector.util.SearchQueries

enum class CallFilter {
    TOUS,
    MANQUES,
    ENTRANTS,
    DEMARCHAGE_SPAM,
    FAVORIS
}

data class ArcepUiState(
    val calls: List<CallLogEntry> = emptyList(),
    val filteredCalls: List<CallLogEntry> = emptyList(),
    val selectedFilter: CallFilter = CallFilter.TOUS,
    val callSearchQuery: String = "",
    val hasPermission: Boolean = false,
    val isUsingSampleData: Boolean = false,
    val isLoading: Boolean = false,
    val currentTab: Int = 0, // 0: Historique, 1: Recherche, 2: Observatoire & Stats
    val selectedCallDetail: CallLogEntry? = null,
    val manualSearchInput: String = "",
    val manualLookupResult: ArcepLookupResult? = null,
    val prefixSearchResults: List<ArcepLookupResult> = emptyList(),
    val isSearchingManual: Boolean = false,
    val databaseStats: DatabaseStats? = null,
    val userNotice: String? = null,
    val showPermissionDialog: Boolean = false,
    val permissionDeniedCount: Int = 0,
    /** The call export being shown, by receiving line; null when closed. */
    val exportLines: List<ReceivingLine>? = null,
    val updateStatus: net.slashetc.callinspector.data.repository.UpdateStatus = net.slashetc.callinspector.data.repository.UpdateStatus.Idle
) {
    val isPermanentlyDenied: Boolean
        get() = permissionDeniedCount >= 2
}

class ArcepViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CallLogRepository(application)
    private val dbManager = ArcepDatabaseManager.getInstance(application)
    private val updateManager = net.slashetc.callinspector.data.repository.ArcepUpdateManager(application)
    private val profileStore = ReporterProfileStore.getInstance(application)
    private val lineDetector = net.slashetc.callinspector.data.repository.PhoneLineDetector(application)

    private val _uiState = MutableStateFlow(ArcepUiState())
    val uiState: StateFlow<ArcepUiState> = _uiState.asStateFlow()

    private var manualSearchJob: Job? = null

    init {
        checkPermissionAndLoad()
        loadStats()
    }

    fun checkPermissionAndLoad() {
        val granted = repository.hasPermission()
        _uiState.update { it.copy(hasPermission = granted) }
        loadCalls()
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            _uiState.update {
                it.copy(
                    hasPermission = true,
                    permissionDeniedCount = 0,
                    showPermissionDialog = false,
                    userNotice = "Journal d'appels synchronisé avec succès"
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    hasPermission = false,
                    permissionDeniedCount = it.permissionDeniedCount + 1,
                    showPermissionDialog = false,
                    userNotice = "Accès refusé. Mode Démo actif."
                )
            }
        }
        loadCalls()
    }

    fun openPermissionDialog() {
        _uiState.update { it.copy(showPermissionDialog = true) }
    }

    fun closePermissionDialog() {
        _uiState.update { it.copy(showPermissionDialog = false) }
    }

    fun loadCalls() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val hasPerm = repository.hasPermission()
            val entries = repository.getCallLogs(useSampleIfEmpty = !hasPerm)
            val isSample = !hasPerm || entries.any { it.id >= 1000L && it.rawNumber == "0162001122" }

            _uiState.update { state ->
                val filtered = applyFilter(entries, state.selectedFilter, state.callSearchQuery)
                state.copy(
                    calls = entries,
                    filteredCalls = filtered,
                    // An open detail sheet follows the reloaded call (e.g. its report count after a report).
                    selectedCallDetail = state.selectedCallDetail?.let { open -> entries.find { it.id == open.id } ?: open },
                    hasPermission = hasPerm,
                    isUsingSampleData = isSample,
                    isLoading = false
                )
            }
        }
    }

    fun forceLoadSampleCalls() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val samples = repository.generateSampleCalls()
            _uiState.update { state ->
                val filtered = applyFilter(samples, state.selectedFilter, state.callSearchQuery)
                state.copy(
                    calls = samples,
                    filteredCalls = filtered,
                    isUsingSampleData = true,
                    isLoading = false,
                    userNotice = "Exemples d'appels chargés"
                )
            }
        }
    }

    fun setFilter(filter: CallFilter) {
        _uiState.update { state ->
            val filtered = applyFilter(state.calls, filter, state.callSearchQuery)
            state.copy(selectedFilter = filter, filteredCalls = filtered)
        }
    }

    fun setCallSearchQuery(query: String) {
        _uiState.update { state ->
            val filtered = applyFilter(state.calls, state.selectedFilter, query)
            state.copy(callSearchQuery = query, filteredCalls = filtered)
        }
    }

    private fun applyFilter(calls: List<CallLogEntry>, filter: CallFilter, query: String): List<CallLogEntry> {
        return calls.filter { call ->
            val matchesFilter = when (filter) {
                CallFilter.TOUS -> true
                CallFilter.MANQUES -> call.callType == CallType.MISSED || call.callType == CallType.REJECTED
                CallFilter.ENTRANTS -> call.callType == CallType.INCOMING
                CallFilter.DEMARCHAGE_SPAM -> call.isSpamFlagged || call.lookupResult.numberType.isDemarchage
                CallFilter.FAVORIS -> call.isFavorite
            }

            matchesFilter && SearchQueries.matchesCall(call, query)
        }
    }

    fun setTab(index: Int) {
        _uiState.update { it.copy(currentTab = index) }
    }

    fun selectCallDetail(call: CallLogEntry?) {
        _uiState.update { it.copy(selectedCallDetail = call) }
    }

    fun toggleSpamFlag(call: CallLogEntry) {
        viewModelScope.launch {
            repository.toggleSpamFlag(call.rawNumber, call.isSpamFlagged)
            loadCalls()
            _uiState.update { state ->
                val updatedCall = state.selectedCallDetail?.takeIf { it.id == call.id }?.copy(
                    isSpamFlagged = !call.isSpamFlagged
                )
                state.copy(selectedCallDetail = updatedCall)
            }
        }
    }

    fun toggleFavorite(call: CallLogEntry) {
        viewModelScope.launch {
            repository.toggleFavorite(call.rawNumber, call.isFavorite)
            loadCalls()
            _uiState.update { state ->
                val updatedCall = state.selectedCallDetail?.takeIf { it.id == call.id }?.copy(
                    isFavorite = !call.isFavorite
                )
                state.copy(selectedCallDetail = updatedCall)
            }
        }
    }

    fun saveCallNote(phoneNumber: String, note: String?) {
        viewModelScope.launch {
            repository.saveNote(phoneNumber, note)
            loadCalls()
            _uiState.update { state ->
                val updatedCall = state.selectedCallDetail?.takeIf { it.rawNumber == phoneNumber }?.copy(
                    userNote = note
                )
                state.copy(selectedCallDetail = updatedCall)
            }
        }
    }

    /**
     * Opens the export of [calls]: groups them by the line (SIM...) that received them, with each line's
     * number from the user's saved lines, the call log or the system.
     */
    fun openExport(calls: List<CallLogEntry>) {
        viewModelScope.launch {
            val stored = runCatching { profileStore.lineNumbers() }.getOrDefault(emptyMap())
            val detected = withContext(Dispatchers.IO) {
                runCatching { lineDetector.detect(calls.mapNotNull { it.lineId }.toSet()) }.getOrDefault(emptyMap())
            }
            _uiState.update { it.copy(exportLines = PhoneLines.linesOf(calls, stored, detected)) }
        }
    }

    /** Remembers the number of one of the user's lines, asked once, and refreshes the open export. */
    fun saveLineNumber(lineKey: String, number: String) {
        viewModelScope.launch {
            profileStore.saveLineNumber(lineKey, number)
            _uiState.value.exportLines?.let { lines -> openExport(lines.flatMap { it.calls }.sortedByDescending { it.timestamp }) }
        }
    }

    fun closeExport() {
        _uiState.update { it.copy(exportLines = null) }
    }

    fun onManualSearchInput(input: String) {
        _uiState.update { it.copy(manualSearchInput = input) }
        manualSearchJob?.cancel()

        if (input.isBlank()) {
            _uiState.update {
                it.copy(
                    manualLookupResult = null,
                    prefixSearchResults = emptyList(),
                    isSearchingManual = false
                )
            }
            return
        }

        manualSearchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearchingManual = true) }
            delay(150) // Small debounce for fluid typing

            val lookup = dbManager.lookupNumber(input)
            if (lookup.isFound) {
                val prefix = lookup.range?.ezabpqm ?: lookup.range?.trancheDebut?.take(4) ?: input.take(4)
                val opName = lookup.operatorDisplayName
                net.slashetc.callinspector.util.SentryHelper.logLookupEvent(getApplication(), prefix, opName)
            }
            val prefixList = SearchQueries.prefixSearchQuery(input)
                ?.let { dbManager.searchPrefixesOrOperators(it) }
                ?: emptyList()

            _uiState.update {
                it.copy(
                    manualLookupResult = lookup,
                    prefixSearchResults = prefixList,
                    isSearchingManual = false
                )
            }
        }
    }

    fun inspectSpecificNumber(number: String) {
        _uiState.update {
            it.copy(
                currentTab = 1,
                manualSearchInput = number
            )
        }
        onManualSearchInput(number)
    }

    private fun loadStats() {
        viewModelScope.launch {
            val stats = dbManager.getStats()
            _uiState.update { it.copy(databaseStats = stats) }
        }
    }

    fun triggerDatabaseUpdate() {
        viewModelScope.launch {
            updateManager.checkAndDownloadUpdate { status ->
                _uiState.update { it.copy(updateStatus = status) }
                if (status is net.slashetc.callinspector.data.repository.UpdateStatus.Success) {
                    loadStats()
                    loadCalls()
                }
            }
        }
    }

    fun resetUpdateStatus() {
        _uiState.update { it.copy(updateStatus = net.slashetc.callinspector.data.repository.UpdateStatus.Idle) }
    }

    fun clearUserNotice() {
        _uiState.update { it.copy(userNotice = null) }
    }
}
