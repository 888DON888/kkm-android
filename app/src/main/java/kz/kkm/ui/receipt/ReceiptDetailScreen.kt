package kz.kkm.ui.receipt

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kz.kkm.data.local.dao.ReceiptDao
import kz.kkm.data.local.entity.ReceiptEntity
import kz.kkm.data.local.entity.ReceiptItemEntity
import kz.kkm.domain.model.*
import kz.kkm.fiscal.QrCodeGenerator
import kz.kkm.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import javax.inject.Inject

// ─── ViewModel ───────────────────────────────────────────────

@HiltViewModel
class ReceiptDetailViewModel @Inject constructor(
    private val receiptDao: ReceiptDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val receiptId: Long = savedStateHandle["receiptId"] ?: 0L
    private val _state = MutableStateFlow<ReceiptDetailState>(ReceiptDetailState.Loading)
    val state = _state.asStateFlow()

    init { loadReceipt() }

    private fun loadReceipt() {
        viewModelScope.launch {
            val entity = receiptDao.getById(receiptId)
            if (entity == null) {
                _state.value = ReceiptDetailState.Error("Чек не найден")
                return@launch
            }
            val items = receiptDao.getItemsForReceipt(receiptId)
            val receipt = entity.toDomain(items)
            val qrUrl = QrCodeGenerator.buildUrl(receipt)
            val qrBitmap = try { QrCodeGenerator.generateBitmap(qrUrl) } catch (e: Exception) { null }
            _state.value = ReceiptDetailState.Success(receipt, qrBitmap, qrUrl)
        }
    }

    // Extension mappers (duplicated locally to avoid repo dependency in VM)
    private fun ReceiptEntity.toDomain(itemEntities: List<ReceiptItemEntity>) = Receipt(
        id = id, uuid = uuid, shiftId = shiftId, shiftNumber = shiftNumber,
        receiptNumber = receiptNumber, type = type,
        items = itemEntities.map {
            ReceiptItem(it.id, it.name, it.barcode, it.unit, it.price, it.quantity, it.discount, it.vatRate)
        },
        paymentType = paymentType, cashAmount = cashAmount, cardAmount = cardAmount,
        totalAmount = totalAmount, vatTotal = vatTotal, change = change,
        fiscalSign = fiscalSign, fiscalSignNum = fiscalSignNum,
        ofdStatus = ofdStatus, ofdTicketId = ofdTicketId, createdAt = createdAt,
        originalReceiptId = originalReceiptId,
        sellerBin = sellerBin, sellerName = sellerName, sellerAddress = sellerAddress
    )
}

sealed class ReceiptDetailState {
    object Loading : ReceiptDetailState()
    data class Success(val receipt: Receipt, val qrBitmap: Bitmap?, val qrUrl: String) : ReceiptDetailState()
    data class Error(val message: String) : ReceiptDetailState()
}

// ─── Screen ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptDetailScreen(receiptId: Long, onBack: () -> Unit, vm: ReceiptDetailViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Фискальный чек") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        when (val s = state) {
            is ReceiptDetailState.Loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            is ReceiptDetailState.Error -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { Text(s.message, color = MaterialTheme.colorScheme.error) }

            is ReceiptDetailState.Success -> ReceiptContent(
                receipt = s.receipt,
                qrBitmap = s.qrBitmap,
                padding = padding
            )
        }
    }
}

@Composable
fun ReceiptContent(receipt: Receipt, qrBitmap: Bitmap?, padding: PaddingValues) {
    val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
    val monospace = FontFamily.Monospace

    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 12.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))

        // Receipt paper look
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(
                Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Text(receipt.sellerName, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = 15.sp)
                Text(receipt.sellerBin, fontSize = 11.sp, color = KkmGray, textAlign = TextAlign.Center)
                Text(receipt.sellerAddress, fontSize = 11.sp, color = KkmGray, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))

                HorizontalDivider()

                // Receipt type badge
                val (label, badgeColor) = when (receipt.type) {
                    ReceiptType.SALE   -> "КАССОВЫЙ ЧЕК" to KkmGreen
                    ReceiptType.RETURN -> "ВОЗВРАТ" to KkmAmber
                    ReceiptType.CANCEL -> "АННУЛИРОВАНИЕ" to KkmRed
                }
                Surface(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = badgeColor,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(label, color = Color.White, fontWeight = FontWeight.Bold,
                        fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                }

                // Meta
                ReceiptRow("Дата:", receipt.createdAt.format(fmt))
                ReceiptRow("Чек №:", "${receipt.receiptNumber}")
                ReceiptRow("Смена №:", "${receipt.shiftNumber}")
                ReceiptRow("Оплата:", when (receipt.paymentType) {
                    PaymentType.CASH  -> "Наличные"
                    PaymentType.CARD  -> "Безналичная"
                    PaymentType.MIXED -> "Смешанная"
                })

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                // Items
                receipt.items.forEach { item ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "${item.quantity} ${item.unit} × ${fmtMoney(item.price)} ₸",
                                fontSize = 11.sp, color = KkmGray, fontFamily = monospace
                            )
                            if (item.vatRate != VatRate.NONE) {
                                Text("НДС: ${fmtMoney(item.vatAmount)} ₸",
                                    fontSize = 10.sp, color = KkmGray)
                            }
                        }
                        Text(
                            "${fmtMoney(item.subtotal)} ₸",
                            fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }

                HorizontalDivider(Modifier.padding(vertical = 4.dp))

                // Totals
                if (receipt.vatTotal > BigDecimal.ZERO) {
                    ReceiptRow("В т.ч. НДС (12%):", "${fmtMoney(receipt.vatTotal)} ₸")
                }
                if (receipt.cashAmount > BigDecimal.ZERO) {
                    ReceiptRow("Наличные:", "${fmtMoney(receipt.cashAmount)} ₸")
                }
                if (receipt.cardAmount > BigDecimal.ZERO) {
                    ReceiptRow("Безналичная:", "${fmtMoney(receipt.cardAmount)} ₸")
                }
                if (receipt.change > BigDecimal.ZERO) {
                    ReceiptRow("Сдача:", "${fmtMoney(receipt.change)} ₸")
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("ИТОГО:", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("${fmtMoney(receipt.totalAmount)} ₸", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                // Fiscal info
                Text("Фискальный признак:", fontSize = 10.sp, color = KkmGray)
                Text(
                    receipt.fiscalSign.take(24) + "...",
                    fontSize = 9.sp, color = KkmGray, fontFamily = monospace,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))

                // OFD status
                val (ofdLabel, ofdColor) = when (receipt.ofdStatus) {
                    OfdStatus.CONFIRMED -> "✓ Передан в ОФД" to KkmGreen
                    OfdStatus.PENDING   -> "⏳ Ожидает передачи" to KkmAmber
                    OfdStatus.SENT      -> "↑ Отправлен" to KkmBlueLight
                    OfdStatus.FAILED    -> "✗ Ошибка передачи" to KkmRed
                }
                Text(ofdLabel, fontSize = 11.sp, color = ofdColor, fontWeight = FontWeight.SemiBold)
                receipt.ofdTicketId?.let {
                    Text("Тикет: $it", fontSize = 9.sp, color = KkmGray)
                }

                // QR Code
                qrBitmap?.let { bmp ->
                    Spacer(Modifier.height(16.dp))
                    Text("Проверьте чек на сайте КГД", fontSize = 11.sp, color = KkmGray)
                    Spacer(Modifier.height(8.dp))
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "QR-код чека",
                        modifier = Modifier
                            .size(160.dp)
                            .border(1.dp, KkmGray.copy(.3f), RoundedCornerShape(4.dp))
                            .padding(4.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("cabinet.salyk.kz", fontSize = 9.sp, color = KkmGray)
                }

                Spacer(Modifier.height(8.dp))
                Text("Версия ФФД: 2.0.3", fontSize = 9.sp, color = KkmGray)
                Text("Спасибо за покупку!", fontSize = 11.sp, color = KkmGray)
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ReceiptRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = KkmGray)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

private fun fmtMoney(bd: BigDecimal): String =
    java.text.NumberFormat.getNumberInstance(java.util.Locale("ru", "KZ")).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(bd)
