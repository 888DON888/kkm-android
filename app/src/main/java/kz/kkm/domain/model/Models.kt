package kz.kkm.domain.model

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

// ─────────────────────────── Shift ───────────────────────────

enum class ShiftStatus { OPEN, CLOSED }

data class Shift(
    val id: Long = 0,
    val shiftNumber: Int,
    val openedAt: LocalDateTime,
    val closedAt: LocalDateTime? = null,
    val status: ShiftStatus = ShiftStatus.OPEN,
    val ofdTicketOpen: String? = null,
    val ofdTicketClose: String? = null,
    val totalSales: BigDecimal = BigDecimal.ZERO,
    val totalReturns: BigDecimal = BigDecimal.ZERO,
    val receiptsCount: Int = 0,
    val returnsCount: Int = 0
)

// ─────────────────────────── Receipt ─────────────────────────

enum class ReceiptType {
    SALE,       // тип 02
    RETURN,     // тип 04
    CANCEL      // тип 05
}

enum class PaymentType { CASH, CARD, MIXED }

enum class OfdStatus { PENDING, SENT, CONFIRMED, FAILED }

data class ReceiptItem(
    val id: Long = 0,
    val name: String,
    val barcode: String? = null,
    val unit: String = "шт",
    val price: BigDecimal,
    val quantity: BigDecimal,
    val discount: BigDecimal = BigDecimal.ZERO,
    val vatRate: VatRate = VatRate.NONE,
) {
    val subtotal: BigDecimal get() = (price * quantity - discount).setScale(2, java.math.RoundingMode.HALF_UP)
    val vatAmount: BigDecimal get() = when (vatRate) {
        VatRate.VAT_12 -> subtotal * BigDecimal("0.12") / BigDecimal("1.12")
        VatRate.NONE   -> BigDecimal.ZERO
    }.setScale(2, java.math.RoundingMode.HALF_UP)
}

enum class VatRate(val rate: BigDecimal, val label: String) {
    NONE(BigDecimal.ZERO, "Без НДС"),
    VAT_12(BigDecimal("0.12"), "НДС 12%")
}

data class Receipt(
    val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val shiftId: Long,
    val shiftNumber: Int,
    val receiptNumber: Int,
    val type: ReceiptType,
    val items: List<ReceiptItem>,
    val paymentType: PaymentType,
    val cashAmount: BigDecimal = BigDecimal.ZERO,
    val cardAmount: BigDecimal = BigDecimal.ZERO,
    val totalAmount: BigDecimal,
    val vatTotal: BigDecimal,
    val change: BigDecimal = BigDecimal.ZERO,
    val fiscalSign: String = "",         // ФП (HMAC-SHA256)
    val fiscalSignNum: Long = 0L,
    val ofdStatus: OfdStatus = OfdStatus.PENDING,
    val ofdTicketId: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val originalReceiptId: Long? = null, // for returns
    val sellerBin: String = "",
    val sellerName: String = "",
    val sellerAddress: String = ""
)

// ─────────────────────────── Organization ────────────────────

data class Organization(
    val bin: String,
    val ntin: String = "",
    val name: String,
    val address: String,
    val isVatPayer: Boolean = false,
    val taxSystem: String = "SNR_UD", // СНР на основе упрощённой декларации
    val ofdUrl: String = "",
    val ofdToken: String = ""
)

// ─────────────────────────── Catalog ─────────────────────────

data class CatalogItem(
    val id: Long = 0,
    val name: String,
    val barcode: String? = null,
    val unit: String = "шт",
    val price: BigDecimal,
    val vatRate: VatRate = VatRate.NONE,
    val isFavorite: Boolean = false,
    val gtin: String? = null
)

// ─────────────────────────── Employee (for 910.00) ───────────

data class Employee(
    val id: Long = 0,
    val iin: String,          // encrypted in DB
    val fullName: String,
    val position: String = "",
    val isPensioner: Boolean = false,
    val isDisabled: Boolean = false,
    val isCivilContract: Boolean = false,  // ГПХ
    val isActive: Boolean = true
)

data class PayrollEntry(
    val id: Long = 0,
    val periodId: Long,
    val employeeId: Long,
    val employeeName: String,
    val monthsWorked: Int,
    val grossIncome: BigDecimal,    // начисленный доход за период
    // Computed by TaxCalculator:
    val opv: BigDecimal = BigDecimal.ZERO,      // ОПВ работника
    val osms: BigDecimal = BigDecimal.ZERO,     // ООСМС работника
    val ipn: BigDecimal = BigDecimal.ZERO,      // ИПН работника
    val so: BigDecimal = BigDecimal.ZERO,       // СО работодатель
    val vosms: BigDecimal = BigDecimal.ZERO,    // ВОСМС работодатель
    val sn: BigDecimal = BigDecimal.ZERO,       // Социальный налог работодатель
    val netIncome: BigDecimal = BigDecimal.ZERO // На руки
)

// ─────────────────────────── Tax Period (910.00) ─────────────

enum class DeclarationStatus {
    DRAFT, SIGNED, SENDING, ACCEPTED, PROCESSED, REJECTED, FAILED, CANCELLED
}

data class TaxPeriod(
    val id: Long = 0,
    val year: Int,
    val half: Int,           // 1 = январь-июнь, 2 = июль-декабрь
    val incomeTotal: BigDecimal = BigDecimal.ZERO,
    val incomeByMonth: Map<Int, BigDecimal> = emptyMap(),
    val status: DeclarationStatus = DeclarationStatus.DRAFT,
    val ticketId: String? = null,
    val submittedAt: LocalDateTime? = null,
    val xmlHash: String? = null
)

// ─────────────────────────── Reports ─────────────────────────

data class XReport(
    val shiftNumber: Int,
    val openedAt: LocalDateTime,
    val printedAt: LocalDateTime,
    val totalSales: BigDecimal,
    val totalReturns: BigDecimal,
    val totalCancelled: BigDecimal,
    val totalCash: BigDecimal,
    val totalCard: BigDecimal,
    val vatTotal: BigDecimal,
    val receiptsCount: Int,
    val returnsCount: Int
)

data class ZReport(
    val shiftNumber: Int,
    val openedAt: LocalDateTime,
    val closedAt: LocalDateTime,
    val ofdTicket: String? = null,
    val xReport: XReport
)
