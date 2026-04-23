package kz.kkm.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kz.kkm.domain.model.Shift
import kz.kkm.domain.model.ShiftStatus
import kz.kkm.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToPayment: () -> Unit,
    onNavigateToReturns: () -> Unit,
    onNavigateToXReport: () -> Unit,
    onNavigateToZReport: () -> Unit,
    onNavigateToJournal: () -> Unit,
    onNavigateToCatalog: () -> Unit,
    onNavigateToTax910:  () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showDrawer by remember { mutableStateOf(false) }
    var showManualEntry by remember { mutableStateOf(false) }
    var showBarcodeScanner by remember { mutableStateOf(false) }

    // Message snackbar
    state.message?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2500)
            viewModel.clearMessage()
        }
    }

    if (state.shift == null || state.shift?.status == ShiftStatus.CLOSED) {
        ShiftClosedPlaceholder(
            isLoading = state.isShiftLoading,
            onOpenShift = viewModel::openShift
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ÐÐ°ÑÑÐ°", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "Ð¡Ð¼ÐµÐ½Ð° â${state.shift!!.shiftNumber}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { showDrawer = true }) {
                        Icon(Icons.Default.Menu, contentDescription = "ÐÐµÐ½Ñ")
                    }
                },
                actions = {
                    // OFD status indicator
                    OfdStatusBadge()
                    IconButton(onClick = { showBarcodeScanner = true }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Ð¡ÐºÐ°Ð½ÐµÑ")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = KkmBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        snackbarHost = {
            state.message?.let {
                Snackbar(modifier = Modifier.padding(16.dp)) { Text(it) }
            }
        }
    ) { padding ->
        Row(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {

            // âââ Left: Catalog / Search ââââââââââââââââââââââââââââ
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(8.dp)
            ) {
                // Search bar
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::onSearchQuery,
                    placeholder = { Text("ÐÐ¾Ð¸ÑÐº ÑÐ¾Ð²Ð°ÑÐ¾Ð² Ð¸Ð»Ð¸ ÑÑÑÐ¸ÑÐºÐ¾Ð´...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQuery("") }) {
                                Icon(Icons.Default.Clear, null)
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (state.searchResults.isNotEmpty()) {
                    // Search results
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(state.searchResults) { item ->
                            SearchResultItem(
                                name  = item.name,
                                price = item.price.toPlainString(),
                                unit  = item.unit,
                                onClick = { viewModel.addToCart(item) }
                            )
                        }
                    }
                } else {
                    // Favorites grid
                    Text("ÐÐ·Ð±ÑÐ°Ð½Ð½Ð¾Ðµ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(vertical = 4.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.favorites) { item ->
                            FavoriteItemCard(
                                name  = item.name,
                                price = formatTenge(item.price.toLong()),
                                onClick = { viewModel.addToCart(item) }
                            )
                        }
                        item {
                            AddManualCard(onClick = { showManualEntry = true })
                        }
                    }
                }
            }

            Divider(modifier = Modifier.fillMaxHeight().width(1.dp))

            // âââ Right: Cart âââââââââââââââââââââââââââââââââââââââ
            Column(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp)
            ) {
                Text("Ð§ÐµÐº", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp))

                if (state.cart.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("ÐÐ¾Ð±Ð°Ð²ÑÑÐµ ÑÐ¾Ð²Ð°ÑÑ", color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(state.cart) { index, item ->
                            CartItemRow(
                                item = item,
                                onQtyChange = { viewModel.updateQuantity(index, it) },
                                onRemove = { viewModel.removeItem(index) }
                            )
                        }
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                // Totals
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("ÐÐÐ¡:", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                    Text(formatTenge(state.cartVat.toLong()),
                        style = MaterialTheme.typography.bodyMedium)
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("ÐÐ¢ÐÐÐ:", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(formatTenge(state.cartTotal.toLong()),
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        color = KkmBlue)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Pay button
                Button(
                    onClick = onNavigateToPayment,
                    enabled = state.cart.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KkmGreen)
                ) {
                    Icon(Icons.Default.Payment, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("ÐÐÐÐÐ¢ÐÐ¢Ð¬", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                TextButton(
                    onClick = viewModel::clearCart,
                    enabled = state.cart.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("ÐÑÐ¸ÑÑÐ¸ÑÑ ÑÐµÐº", color = KkmRed)
                }
            }
        }
    }

    // Drawer / side menu
    if (showDrawer) {
        KkmDrawer(
            shift = state.shift!!,
            onClose         = { showDrawer = false },
            onReturns       = { showDrawer = false; onNavigateToReturns() },
            onXReport       = { showDrawer = false; onNavigateToXReport() },
            onZReport       = { showDrawer = false; onNavigateToZReport() },
            onJournal       = { showDrawer = false; onNavigateToJournal() },
            onCatalog       = { showDrawer = false; onNavigateToCatalog() },
            onTax910        = { showDrawer = false; onNavigateToTax910() },
            onSettings      = { showDrawer = false; onNavigateToSettings() }
        )
    }

    if (showManualEntry) {
        ManualItemDialog(
            onConfirm = { name, price, qty, vatRate ->
                viewModel.addManualItem(name, price, qty, vatRate)
                showManualEntry = false
            },
            onDismiss = { showManualEntry = false }
        )
    }
}

@Composable
private fun ShiftClosedPlaceholder(isLoading: Boolean, onOpenShift: () -> Unit) {
    Box(Modifier.fillMaxSize().background(KkmBlue), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.LockOpen, contentDescription = null,
                tint = Color.White, modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(24.dp))
            Text("Ð¡Ð¼ÐµÐ½Ð° Ð·Ð°ÐºÑÑÑÐ°", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("ÐÑÐºÑÐ¾Ð¹ÑÐµ ÑÐ¼ÐµÐ½Ñ Ð´Ð»Ñ Ð½Ð°ÑÐ°Ð»Ð° ÑÐ°Ð±Ð¾ÑÑ",
                color = Color.White.copy(0.7f), fontSize = 14.sp)
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onOpenShift,
                enabled = !isLoading,
                modifier = Modifier.width(200.dp).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = KkmBlue)
                else Text("ÐÑÐºÑÑÑÑ ÑÐ¼ÐµÐ½Ñ", color = KkmBlue, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FavoriteItemCard(name: String, price: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.aspectRatio(1f),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(name, style = MaterialTheme.typography.bodySmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(price, style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold, color = KkmBlue)
        }
    }
}

@Composable
private fun AddManualCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.aspectRatio(1f),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Add, null, tint = KkmBlue, modifier = Modifier.size(28.dp))
                Text("ÐÑÑÑÐ½ÑÑ", style = MaterialTheme.typography.labelSmall, color = KkmBlue)
            }
        }
    }
}

@Composable
private fun SearchResultItem(name: String, price: String, unit: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(name, modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$price â¸/$unit",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold, color = KkmBlue)
        }
    }
}

@Composable
private fun CartItemRow(
    item: CartItem,
    onQtyChange: (java.math.BigDecimal) -> Unit,
    onRemove: () -> Unit
) {
    Card(shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.name, modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp), tint = KkmRed)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onQtyChange(item.quantity - java.math.BigDecimal.ONE) },
                        modifier = Modifier.size(32.dp)
                    ) { Icon(Icons.Default.Remove, null, modifier = Modifier.size(16.dp)) }
                    Text("${item.quantity} ${item.unit}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 4.dp))
                    IconButton(
                        onClick = { onQtyChange(item.quantity + java.math.BigDecimal.ONE) },
                        modifier = Modifier.size(32.dp)
                    ) { Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)) }
                }
                Text(formatTenge(item.subtotal.toLong()),
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun OfdStatusBadge() {
    // TODO: connect to actual OFD status flow
    Box(
        modifier = Modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(KkmGreen.copy(alpha = 0.2f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text("ÐÐ¤Ð â", fontSize = 11.sp, color = KkmGreen, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun KkmDrawer(
    shift: Shift,
    onClose: () -> Unit,
    onReturns: () -> Unit,
    onXReport: () -> Unit,
    onZReport: () -> Unit,
    onJournal: () -> Unit,
    onCatalog: () -> Unit,
    onTax910: () -> Unit,
    onSettings: () -> Unit
) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onClose) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("ÐÐµÐ½Ñ", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp))
            DrawerItem(Icons.Default.Undo, "ÐÐ¾Ð·Ð²ÑÐ°Ñ ÑÐ¾Ð²Ð°ÑÐ°", onReturns)
            DrawerItem(Icons.Default.BarChart, "X-Ð¾ÑÑÑÑ", onXReport)
            DrawerItem(Icons.Default.AssignmentTurnedIn, "ÐÐ°ÐºÑÑÑÑ ÑÐ¼ÐµÐ½Ñ (Z-Ð¾ÑÑÑÑ)", onZReport)
            DrawerItem(Icons.Default.History, "ÐÑÑÐ½Ð°Ð» Ð¾Ð¿ÐµÑÐ°ÑÐ¸Ð¹", onJournal)
            DrawerItem(Icons.Default.Inventory, "ÐÐ°ÑÐ°Ð»Ð¾Ð³ ÑÐ¾Ð²Ð°ÑÐ¾Ð²", onCatalog)
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            DrawerItem(Icons.Default.AccountBalance, "ÐÐ°Ð»Ð¾Ð³Ð¾Ð²Ð°Ñ Ð¾ÑÑÑÑÐ½Ð¾ÑÑÑ 910.00", onTax910)
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            DrawerItem(Icons.Default.Settings, "ÐÐ°ÑÑÑÐ¾Ð¹ÐºÐ¸", onSettings)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DrawerItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = KkmBlue)
        Spacer(Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualItemDialog(
    onConfirm: (String, java.math.BigDecimal, java.math.BigDecimal, kz.kkm.domain.model.VatRate) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var vatRate by remember { mutableStateOf(kz.kkm.domain.model.VatRate.NONE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ÐÐ¾Ð±Ð°Ð²Ð¸ÑÑ ÑÐ¾Ð²Ð°Ñ Ð²ÑÑÑÐ½ÑÑ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("ÐÐ°Ð¸Ð¼ÐµÐ½Ð¾Ð²Ð°Ð½Ð¸Ðµ*") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = price, onValueChange = { price = it },
                    label = { Text("Ð¦ÐµÐ½Ð°, â¸*") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = qty, onValueChange = { qty = it },
                    label = { Text("ÐÐ¾Ð»Ð¸ÑÐµÑÑÐ²Ð¾") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = vatRate == kz.kkm.domain.model.VatRate.VAT_12,
                        onCheckedChange = { checked ->
                            vatRate = if (checked) kz.kkm.domain.model.VatRate.VAT_12
                                      else kz.kkm.domain.model.VatRate.NONE
                        })
                    Text("ÐÐÐ¡ 12%")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val p = price.toBigDecimalOrNull() ?: return@TextButton
                    val q = qty.toBigDecimalOrNull() ?: java.math.BigDecimal.ONE
                    if (name.isNotBlank()) onConfirm(name, p, q, vatRate)
                }
            ) { Text("ÐÐ¾Ð±Ð°Ð²Ð¸ÑÑ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ÐÑÐ¼ÐµÐ½Ð°") }
        }
    )
}

fun formatTenge(amount: Long): String {
    return NumberFormat.getNumberInstance(Locale("ru", "KZ")).format(amount) + " â¸"
}
