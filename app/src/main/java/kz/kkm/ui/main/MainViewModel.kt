package kz.kkm.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kz.kkm.data.local.dao.CatalogDao
import kz.kkm.data.local.entity.CatalogItemEntity
import kz.kkm.data.repository.ShiftRepository
import kz.kkm.domain.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class CartItem(
    val catalogId: Long,
    val name: String,
    val barcode: String?,
    val unit: String,
    val price: BigDecimal,
    var quantity: BigDecimal,
    var discount: BigDecimal = BigDecimal.ZERO,
    val vatRate: VatRate
) {
    val subtotal: BigDecimal get() = (price * quantity - discount)
        .setScale(2, java.math.RoundingMode.HALF_UP)
    val vatAmount: BigDecimal get() = when (vatRate) {
        VatRate.VAT_12 -> subtotal * BigDecimal("0.12") / BigDecimal("1.12")
        VatRate.NONE   -> BigDecimal.ZERO
    }.setScale(2, java.math.RoundingMode.HALF_UP)
}

data class MainUiState(
    val shift: Shift? = null,
    val cart: List<CartItem> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<CatalogItemEntity> = emptyList(),
    val favorites: List<CatalogItemEntity> = emptyList(),
    val isShiftLoading: Boolean = false,
    val message: String? = null
) {
    val cartTotal: BigDecimal get() = cart.sumOf { it.subtotal }
    val cartVat: BigDecimal get() = cart.sumOf { it.vatAmount }
    val itemCount: Int get() = cart.size
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val shiftRepository: ShiftRepository,
    private val catalogDao: CatalogDao
) : ViewModel() {

    private val _state = MutableStateFlow(MainUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            shiftRepository.observeOpenShift().collect { shift ->
                _state.update { it.copy(shift = shift) }
            }
        }
        viewModelScope.launch {
            catalogDao.observeFavorites().collect { favs ->
                _state.update { it.copy(favorites = favs) }
            }
        }
    }

    // ─── Shift ───────────────────────────────────────────────

    fun openShift() {
        viewModelScope.launch {
            _state.update { it.copy(isShiftLoading = true) }
            shiftRepository.openShift()
                .onFailure { e -> _state.update { it.copy(message = "Ошибка: ${e.message}") } }
            _state.update { it.copy(isShiftLoading = false) }
        }
    }

    // ─── Search / Barcode ────────────────────────────────────

    fun onSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            _state.update { it.copy(searchResults = emptyList()) }
            return
        }
        viewModelScope.launch {
            val results = catalogDao.search(query)
            _state.update { it.copy(searchResults = results) }
        }
    }

    fun onBarcodeScanned(barcode: String) {
        viewModelScope.launch {
            val item = catalogDao.getByBarcode(barcode)
            if (item != null) addToCart(item)
            else _state.update { it.copy(message = "Товар не найден: $barcode") }
        }
    }

    // ─── Cart ─────────────────────────────────────────────────

    fun addToCart(item: CatalogItemEntity) {
        val cart = _state.value.cart.toMutableList()
        val existing = cart.indexOfFirst { it.catalogId == item.id }
        if (existing >= 0) {
            cart[existing] = cart[existing].copy(
                quantity = cart[existing].quantity + BigDecimal.ONE
            )
        } else {
            cart.add(CartItem(
                catalogId = item.id,
                name      = item.name,
                barcode   = item.barcode,
                unit      = item.unit,
                price     = item.price,
                quantity  = BigDecimal.ONE,
                vatRate   = item.vatRate
            ))
        }
        _state.update { it.copy(cart = cart, searchQuery = "", searchResults = emptyList()) }
    }

    fun addManualItem(name: String, price: BigDecimal, qty: BigDecimal, vatRate: VatRate) {
        val cart = _state.value.cart.toMutableList()
        cart.add(CartItem(
            catalogId = -1L,
            name = name, barcode = null, unit = "шт",
            price = price, quantity = qty, vatRate = vatRate
        ))
        _state.update { it.copy(cart = cart) }
    }

    fun updateQuantity(index: Int, qty: BigDecimal) {
        if (qty <= BigDecimal.ZERO) { removeItem(index); return }
        val cart = _state.value.cart.toMutableList()
        cart[index] = cart[index].copy(quantity = qty)
        _state.update { it.copy(cart = cart) }
    }

    fun removeItem(index: Int) {
        val cart = _state.value.cart.toMutableList()
        cart.removeAt(index)
        _state.update { it.copy(cart = cart) }
    }

    fun clearCart() = _state.update { it.copy(cart = emptyList()) }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun toReceiptItems(): List<ReceiptItem> = _state.value.cart.map { c ->
        ReceiptItem(
            name     = c.name,
            barcode  = c.barcode,
            unit     = c.unit,
            price    = c.price,
            quantity = c.quantity,
            discount = c.discount,
            vatRate  = c.vatRate
        )
    }
}
