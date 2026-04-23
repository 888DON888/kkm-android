package kz.kkm.ui.returns

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kz.kkm.data.local.dao.ReceiptDao
import kz.kkm.data.repository.ReceiptRepository
import kz.kkm.domain.model.*
import kz.kkm.ui.main.formatTenge
import kz.kkm.ui.theme.KkmBlue
import kz.kkm.ui.theme.KkmGreen
import kz.kkm.ui.theme.KkmRed
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReturnsUiState(
    val searchNum: String = "",
    val foundReceipt: Receipt? = null,
    val selectedItems: Set<Int> = emptySet(),
    val isProcessing: Boolean = false,
    val error: String? = null,
    val success: Boolean = false
)

@HiltViewModel
class ReturnsViewModel @Inject constructor(
    private val receiptDao: ReceiptDao,
    private val receiptRepo: ReceiptRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ReturnsUiState())
    val state = _state.asStateFlow()

    fun onSearchNum(v: String) = _state.update { it.copy(searchNum = v) }

    fun searchReceipt() {
        viewModelScope.launch {
            val num = _state.value.searchNum.toIntOrNull() ?: return@launch
            val entity = receiptDao.getById(num.toLong())
            if (entity != null) {
                val items = receiptDao.getItemsForReceipt(entity.id).map {
                    ReceiptItem(id = it.id, name = it.name, barcode = it.barcode,
                        unit = it.unit, price = it.price, quantity = it.quantity,
                        discount = it.discount, vatRate = it.vatRate)
                }
                val r = Receipt(
                    id = entity.id, uuid = entity.uuid, shiftId = entity.shiftId,
                    shiftNumber = entity.shiftNumber, receiptNumber = entity.receiptNumber,
                    type = entity.type, items = items, paymentType = entity.paymentType,
                    cashAmount = entity.cashAmount, cardAmount = entity.cardAmount,
                    totalAmount = entity.totalAmount, vatTotal = entity.vatTotal,
                    change = entity.change, fiscalSign = entity.fiscalSign,
                    fiscalSignNum = entity.fiscalSignNum, ofdStatus = entity.ofdStatus,
                    sellerBin = entity.sellerBin, sellerName = entity.sellerName,
                    sellerAddress = entity.sellerAddress
                )
                _state.update { it.copy(foundReceipt = r, selectedItems = emptySet(), error = null) }
            } else {
                _state.update { it.copy(error = "Чек не найден", foundReceipt = null) }
            }
        }
    }

    fun toggleItem(index: Int) {
        val selected = _state.value.selectedItems.toMutableSet()
        if (selected.contains(index)) selected.remove(index) else selected.add(index)
        _state.update { it.copy(selectedItems = selected) }
    }

    fun processReturn() {
        viewModelScope.launch {
            val st = _state.value
            val original = st.foundReceipt ?: return@launch
            val returnItems = st.selectedItems.map { original.items[it] }
            if (returnItems.isEmpty()) return@launch

            _state.update { it.copy(isProcessing = true) }
            receiptRepo.createReceipt(
                items = returnItems,
                paymentType = original.paymentType,
                cashAmount = original.cashAmount,
                cardAmount = original.cardAmount,
                type = ReceiptType.RETURN,
                originalReceiptId = original.id
            ).onSuccess {
                _state.update { it.copy(success = true, isProcessing = false) }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message, isProcessing = false) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReturnsScreen(
    onReturnComplete: () -> Unit,
    onBack: () -> Unit,
    viewModel: ReturnsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.success) {
        if (state.success) { kotlinx.coroutines.delay(1500); onReturnComplete() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Возврат товара") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = KkmBlue,
                    titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // Search
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.searchNum,
                    onValueChange = viewModel::onSearchNum,
                    label = { Text("Номер чека") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                Button(onClick = viewModel::searchReceipt, modifier = Modifier.height(56.dp)) {
                    Text("Найти")
                }
            }

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            if (state.success) {
                Card(colors = CardDefaults.cardColors(containerColor = KkmGreen.copy(0.1f)),
                    modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = KkmGreen)
                        Spacer(Modifier.width(8.dp))
                        Text("Возврат оформлен!", color = KkmGreen, fontWeight = FontWeight.Bold)
                    }
                }
                return@Scaffold
            }

            state.foundReceipt?.let { receipt ->
                Text("Чек №${receipt.receiptNumber} от ${receipt.createdAt}",
                    style = MaterialTheme.typography.titleSmall)
                Text("Выберите позиции для возврата:",
                    color = Color.Gray, style = MaterialTheme.typography.bodySmall)

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(receipt.items.size) { idx ->
                        val item = receipt.items[idx]
                        val selected = state.selectedItems.contains(idx)
                        Card(
                            onClick = { viewModel.toggleItem(idx) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) KkmBlue.copy(0.1f)
                                else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = selected, onCheckedChange = { viewModel.toggleItem(idx) })
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, fontWeight = FontWeight.SemiBold)
                                    Text("${item.quantity} ${item.unit} × ${formatTenge(item.price.toLong())}",
                                        fontSize = androidx.compose.ui.unit.TextUnit.Unspecified,
                                        color = Color.Gray)
                                }
                                Text(formatTenge(item.subtotal.toLong()), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (state.selectedItems.isNotEmpty()) {
                    val returnTotal = state.selectedItems.sumOf {
                        receipt.items[it].subtotal.toLong()
                    }
                    Button(
                        onClick = viewModel::processReturn,
                        enabled = !state.isProcessing,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KkmRed)
                    ) {
                        if (state.isProcessing) CircularProgressIndicator(
                            modifier = Modifier.size(20.dp), color = Color.White)
                        else Text("Оформить возврат на ${formatTenge(returnTotal)}",
                            fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ReturnDetailScreen(receiptId: Long, onBack: () -> Unit) {
    // Placeholder - full detail view if needed
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = onBack) { Text("Назад") }
    }
}
