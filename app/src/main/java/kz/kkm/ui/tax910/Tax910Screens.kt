package kz.kkm.ui.tax910

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kz.kkm.data.local.dao.EmployeeDao
import kz.kkm.data.local.dao.TaxPeriodDao
import kz.kkm.data.local.entity.*
import kz.kkm.data.remote.IsnaApiService
import kz.kkm.data.repository.ReceiptRepository
import kz.kkm.data.repository.SettingsRepository
import kz.kkm.domain.model.*
import kz.kkm.tax.*
import kz.kkm.ui.main.formatTenge
import kz.kkm.ui.theme.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

// ════════════════════════════════════════════
//  SHARED VIEW MODEL  (period-scoped)
// ════════════════════════════════════════════

data class Tax910UiState(
    val period: TaxPeriodEntity? = null,
    val income1H: BigDecimal = BigDecimal.ZERO,
    val income2H: BigDecimal = BigDecimal.ZERO,
    val employees: List<EmployeeEntity> = emptyList(),
    val payrollEntries: List<PayrollEntryEntity> = emptyList(),
    val summary: Form910Summary? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val submittedStatus: DeclarationStatus? = null
)

@HiltViewModel
class Tax910ViewModel @Inject constructor(
    private val taxPeriodDao: TaxPeriodDao,
    private val employeeDao: EmployeeDao,
    private val receiptRepo: ReceiptRepository,
    private val taxCalc: TaxCalculator,
    private val xmlBuilder: XmlFormBuilder,
    private val isnaApi: IsnaApiService,
    private val settingsRepo: SettingsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val periodId = savedStateHandle.get<Long>("periodId") ?: 0L
    private val _state = MutableStateFlow(Tax910UiState())
    val state = _state.asStateFlow()

    init { loadPeriod() }

    private fun loadPeriod() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val year = java.time.LocalDate.now().year
            val half = if (java.time.LocalDate.now().monthValue <= 6) 1 else 2

            // Get or create period
            var period = if (periodId > 0) taxPeriodDao.getById(periodId)
                         else taxPeriodDao.getByYearHalf(year, half)
            if (period == null) {
                val id = taxPeriodDao.insert(TaxPeriodEntity(year = year, half = half))
                period = taxPeriodDao.getById(id)
            }

            // Load income from KKM
            val (from, to) = halfDateRange(period!!.year, period.half)
            val income = receiptRepo.getIncomeForPeriod(from, to)
            val income1H = if (period.half == 1) income else BigDecimal.ZERO
            val income2H = if (period.half == 2) income else BigDecimal.ZERO

            // Update period income
            taxPeriodDao.update(period.copy(incomeTotal = income))

            // Load employees + payroll entries
            val employees = employeeDao.getAll().filter { it.isActive }
            val payrollEntries = taxPeriodDao.getPayrollEntries(period.id)

            _state.update {
                it.copy(
                    period = period.copy(incomeTotal = income),
                    income1H = income1H,
                    income2H = income2H,
                    employees = employees,
                    payrollEntries = payrollEntries,
                    isLoading = false
                )
            }
            recalcSummary()
        }
    }

    fun addEmployee(name: String, iin: String, monthlyGross: BigDecimal,
                    monthsWorked: Int, isPensioner: Boolean, isCivilContract: Boolean) {
        viewModelScope.launch {
            val period = _state.value.period ?: return@launch
            val emp = EmployeeEntity(
                iinEncrypted = iin, // TODO: encrypt with AES
                fullName = name,
                isPensioner = isPensioner,
                isCivilContract = isCivilContract
            )
            val empId = employeeDao.insert(emp)
            val employee = Employee(id = empId, iin = iin, fullName = name,
                isPensioner = isPensioner, isCivilContract = isCivilContract)
            val result = taxCalc.calculateForEmployee(employee, monthlyGross, monthsWorked)
            val entry = PayrollEntryEntity(
                periodId = period.id, employeeId = empId, employeeName = name,
                monthsWorked = monthsWorked, grossIncome = result.grossIncome,
                opv = result.opv, osms = result.osms, ipn = result.ipn,
                so = result.so, vosms = result.vosms, sn = result.sn, netIncome = result.netIncome
            )
            taxPeriodDao.insertPayrollEntry(entry)
            loadPeriod()
        }
    }

    fun removeEmployee(entryId: Long) {
        viewModelScope.launch {
            // Remove entry from list
            val entries = _state.value.payrollEntries.filter { it.id != entryId }
            _state.update { it.copy(payrollEntries = entries) }
            recalcSummary()
        }
    }

    private fun recalcSummary() {
        val st = _state.value
        val entries = st.payrollEntries.map { e ->
            PayrollEntry(
                id = e.id, periodId = e.periodId, employeeId = e.employeeId,
                employeeName = e.employeeName, monthsWorked = e.monthsWorked,
                grossIncome = e.grossIncome, opv = e.opv, osms = e.osms,
                ipn = e.ipn, so = e.so, vosms = e.vosms, sn = e.sn, netIncome = e.netIncome
            )
        }
        val summary = taxCalc.build910Summary(st.income1H, st.income2H, entries)
        _state.update { it.copy(summary = summary) }
    }

    fun signAndSubmit() {
        viewModelScope.launch {
            val st = _state.value
            val period = st.period ?: return@launch
            val summary = st.summary ?: return@launch
            val org = settingsRepo.getOrganization()
            val isnaToken = settingsRepo.getIsnaToken()

            _state.update { it.copy(isLoading = true) }

            try {
                val entries = st.payrollEntries.map { e ->
                    PayrollEntry(e.id, e.periodId, e.employeeId, e.employeeName,
                        e.monthsWorked, e.grossIncome, e.opv, e.osms, e.ipn, e.so, e.vosms, e.sn, e.netIncome)
                }

                // Build XML
                val xml = xmlBuilder.build910Xml(org, period.year, period.half, summary, entries)
                val xmlHash = xmlBuilder.sha256(xml)

                // Save XML to file
                val xmlFile = xmlBuilder.saveToFile(
                    xml,
                    java.io.File(android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOCUMENTS), "kkm_910"),
                    period.year, period.half
                )

                // Sign XML (cloud NCA RK - simplified, real impl uses NCALayer SDK)
                val signature = "PLACEHOLDER_GOST_SIGNATURE" // TODO: NCA RK SDK
                val certificate = "PLACEHOLDER_CERTIFICATE"  // TODO: NCA RK SDK

                // Update DB
                taxPeriodDao.update(period.copy(
                    xmlHash = xmlHash,
                    xmlFilePath = xmlFile.absolutePath,
                    status = DeclarationStatus.SIGNED
                ))

                // Send to ISNA
                val formCode   = "910.00".toRequestBody("text/plain".toMediaType())
                val yearBody   = period.year.toString().toRequestBody("text/plain".toMediaType())
                val halfBody   = period.half.toString().toRequestBody("text/plain".toMediaType())
                val binBody    = org.bin.toRequestBody("text/plain".toMediaType())
                val sigType    = "GOST".toRequestBody("text/plain".toMediaType())
                val filePart   = MultipartBody.Part.createFormData(
                    "file", xmlFile.name, xmlFile.asRequestBody("application/xml".toMediaType())
                )

                val response = isnaApi.submitDeclaration(
                    authorization = "Bearer $isnaToken",
                    signature = signature,
                    certificate = certificate,
                    formCode = formCode,
                    periodYear = yearBody,
                    periodHalf = halfBody,
                    bin = binBody,
                    signatureType = sigType,
                    file = filePart
                )

                if (response.isSuccessful) {
                    val body = response.body()
                    val status = when (body?.status) {
                        "ACCEPTED"  -> DeclarationStatus.ACCEPTED
                        "PROCESSED" -> DeclarationStatus.PROCESSED
                        else        -> DeclarationStatus.FAILED
                    }
                    taxPeriodDao.update(period.copy(
                        status = status,
                        ticketId = body?.ticketId,
                        submittedAt = LocalDateTime.now()
                    ))
                    taxPeriodDao.insertAttempt(DeclarationAttemptEntity(
                        periodId = period.id, httpStatus = response.code(),
                        responseBody = body.toString(), success = true
                    ))
                    _state.update { it.copy(submittedStatus = status, isLoading = false) }
                } else {
                    taxPeriodDao.update(period.copy(status = DeclarationStatus.FAILED))
                    taxPeriodDao.insertAttempt(DeclarationAttemptEntity(
                        periodId = period.id, httpStatus = response.code(), success = false
                    ))
                    _state.update { it.copy(error = "Ошибка ИСНА: ${response.code()}", isLoading = false) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Ошибка: ${e.message}", isLoading = false) }
            }
        }
    }

    private fun halfDateRange(year: Int, half: Int): Pair<LocalDateTime, LocalDateTime> {
        val from = if (half == 1) LocalDateTime.of(year, 1, 1, 0, 0)
                   else LocalDateTime.of(year, 7, 1, 0, 0)
        val to   = if (half == 1) LocalDateTime.of(year, 6, 30, 23, 59, 59)
                   else LocalDateTime.of(year, 12, 31, 23, 59, 59)
        return from to to
    }
}

// ════════════════════════════════════════════
//  LIST / HISTORY SCREEN
// ════════════════════════════════════════════

@HiltViewModel
class Tax910ListViewModel @Inject constructor(
    private val taxPeriodDao: TaxPeriodDao,
    private val receiptRepo: ReceiptRepository
) : ViewModel() {
    val periods = taxPeriodDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun createPeriod(year: Int, half: Int, callback: (Long) -> Unit) {
        viewModelScope.launch {
            val existing = taxPeriodDao.getByYearHalf(year, half)
            val id = existing?.id ?: taxPeriodDao.insert(TaxPeriodEntity(year = year, half = half))
            callback(id)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Tax910ListScreen(
    onStartNew: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: Tax910ListViewModel = hiltViewModel()
) {
    val periods by viewModel.periods.collectAsState()
    val currentYear = java.time.LocalDate.now().year
    val currentHalf = if (java.time.LocalDate.now().monthValue <= 6) 1 else 2

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Налоговая отчётность") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = KkmBlue,
                    titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.createPeriod(currentYear, currentHalf) { onStartNew(it) } },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Подать 910.00") },
                containerColor = KkmBlue,
                contentColor = Color.White
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = KkmBlue.copy(0.08f))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Форма 910.00 — Упрощённая декларация",
                        fontWeight = FontWeight.Bold)
                    Text("Текущий период: ${if (currentHalf==1) "I" else "II"} полугодие $currentYear",
                        color = Color.Gray, fontSize = 13.sp)
                    Text("Срок подачи: до 15-го числа 2-го месяца после окончания",
                        color = Color.Gray, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("История деклараций", style = MaterialTheme.typography.titleMedium)

            if (periods.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Деклараций нет", color = Color.Gray)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(periods) { period ->
                        Card(
                            onClick = { onStartNew(period.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${if (period.half==1)"I" else "II"} полугодие ${period.year}",
                                        fontWeight = FontWeight.Bold)
                                    Text("Доход: ${formatTenge(period.incomeTotal.toLong())}",
                                        fontSize = 13.sp, color = Color.Gray)
                                    period.submittedAt?.let {
                                        Text("Подана: ${it.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))}",
                                            fontSize = 12.sp, color = Color.Gray)
                                    }
                                }
                                DeclarationStatusChip(period.status)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeclarationStatusChip(status: DeclarationStatus) {
    val (label, color) = when (status) {
        DeclarationStatus.DRAFT     -> "Черновик" to Color.Gray
        DeclarationStatus.SIGNED    -> "Подписана" to KkmAmber
        DeclarationStatus.SENDING   -> "Отправка..." to KkmAmber
        DeclarationStatus.ACCEPTED  -> "Принята" to KkmBlue
        DeclarationStatus.PROCESSED -> "Зарегистрирована" to KkmGreen
        DeclarationStatus.REJECTED  -> "Отклонена" to KkmRed
        DeclarationStatus.FAILED    -> "Ошибка" to KkmRed
        DeclarationStatus.CANCELLED -> "Отозвана" to Color.Gray
    }
    Surface(color = color.copy(0.15f), shape = RoundedCornerShape(8.dp)) {
        Text(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = color, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

// ════════════════════════════════════════════
//  STEP 1 — Income Summary
// ════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Tax910Step1Screen(periodId: Long, onNext: (Long) -> Unit, onBack: () -> Unit,
    viewModel: Tax910ViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Scaffold(topBar = {
        Tax910StepAppBar(title = "Шаг 1 из 4 — Доходы", step = 1, onBack = onBack)
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state.isLoading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Scaffold
            }
            val period = state.period

            InfoCard(
                "Данные из ККМ",
                "Доход рассчитан автоматически по Z-отчётам и фискальным чекам. Редактирование не требуется."
            )

            // Period info
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Налоговый период")
                        Text("${if ((period?.half ?: 1)==1) "I" else "II"} полугодие ${period?.year ?: ""}",
                            fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    TaxSummaryRow("Доход I полугодие (910.00.001)", state.income1H)
                    TaxSummaryRow("Доход II полугодие (910.00.002)", state.income2H)
                    Divider(Modifier.padding(vertical = 8.dp))
                    TaxSummaryRow("Совокупный доход (910.00.003)", state.income1H + state.income2H, bold = true)
                    Divider(Modifier.padding(vertical = 8.dp))
                    state.summary?.let {
                        TaxSummaryRow("Исчисленный налог 3% (910.00.004)", it.f004, color = KkmRed)
                        TaxSummaryRow("  в т.ч. ИПН 1,5%", it.f004_1)
                        TaxSummaryRow("  в т.ч. Соц. налог 1,5%", it.f004_2)
                    }
                }
            }

            Button(onClick = { state.period?.let { onNext(it.id) } },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(14.dp)) {
                Text("Далее — Работники"); Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.ArrowForward, null)
            }
        }
    }
}

// ════════════════════════════════════════════
//  STEP 2 — Employees & Payroll
// ════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Tax910Step2Screen(periodId: Long, onNext: (Long) -> Unit, onBack: () -> Unit,
    viewModel: Tax910ViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        Tax910StepAppBar("Шаг 2 из 4 — Работники", 2, onBack)
    }, floatingActionButton = {
        FloatingActionButton(onClick = { showAddDialog = true }, containerColor = KkmBlue) {
            Icon(Icons.Default.PersonAdd, null, tint = Color.White)
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.payrollEntries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PeopleAlt, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                        Text("Нет наёмных работников", color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
                        Text("Нажмите + для добавления", color = Color.Gray, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)) {
                    items(state.payrollEntries) { entry ->
                        EmployeePayrollCard(entry = entry,
                            onRemove = { viewModel.removeEmployee(entry.id) })
                    }
                }
            }
            // Totals footer
            if (state.payrollEntries.isNotEmpty()) {
                state.summary?.let { s ->
                    Card(Modifier.fillMaxWidth().padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = KkmBlue.copy(0.07f))) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Итого по работникам:", fontWeight = FontWeight.Bold)
                            TaxSummaryRow("ИПН (удержано)", s.f006)
                            TaxSummaryRow("Соц. налог (работодатель)", s.f007)
                            TaxSummaryRow("ОПВ", s.f008)
                            TaxSummaryRow("ООСМС + ВОСМС", s.f009 + s.f011)
                            TaxSummaryRow("СО", s.f010)
                        }
                    }
                }
            }
            Button(onClick = { state.period?.let { onNext(it.id) } },
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp),
                shape = RoundedCornerShape(14.dp)) {
                Text("Далее — Сводка 910.00"); Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.ArrowForward, null)
            }
        }
    }

    if (showAddDialog) {
        AddEmployeeDialog(
            onConfirm = { name, iin, gross, months, isPensioner, isGph ->
                viewModel.addEmployee(name, iin, gross, months, isPensioner, isGph)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

@Composable
private fun EmployeePayrollCard(entry: PayrollEntryEntity, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(entry.employeeName, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, null, tint = KkmRed, modifier = Modifier.size(18.dp))
                }
            }
            Text("Доход: ${formatTenge(entry.grossIncome.toLong())} (${entry.monthsWorked} мес.)",
                fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    SmallTaxRow("ОПВ",   entry.opv)
                    SmallTaxRow("ООСМС", entry.osms)
                    SmallTaxRow("ИПН",   entry.ipn)
                }
                Column {
                    SmallTaxRow("СО",     entry.so)
                    SmallTaxRow("ВОСМС",  entry.vosms)
                    SmallTaxRow("Соц.Н", entry.sn)
                }
            }
            Divider(Modifier.padding(vertical = 4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("На руки:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(formatTenge(entry.netIncome.toLong()), fontWeight = FontWeight.Bold, color = KkmGreen)
            }
        }
    }
}

@Composable
private fun SmallTaxRow(label: String, amount: BigDecimal) {
    Row {
        Text("$label: ", fontSize = 11.sp, color = Color.Gray)
        Text(formatTenge(amount.toLong()), fontSize = 11.sp)
    }
}

// ════════════════════════════════════════════
//  STEP 3 — Summary 910.00
// ════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Tax910Step3Screen(periodId: Long, onNext: (Long) -> Unit, onBack: () -> Unit,
    viewModel: Tax910ViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Scaffold(topBar = { Tax910StepAppBar("Шаг 3 из 4 — Сводка 910.00", 3, onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.summary?.let { s ->
                SummarySection("Доходы предпринимателя") {
                    TaxSummaryRow("910.00.001 — Доход I полугодие", s.f001)
                    TaxSummaryRow("910.00.002 — Доход II полугодие", s.f002)
                    TaxSummaryRow("910.00.003 — Совокупный доход", s.f003, bold = true)
                    TaxSummaryRow("910.00.004 — Налог 3%", s.f004, color = KkmRed)
                    TaxSummaryRow("  ИПН предпринимателя (1,5%)", s.f004_1)
                    TaxSummaryRow("  Соц. налог предпринимателя (1,5%)", s.f004_2)
                }
                if (s.f005.compareTo(BigDecimal.ZERO) != 0) {
                    SummarySection("Работники") {
                        TaxSummaryRow("910.00.005 — Доход работников", s.f005)
                        TaxSummaryRow("910.00.006 — ИПН работников", s.f006, color = KkmRed)
                        TaxSummaryRow("910.00.007 — Соц. налог (раб-дель)", s.f007, color = KkmRed)
                        TaxSummaryRow("910.00.008 — ОПВ", s.f008)
                        TaxSummaryRow("910.00.009 — ООСМС", s.f009)
                        TaxSummaryRow("910.00.010 — СО", s.f010)
                        TaxSummaryRow("910.00.011 — ВОСМС", s.f011)
                    }
                }
                // Payment summary
                Card(Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = KkmRed.copy(0.07f))) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Итого к уплате", fontWeight = FontWeight.Bold, color = KkmRed)
                        Spacer(Modifier.height(4.dp))
                        TaxSummaryRow("ИПН предпринимателя", s.f004_1)
                        TaxSummaryRow("Социальный налог", s.f004_2 + s.f007)
                        TaxSummaryRow("ОПВ работников", s.f008)
                        TaxSummaryRow("ООСМС + ВОСМС", s.f009 + s.f011)
                        TaxSummaryRow("Социальные отчисления (СО)", s.f010)
                        Divider(Modifier.padding(vertical = 4.dp))
                        TaxSummaryRow("ВСЕГО", s.f004 + s.f007 + s.f008 + s.f009 + s.f010 + s.f011,
                            bold = true, color = KkmRed, fontSize = 16)
                    }
                }
            } ?: CircularProgressIndicator()

            Button(onClick = { state.period?.let { onNext(it.id) } },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(14.dp)) {
                Text("Далее — Подписание и отправка"); Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.ArrowForward, null)
            }
        }
    }
}

// ════════════════════════════════════════════
//  STEP 4 — Sign & Submit
// ════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Tax910Step4Screen(periodId: Long, onSubmitted: () -> Unit, onBack: () -> Unit,
    viewModel: Tax910ViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.submittedStatus) {
        if (state.submittedStatus == DeclarationStatus.ACCEPTED ||
            state.submittedStatus == DeclarationStatus.PROCESSED) {
            kotlinx.coroutines.delay(2000); onSubmitted()
        }
    }

    Scaffold(topBar = { Tax910StepAppBar("Шаг 4 из 4 — Подписание", 4, onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {

            Icon(Icons.Default.GppGood, null, tint = KkmBlue, modifier = Modifier.size(80.dp))
            Text("Готово к подписанию", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Нажмите кнопку ниже для подписания декларации облачной ЭЦП НУЦ РК и отправки в ИСНА (КГД).",
                textAlign = TextAlign.Center, color = Color.Gray)

            state.period?.let { p ->
                InfoCard("Декларация", "${if (p.half==1)"I" else "II"} полугодие ${p.year}\n" +
                    "Доход: ${formatTenge(p.incomeTotal.toLong())}\n" +
                    "Налог: ${state.summary?.let { formatTenge(it.f004.toLong()) } ?: "—"}")
            }

            InfoCard("ЭЦП", "Будет использована облачная ЭЦП НУЦ РК.\n" +
                "Стандарт подписи: XAdES-BES / GOST R 34.10-2012")

            state.error?.let {
                Card(colors = CardDefaults.cardColors(containerColor = KkmRed.copy(0.1f)),
                    modifier = Modifier.fillMaxWidth()) {
                    Text(it, modifier = Modifier.padding(12.dp), color = KkmRed)
                }
            }

            state.submittedStatus?.let { status ->
                Card(colors = CardDefaults.cardColors(containerColor = KkmGreen.copy(0.1f)),
                    modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = KkmGreen)
                        Spacer(Modifier.width(8.dp))
                        Text("Статус: ${status.name}", color = KkmGreen, fontWeight = FontWeight.Bold)
                    }
                }
                return@Scaffold
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = viewModel::signAndSubmit,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = KkmBlue)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Подписываю и отправляю...")
                } else {
                    Icon(Icons.Default.Send, null)
                    Spacer(Modifier.width(8.dp))
                    Text("ПОДПИСАТЬ ЭЦП И ОТПРАВИТЬ В ИСНА", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ════════════════════════════════════════════
//  Shared UI helpers
// ════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Tax910StepAppBar(title: String, step: Int, onBack: () -> Unit) {
    TopAppBar(
        title = {
            Column {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                LinearProgressIndicator(
                    progress = step / 4f,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(0.3f)
                )
            }
        },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = KkmBlue,
            titleContentColor = Color.White, navigationIconContentColor = Color.White)
    )
}

@Composable
private fun TaxSummaryRow(
    label: String, amount: BigDecimal,
    bold: Boolean = false, color: Color = Color.Unspecified, fontSize: Int = 14
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = fontSize.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f), maxLines = 2)
        Text(formatTenge(amount.toLong()), fontSize = fontSize.sp,
            fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color)
    }
}

@Composable
private fun SummarySection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                color = KkmBlue, modifier = Modifier.padding(bottom = 8.dp))
            content()
        }
    }
}

@Composable
private fun InfoCard(title: String, text: String) {
    Card(Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = KkmBlue)
            Spacer(Modifier.height(4.dp))
            Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEmployeeDialog(
    onConfirm: (String, String, BigDecimal, Int, Boolean, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var name    by remember { mutableStateOf("") }
    var iin     by remember { mutableStateOf("") }
    var gross   by remember { mutableStateOf("") }
    var months  by remember { mutableStateOf("6") }
    var isPens  by remember { mutableStateOf(false) }
    var isGph   by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить работника") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("ФИО*") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = iin, onValueChange = { iin = it },
                    label = { Text("ИИН (12 цифр)*") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = gross, onValueChange = { gross = it },
                    label = { Text("Оклад в месяц, ₸*") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = months, onValueChange = { months = it },
                    label = { Text("Кол-во месяцев в периоде (1-6)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isPens, onCheckedChange = { isPens = it })
                    Text("Пенсионер (нет ОПВ)")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isGph, onCheckedChange = { isGph = it })
                    Text("Договор ГПХ (нет СО/СН)")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val g = gross.toBigDecimalOrNull() ?: return@TextButton
                val m = months.toIntOrNull()?.coerceIn(1, 6) ?: 6
                if (name.isNotBlank() && iin.length == 12) onConfirm(name, iin, g, m, isPens, isGph)
            }) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
