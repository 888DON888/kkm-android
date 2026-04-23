package kz.kkm.ui.receipt

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kz.kkm.data.local.dao.ReceiptDao
import kz.kkm.data.local.entity.ReceiptEntity
import kz.kkm.domain.model.Receipt
import kz.kkm.domain.model.ReceiptItem
import kz.kkm.domain.model.VatRate
import kz.kkm.fiscal.QrCodeGenerator
import kz.kkm.ui.main.formatTenge
import kz.kkm.ui.theme.KkmBlue
import kz.kkm.ui.theme.KkmGreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class ReceiptDoneViewModel @Inject constructor(
    private val receiptDao: ReceiptDao
) : ViewModel() {

    private val _receipt = MutableStateFlow<Receipt?>(null)
    val receipt = _receipt.asStateFlow()
    private val _qrBitmap = MutableStateFlow<Bitmap?>(null)
    val qrBitmap = _qrBitmap.asStateFlow()

    fun load(receiptId: Long) {
        viewModelScope.launch {
            val entity = receiptDao.getById(receiptId) ?: return@launch
            val items = receiptDao.getItemsForReceipt(receiptId).map { it.toDomain() }
            val r = entity.toDomain(items)
            _receipt.value = r
            // Generate QR
            val url = QrCodeGenerator.buildUrl(r)
            _qrBitmap.value = QrCodeGenerator.generateBitmap(url, 400)
        }
    }

    private fun kz.kkm.data.local.entity.ReceiptItemEntity.toDomain() = ReceiptItem(
        id = id, name = name, barcode = barcode, unit = unit,
        price = price, quantity = quantity, discount = discount, vatRate = vatRate
    )

    private fun ReceiptEntity.toDomain(items: List<ReceiptItem>) = Receipt(
        id = id, uuid = uuid, shiftId = shiftId, shiftNumber = shiftNumber,
        receiptNumber = receiptNumber, type = type, items = items,
        paymentType = paymentType, cashAmount = cashAmount, cardAmount = cardAmount,
        totalAmount = totalAmount, vatTotal = vatTotal, change = change,
        fiscalSign = fiscalSign, fiscalSignNum = fiscalSignNum,
        ofdStatus = ofdStatus, ofdTicketId = ofdTicketId, createdAt = createdAt,
        sellerBin = sellerBin, sellerName = sellerName, sellerAddress = sellerAddress
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptDoneScreen(
    receiptId: Long,
    onNewReceipt: () -> Unit,
    onBack: () -> Unit,
    viewModel: ReceiptDoneViewModel = hiltViewModel()
) {
    val receipt by viewModel.receipt.collectAsState()
    val qrBitmap by viewModel.qrBitmap.collectAsState()

    LaunchedEffect(receiptId) { viewModel.load(receiptId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Чек №${receipt?.receiptNumber ?: ""}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = KkmGreen,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        if (receipt == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        val r = receipt!!

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Success indicator
            Icon(Icons.Default.CheckCircle, null,
                tint = KkmGreen, modifier = Modifier.size(64.dp))
            Text("Чек пробит!", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                color = KkmGreen, modifier = Modifier.padding(vertical = 8.dp))

            // Receipt card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header
                    Text(r.sellerName, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                        textAlign = TextAlign.Center)
                    Text(r.sellerBin, fontSize = 12.sp, color = Color.Gray)
                    Text(r.sellerAddress, fontSize = 12.sp, color = Color.Gray,
                        textAlign = TextAlign.Center)
                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    // Date/time and receipt number
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(r.createdAt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")),
                            fontSize = 12.sp)
                        Text("Чек №${r.receiptNumber}", fontSize = 12.sp)
                    }
                    Text("Смена №${r.shiftNumber}", fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth())

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    // Items
                    r.items.forEach { item ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, fontSize = 13.sp)
                                Text("${item.quantity} ${item.unit} × ${formatTenge(item.price.toLong())}",
                                    fontSize = 11.sp, color = Color.Gray)
                            }
                            Text(formatTenge(item.subtotal.toLong()),
                                fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    // Totals
                    if (r.vatTotal.compareTo(java.math.BigDecimal.ZERO) != 0) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("НДС 12%:", fontSize = 12.sp, color = Color.Gray)
                            Text(formatTenge(r.vatTotal.toLong()), fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("ИТОГО:", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(formatTenge(r.totalAmount.toLong()),
                            fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = KkmBlue)
                    }

                    Spacer(Modifier.height(4.dp))
                    // Payment
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Оплата:", fontSize = 12.sp, color = Color.Gray)
                        Text(r.paymentType.let {
                            when (it) {
                                kz.kkm.domain.model.PaymentType.CASH  -> "Наличные"
                                kz.kkm.domain.model.PaymentType.CARD  -> "Карта"
                                kz.kkm.domain.model.PaymentType.MIXED -> "Смешанная"
                            }
                        }, fontSize = 12.sp)
                    }
                    if (r.change.compareTo(java.math.BigDecimal.ZERO) != 0) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Сдача:", fontSize = 12.sp, color = Color.Gray)
                            Text(formatTenge(r.change.toLong()), fontSize = 12.sp)
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    // Fiscal info
                    Text("ФП: ${r.fiscalSign.take(16)}...", fontSize = 10.sp, color = Color.Gray)
                    Text("ФФД: 2.0.3", fontSize = 10.sp, color = Color.Gray)

                    // QR Code
                    qrBitmap?.let { bmp ->
                        Spacer(Modifier.height(12.dp))
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "QR-код чека",
                            modifier = Modifier.size(160.dp)
                        )
                        Text("Проверить чек на cabinet.salyk.kz",
                            fontSize = 10.sp, color = Color.Gray, textAlign = TextAlign.Center)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Actions
            Button(
                onClick = onNewReceipt,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Новый чек", fontWeight = FontWeight.Bold)
            }
        }
    }
}
