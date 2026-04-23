package kz.kkm.ui.receipt

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kz.kkm.data.repository.ReceiptRepository
import kz.kkm.domain.model.*
import kz.kkm.ui.main.MainViewModel
import kz.kkm.ui.main.formatTenge
import kz.kkm.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

// ─────────────────── ViewModel ─────────────────────────────

data class PaymentState(
    val selectedType: PaymentType = PaymentType.CASH,
    val cashEntered: String = "",
    val isProcessing: Boolean = false,
    val error: String? = null,
    val receiptId: Long? = null
)

@HiltViewModel
class PaymentViewModel @Inject constructor(
    private val receiptRepo: ReceiptRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PaymentState())
    val state = _state.asStateFlow()

    fun selectPaymentType(type: PaymentType) = _state.update { it.copy(selectedType = type) }
    fun onCashEntered(v: String) = _state.update { it.copy(cashEntered = v) }

    fun processPayment(
        items: List<ReceiptItem>,
        total: BigDecimal
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, error = null) }
            val st = _state.value
            val cash = st.cashEntered.toBigDecimalOrNull() ?: BigDecimal.ZERO
            val change = if (st.selectedType == PaymentType.CASH) (cash - total).max(BigDecimal.ZERO)
                         else BigDecimal.ZERO
            receiptRepo.createReceipt(
                items        = items,
                paymentType  = st.selectedType,
                cashAmount   = if (st.selectedType != PaymentType.CARD) cash.min(total + change) else BigDecimal.ZERO,
                cardAmount   = if (st.selectedType == PaymentType.CARD) total
                               else if (st.selectedType == PaymentType.MIXED) total - (cash.min(total)) else BigDecimal.ZERO,
                change       = change
            ).onSuccess { receipt ->
                _state.update { it.copy(receiptId = receipt.id, isProcessing = false) }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message, isProcessing = false) }
            }
        }
    }
}

// ─────────────────── Screen ────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(
    onPaymentComplete: (Long) -> Unit,
    onBack: () -> Unit,
    paymentVm: PaymentViewModel = hiltViewModel(),
    mainVm: MainViewModel = hiltViewModel()
) {
    val payState by paymentVm.state.collectAsState()
    val mainState by mainVm.state.collectAsState()

    LaunchedEffect(payState.receiptId) {
        payState.receiptId?.let {
            mainVm.clearCart()
            onPaymentComplete(it)
        }
    }

    val total = mainState.cartTotal
    val cashEntered = payState.cashEntered.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val change = if (payState.selectedType == PaymentType.CASH && cashEntered > total)
        (cashEntered - total).setScale(2, RoundingMode.HALF_UP) else BigDecimal.ZERO

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Оплата") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = KkmBlue,
                    titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Total
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = KkmBlue),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("К оплате", color = Color.White.copy(0.7f), fontSize = 14.sp)
                    Text(
                        formatTenge(total.toLong()),
                        color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold
                    )
                    if (mainState.cartVat > BigDecimal.ZERO) {
                        Text("в т.ч. НДС: ${formatTenge(mainState.cartVat.toLong())}",
                            color = Color.White.copy(0.6f), fontSize = 12.sp)
                    }
                }
            }

            // Payment type selector
            Text("Способ оплаты", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PaymentType.entries.forEach { type ->
                    FilterChip(
                        selected = payState.selectedType == type,
                        onClick  = { paymentVm.selectPaymentType(type) },
                        label    = { Text(type.label()) },
                        leadingIcon = { Icon(type.icon(), null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }

            // Cash input
            if (payState.selectedType != PaymentType.CARD) {
                OutlinedTextField(
                    value = payState.cashEntered,
                    onValueChange = paymentVm::onCashEntered,
                    label = { Text("Сумма наличных, ₸") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    suffix = { Text("₸") }
                )

                // Quick cash buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        total.setScale(0, RoundingMode.CEILING),
                        BigDecimal("5000"), BigDecimal("10000"), BigDecimal("20000")
                    ).forEach { amount ->
                        OutlinedButton(
                            onClick = { paymentVm.onCashEntered(amount.toPlainString()) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text(formatTenge(amount.toLong()), fontSize = 11.sp) }
                    }
                }

                if (change > BigDecimal.ZERO) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = KkmGreen.copy(0.1f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Сдача:", fontWeight = FontWeight.Bold)
                            Text(formatTenge(change.toLong()),
                                fontWeight = FontWeight.ExtraBold, color = KkmGreen, fontSize = 20.sp)
                        }
                    }
                }
            }

            payState.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.weight(1f))

            // Confirm button
            Button(
                onClick = { paymentVm.processPayment(mainState.cart.map { c ->
                    ReceiptItem(name = c.name, barcode = c.barcode, unit = c.unit,
                        price = c.price, quantity = c.quantity, discount = c.discount, vatRate = c.vatRate)
                }, total) },
                enabled = !payState.isProcessing && (
                    payState.selectedType == PaymentType.CARD ||
                    (payState.cashEntered.toBigDecimalOrNull() ?: BigDecimal.ZERO) >= total
                ),
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = KkmGreen)
            ) {
                if (payState.isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Icon(Icons.Default.CheckCircle, null)
                    Spacer(Modifier.width(8.dp))
                    Text("ПРОВЕСТИ ОПЛАТУ", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

private fun PaymentType.label() = when (this) {
    PaymentType.CASH  -> "Наличные"
    PaymentType.CARD  -> "Карта"
    PaymentType.MIXED -> "Смешанная"
}

private fun PaymentType.icon() = when (this) {
    PaymentType.CASH  -> Icons.Default.Money
    PaymentType.CARD  -> Icons.Default.CreditCard
    PaymentType.MIXED -> Icons.Default.CompareArrows
}
