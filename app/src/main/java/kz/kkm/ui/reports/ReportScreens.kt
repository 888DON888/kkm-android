package kz.kkm.ui.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kz.kkm.data.repository.ShiftRepository
import kz.kkm.domain.model.XReport
import kz.kkm.domain.model.ZReport
import kz.kkm.ui.main.formatTenge
import kz.kkm.ui.theme.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import javax.inject.Inject

// ─────────────────── ViewModels ─────────────────────────────

@HiltViewModel
class XReportViewModel @Inject constructor(
    private val shiftRepo: ShiftRepository
) : ViewModel() {
    private val _report = MutableStateFlow<XReport?>(null)
    val report = _report.asStateFlow()

    init {
        viewModelScope.launch { _report.value = shiftRepo.getXReport() }
    }
}

@HiltViewModel
class ZReportViewModel @Inject constructor(
    private val shiftRepo: ShiftRepository
) : ViewModel() {
    private val _state = MutableStateFlow<ZReportUiState>(ZReportUiState.Idle)
    val state = _state.asStateFlow()

    fun closeShift() {
        viewModelScope.launch {
            _state.value = ZReportUiState.Loading
            shiftRepo.closeShift()
                .onSuccess { z -> _state.value = ZReportUiState.Done(z) }
                .onFailure { e -> _state.value = ZReportUiState.Error(e.message ?: "Ошибка") }
        }
    }
}

sealed class ZReportUiState {
    object Idle    : ZReportUiState()
    object Loading : ZReportUiState()
    data class Done(val report: ZReport) : ZReportUiState()
    data class Error(val message: String) : ZReportUiState()
}

// ─────────────────── X-Report Screen ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XReportScreen(
    onBack: () -> Unit,
    viewModel: XReportViewModel = hiltViewModel()
) {
    val report by viewModel.report.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("X-отчёт") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = KkmBlue,
                    titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        }
    ) { padding ->
        if (report == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Нет открытой смены", color = Color.Gray)
            }
            return@Scaffold
        }
        val r = report!!
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReportHeader(title = "X-ОТЧЁТ", subtitle = "Промежуточный (без закрытия смены)")
            ReportInfoRow("Смена №", r.shiftNumber.toString())
            ReportInfoRow("Открыта", r.openedAt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")))
            ReportInfoRow("Сформирован", r.printedAt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")))
            Divider()
            ReportMoneyRow("Продажи (${r.receiptsCount} чеков)", r.totalSales.toLong(), color = KkmGreen)
            ReportMoneyRow("Возвраты (${r.returnsCount} чеков)", r.totalReturns.toLong(), color = KkmRed)
            ReportMoneyRow("Аннулированные", r.totalCancelled.toLong(), color = KkmAmber)
            Divider()
            if (r.vatTotal.compareTo(java.math.BigDecimal.ZERO) != 0)
                ReportMoneyRow("НДС 12%", r.vatTotal.toLong())
            Divider()
            ReportMoneyRow("НАЛИЧНЫЕ", r.totalCash.toLong())
            ReportMoneyRow("БЕЗНАЛ", r.totalCard.toLong())
            Divider()
            ReportMoneyRow("ИТОГО ПО КАССЕ",
                (r.totalSales - r.totalReturns).toLong(),
                bold = true, color = KkmBlue)
        }
    }
}

// ─────────────────── Z-Report Screen ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZReportScreen(
    onShiftClosed: () -> Unit,
    onBack: () -> Unit,
    viewModel: ZReportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var confirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        if (state is ZReportUiState.Done) {
            kotlinx.coroutines.delay(3000)
            onShiftClosed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Закрытие смены") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = KkmBlue,
                    titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is ZReportUiState.Idle -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.AssignmentTurnedIn, null,
                            modifier = Modifier.size(80.dp), tint = KkmBlue)
                        Spacer(Modifier.height(16.dp))
                        Text("Закрыть смену", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("Z-отчёт будет отправлен в ОФД",
                            color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp))
                        Spacer(Modifier.height(32.dp))
                        Button(
                            onClick = { confirmDialog = true },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KkmRed)
                        ) {
                            Text("Закрыть смену и Z-отчёт", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
                is ZReportUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = KkmBlue)
                            Spacer(Modifier.height(16.dp))
                            Text("Закрываю смену, отправляю Z-отчёт в ОФД...")
                        }
                    }
                }
                is ZReportUiState.Done -> {
                    val z = s.report
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = KkmGreen,
                                modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Смена закрыта!", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                                color = KkmGreen)
                        }
                        ReportHeader("Z-ОТЧЁТ", "Смена №${z.shiftNumber}")
                        z.ofdTicket?.let { ReportInfoRow("ОФД Ticket", it) }
                        ReportInfoRow("Открыта", z.openedAt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")))
                        ReportInfoRow("Закрыта", z.closedAt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")))
                        Divider()
                        ReportMoneyRow("Продажи (${z.xReport.receiptsCount} чеков)",
                            z.xReport.totalSales.toLong(), color = KkmGreen)
                        ReportMoneyRow("Возвраты (${z.xReport.returnsCount} чеков)",
                            z.xReport.totalReturns.toLong(), color = KkmRed)
                        Divider()
                        ReportMoneyRow("ИТОГО",
                            (z.xReport.totalSales - z.xReport.totalReturns).toLong(),
                            bold = true, color = KkmBlue)
                    }
                }
                is ZReportUiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Ошибка: ${s.message}", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (confirmDialog) {
        AlertDialog(
            onDismissRequest = { confirmDialog = false },
            title = { Text("Закрыть смену?") },
            text = { Text("Будет сформирован Z-отчёт и отправлен в ОФД. Отменить нельзя.") },
            confirmButton = {
                TextButton(onClick = { confirmDialog = false; viewModel.closeShift() }) {
                    Text("Закрыть", color = KkmRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDialog = false }) { Text("Отмена") }
            }
        )
    }
}

// ─────────────────── Shared composables ─────────────────────

@Composable
private fun ReportHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = KkmBlue)
        Text(subtitle, fontSize = 13.sp, color = Color.Gray)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ReportInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = Color.Gray)
        Text(value, fontSize = 13.sp)
    }
}

@Composable
private fun ReportMoneyRow(
    label: String,
    amount: Long,
    bold: Boolean = false,
    color: Color = Color.Unspecified
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(label,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontSize = if (bold) 16.sp else 14.sp)
        Text(formatTenge(amount),
            fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.SemiBold,
            fontSize = if (bold) 16.sp else 14.sp,
            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color)
    }
}
