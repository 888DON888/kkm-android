package kz.kkm.ui.shift

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kz.kkm.data.repository.ShiftRepository
import kz.kkm.domain.model.*
import kz.kkm.ui.theme.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import javax.inject.Inject

// ─── ViewModel ───────────────────────────────────────────────

@HiltViewModel
class ShiftViewModel @Inject constructor(
    private val shiftRepo: ShiftRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShiftUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            shiftRepo.observeOpenShift().collect { shift ->
                _uiState.value = _uiState.value.copy(
                    openShift = shift,
                    isLoading = false
                )
            }
        }
    }

    fun openShift() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = shiftRepo.openShift()
            result.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Ошибка открытия смены"
                )
            }
        }
    }

    fun closeShift() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = shiftRepo.closeShift()
            result.fold(
                onSuccess = { zReport ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        zReport = zReport,
                        openShift = null
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Ошибка закрытия смены"
                    )
                }
            )
        }
    }

    fun getXReport() {
        viewModelScope.launch {
            val report = shiftRepo.getXReport()
            _uiState.value = _uiState.value.copy(xReport = report)
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

data class ShiftUiState(
    val openShift: Shift? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val xReport: XReport? = null,
    val zReport: ZReport? = null
)

// ─── Screen ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftScreen(onBack: () -> Unit, vm: ShiftViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsState()
    var showZConfirm by remember { mutableStateOf(false) }
    var showXReport by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Управление сменой") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Shift status card
            val shift = state.openShift
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (shift != null)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (shift != null) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            null,
                            tint = if (shift != null) KkmGreen else KkmGray
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (shift != null) "Смена открыта" else "Смена закрыта",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (shift != null) {
                        val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                        Text("Смена №${shift.shiftNumber}", style = MaterialTheme.typography.bodyMedium)
                        Text("Открыта: ${shift.openedAt.format(fmt)}", style = MaterialTheme.typography.bodySmall)
                        Text("Чеков: ${shift.receiptsCount}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Оборот: ${formatAmount(shift.totalSales)} ₸",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            state.error?.let { err ->
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(err, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Actions
            if (state.openShift == null && !state.isLoading) {
                Button(
                    onClick = vm::openShift,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KkmGreen)
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Открыть смену", fontWeight = FontWeight.Bold)
                }
            }

            if (state.openShift != null && !state.isLoading) {
                // X-Report button
                OutlinedButton(
                    onClick = { vm.getXReport(); showXReport = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Assessment, null)
                    Spacer(Modifier.width(8.dp))
                    Text("X-Отчёт (промежуточный)")
                }

                // Close shift button
                Button(
                    onClick = { showZConfirm = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KkmRed)
                ) {
                    Icon(Icons.Default.Stop, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Закрыть смену (Z-Отчёт)", fontWeight = FontWeight.Bold)
                }
            }

            // Z-Report result
            state.zReport?.let { z ->
                ZReportCard(z)
            }
        }
    }

    // Z-confirm dialog
    if (showZConfirm) {
        AlertDialog(
            onDismissRequest = { showZConfirm = false },
            icon = { Icon(Icons.Default.Warning, null, tint = KkmAmber) },
            title = { Text("Закрыть смену?") },
            text = { Text("Будет сформирован Z-отчёт и отправлен в ОФД. Операции по текущей смене станут недоступны.") },
            confirmButton = {
                Button(
                    onClick = { showZConfirm = false; vm.closeShift() },
                    colors = ButtonDefaults.buttonColors(containerColor = KkmRed)
                ) { Text("Закрыть смену") }
            },
            dismissButton = {
                TextButton(onClick = { showZConfirm = false }) { Text("Отмена") }
            }
        )
    }

    // X-report dialog
    state.xReport?.let { x ->
        if (showXReport) {
            AlertDialog(
                onDismissRequest = { showXReport = false },
                title = { Text("X-Отчёт — Смена №${x.shiftNumber}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        XReportRow("Продажи:", "${formatAmount(x.totalSales)} ₸")
                        XReportRow("Возвраты:", "${formatAmount(x.totalReturns)} ₸")
                        XReportRow("Кол-во чеков:", x.receiptsCount.toString())
                        XReportRow("Кол-во возвратов:", x.returnsCount.toString())
                        XReportRow("НДС итого:", "${formatAmount(x.vatTotal)} ₸")
                        Divider()
                        val net = x.totalSales - x.totalReturns
                        XReportRow("ИТОГО оборот:", "${formatAmount(net)} ₸", bold = true)
                    }
                },
                confirmButton = {
                    Button(onClick = { showXReport = false }) { Text("Закрыть") }
                }
            )
        }
    }
}

@Composable
private fun ZReportCard(z: ZReport) {
    val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Z-Отчёт сформирован", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Divider()
            XReportRow("Смена №:", z.shiftNumber.toString())
            XReportRow("Открыта:", z.openedAt.format(fmt))
            XReportRow("Закрыта:", z.closedAt.format(fmt))
            XReportRow("Продажи:", "${formatAmount(z.xReport.totalSales)} ₸")
            XReportRow("Возвраты:", "${formatAmount(z.xReport.totalReturns)} ₸")
            XReportRow("Чеков:", z.xReport.receiptsCount.toString())
            z.ofdTicket?.let { XReportRow("Тикет ОФД:", it) }
            if (z.ofdTicket == null) {
                Text(
                    "⚠ ОФД не ответил. Будет выполнена повторная отправка.",
                    color = KkmAmber,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun XReportRow(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = KkmGray)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
    }
}

private fun formatAmount(bd: java.math.BigDecimal): String =
    java.text.NumberFormat.getNumberInstance(java.util.Locale("ru", "KZ")).apply {
        maximumFractionDigits = 0
    }.format(bd)
