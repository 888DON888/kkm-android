package kz.kkm.ui.catalog

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
import kz.kkm.data.local.dao.CatalogDao
import kz.kkm.data.local.entity.CatalogItemEntity
import kz.kkm.domain.model.VatRate
import kz.kkm.ui.main.formatTenge
import kz.kkm.ui.theme.KkmBlue
import kz.kkm.ui.theme.KkmGreen
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val catalogDao: CatalogDao
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _all = catalogDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val items: StateFlow<List<CatalogItemEntity>> = _query
        .debounce(200)
        .combine(_all) { q, list ->
            if (q.isBlank()) list
            else list.filter {
                it.name.contains(q, ignoreCase = true) ||
                it.barcode?.contains(q) == true
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun onQuery(q: String) { _query.value = q }

    fun addItem(name: String, barcode: String?, price: BigDecimal, unit: String, vatRate: VatRate) {
        viewModelScope.launch {
            catalogDao.insert(CatalogItemEntity(name = name, barcode = barcode,
                price = price, unit = unit, vatRate = vatRate))
        }
    }

    fun toggleFavorite(item: CatalogItemEntity) {
        viewModelScope.launch {
            catalogDao.setFavorite(item.id, !item.isFavorite)
        }
    }

    fun deleteItem(item: CatalogItemEntity) {
        viewModelScope.launch { catalogDao.delete(item) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(onBack: () -> Unit, viewModel: CatalogViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsState()
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Каталог товаров") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = KkmBlue,
                    titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }, containerColor = KkmBlue) {
                Icon(Icons.Default.Add, null, tint = Color.White)
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; viewModel.onQuery(it) },
                placeholder = { Text("Поиск по названию или штрихкоду") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(12.dp)
            )

            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, fontWeight = FontWeight.SemiBold)
                                item.barcode?.let {
                                    Text(it, fontSize = 11.sp, color = Color.Gray)
                                }
                                Text("${formatTenge(item.price.toLong())} / ${item.unit}",
                                    fontSize = 13.sp, color = KkmBlue)
                                if (item.vatRate == VatRate.VAT_12) {
                                    Text("НДС 12%", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                            IconButton(onClick = { viewModel.toggleFavorite(item) }) {
                                Icon(
                                    if (item.isFavorite) Icons.Default.Star else Icons.Default.StarOutline,
                                    null, tint = if (item.isFavorite) KkmGreen else Color.Gray
                                )
                            }
                            IconButton(onClick = { viewModel.deleteItem(item) }) {
                                Icon(Icons.Default.Delete, null,
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddCatalogItemDialog(
            onConfirm = { name, barcode, price, unit, vatRate ->
                viewModel.addItem(name, barcode, price, unit, vatRate)
                showAdd = false
            },
            onDismiss = { showAdd = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCatalogItemDialog(
    onConfirm: (String, String?, BigDecimal, String, VatRate) -> Unit,
    onDismiss: () -> Unit
) {
    var name    by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var price   by remember { mutableStateOf("") }
    var unit    by remember { mutableStateOf("шт") }
    var vatRate by remember { mutableStateOf(VatRate.NONE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить товар") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Наименование*") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = barcode, onValueChange = { barcode = it },
                    label = { Text("Штрихкод (необязательно)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = price, onValueChange = { price = it },
                    label = { Text("Цена, ₸*") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = unit, onValueChange = { unit = it },
                    label = { Text("Единица измерения") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = vatRate == VatRate.VAT_12,
                        onCheckedChange = { v -> vatRate = if (v) VatRate.VAT_12 else VatRate.NONE })
                    Text("НДС 12%")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val p = price.toBigDecimalOrNull() ?: return@TextButton
                if (name.isNotBlank()) onConfirm(name, barcode.ifBlank { null }, p, unit, vatRate)
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
