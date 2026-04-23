package kz.kkm.data.local.entity

import androidx.room.*
import kz.kkm.domain.model.*
import java.math.BigDecimal
import java.time.LocalDateTime

// ─────────────────── Type Converters ───────────────────

class Converters {
    @TypeConverter fun fromBigDecimal(v: BigDecimal?): String? = v?.toPlainString()
    @TypeConverter fun toBigDecimal(v: String?): BigDecimal? = v?.let { BigDecimal(it) }
    @TypeConverter fun fromLocalDateTime(v: LocalDateTime?): Long? = v?.let {
        java.time.ZoneOffset.UTC.let { z -> it.toInstant(z).epochSecond }
    }
    @TypeConverter fun toLocalDateTime(v: Long?): LocalDateTime? = v?.let {
        LocalDateTime.ofEpochSecond(it, 0, java.time.ZoneOffset.UTC)
    }
    @TypeConverter fun fromShiftStatus(v: ShiftStatus?): String? = v?.name
    @TypeConverter fun toShiftStatus(v: String?): ShiftStatus? = v?.let { ShiftStatus.valueOf(it) }
    @TypeConverter fun fromReceiptType(v: ReceiptType?): String? = v?.name
    @TypeConverter fun toReceiptType(v: String?): ReceiptType? = v?.let { ReceiptType.valueOf(it) }
    @TypeConverter fun fromPaymentType(v: PaymentType?): String? = v?.name
    @TypeConverter fun toPaymentType(v: String?): PaymentType? = v?.let { PaymentType.valueOf(it) }
    @TypeConverter fun fromOfdStatus(v: OfdStatus?): String? = v?.name
    @TypeConverter fun toOfdStatus(v: String?): OfdStatus? = v?.let { OfdStatus.valueOf(it) }
    @TypeConverter fun fromVatRate(v: VatRate?): String? = v?.name
    @TypeConverter fun toVatRate(v: String?): VatRate? = v?.let { VatRate.valueOf(it) }
    @TypeConverter fun fromDeclarationStatus(v: DeclarationStatus?): String? = v?.name
    @TypeConverter fun toDeclarationStatus(v: String?): DeclarationStatus? = v?.let { DeclarationStatus.valueOf(it) }
}

// ─────────────────── Shift Entity ───────────────────

@Entity(tableName = "shifts")
data class ShiftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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

// ─────────────────── Receipt Entity ───────────────────

@Entity(
    tableName = "receipts",
    foreignKeys = [ForeignKey(
        entity = ShiftEntity::class,
        parentColumns = ["id"],
        childColumns = ["shiftId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("shiftId"), Index("ofdStatus"), Index("createdAt")]
)
data class ReceiptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val shiftId: Long,
    val shiftNumber: Int,
    val receiptNumber: Int,
    val type: ReceiptType,
    val paymentType: PaymentType,
    val cashAmount: BigDecimal = BigDecimal.ZERO,
    val cardAmount: BigDecimal = BigDecimal.ZERO,
    val totalAmount: BigDecimal,
    val vatTotal: BigDecimal,
    val change: BigDecimal = BigDecimal.ZERO,
    val fiscalSign: String = "",
    val fiscalSignNum: Long = 0L,
    val ofdStatus: OfdStatus = OfdStatus.PENDING,
    val ofdTicketId: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val originalReceiptId: Long? = null,
    val sellerBin: String = "",
    val sellerName: String = "",
    val sellerAddress: String = ""
)

// ─────────────────── ReceiptItem Entity ───────────────────

@Entity(
    tableName = "receipt_items",
    foreignKeys = [ForeignKey(
        entity = ReceiptEntity::class,
        parentColumns = ["id"],
        childColumns = ["receiptId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("receiptId")]
)
data class ReceiptItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptId: Long,
    val name: String,
    val barcode: String? = null,
    val unit: String = "шт",
    val price: BigDecimal,
    val quantity: BigDecimal,
    val discount: BigDecimal = BigDecimal.ZERO,
    val vatRate: VatRate = VatRate.NONE
)

// ─────────────────── Catalog Entity ───────────────────

@Entity(
    tableName = "catalog_items",
    indices = [Index("barcode"), Index("isFavorite")]
)
data class CatalogItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val barcode: String? = null,
    val unit: String = "шт",
    val price: BigDecimal,
    val vatRate: VatRate = VatRate.NONE,
    val isFavorite: Boolean = false,
    val gtin: String? = null
)

// ─────────────────── Employee Entity ───────────────────

@Entity(tableName = "employees")
data class EmployeeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val iinEncrypted: String,          // AES encrypted
    val fullName: String,
    val position: String = "",
    val isPensioner: Boolean = false,
    val isDisabled: Boolean = false,
    val isCivilContract: Boolean = false,
    val isActive: Boolean = true
)

// ─────────────────── Payroll Entry ───────────────────

@Entity(
    tableName = "payroll_entries",
    foreignKeys = [ForeignKey(
        entity = TaxPeriodEntity::class,
        parentColumns = ["id"],
        childColumns = ["periodId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("periodId")]
)
data class PayrollEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val periodId: Long,
    val employeeId: Long,
    val employeeName: String,
    val monthsWorked: Int,
    val grossIncome: BigDecimal,
    val opv: BigDecimal = BigDecimal.ZERO,
    val osms: BigDecimal = BigDecimal.ZERO,
    val ipn: BigDecimal = BigDecimal.ZERO,
    val so: BigDecimal = BigDecimal.ZERO,
    val vosms: BigDecimal = BigDecimal.ZERO,
    val sn: BigDecimal = BigDecimal.ZERO,
    val netIncome: BigDecimal = BigDecimal.ZERO
)

// ─────────────────── Tax Period Entity ───────────────────

@Entity(
    tableName = "tax_periods",
    indices = [Index(value = ["year", "half"], unique = true)]
)
data class TaxPeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val year: Int,
    val half: Int,
    val incomeTotal: BigDecimal = BigDecimal.ZERO,
    val status: DeclarationStatus = DeclarationStatus.DRAFT,
    val ticketId: String? = null,
    val submittedAt: LocalDateTime? = null,
    val xmlHash: String? = null,
    val xmlFilePath: String? = null
)

// ─────────────────── Declaration Attempt ───────────────────

@Entity(
    tableName = "declaration_attempts",
    foreignKeys = [ForeignKey(
        entity = TaxPeriodEntity::class,
        parentColumns = ["id"],
        childColumns = ["periodId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("periodId")]
)
data class DeclarationAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val periodId: Long,
    val attemptedAt: LocalDateTime = LocalDateTime.now(),
    val httpStatus: Int = 0,
    val responseBody: String = "",
    val success: Boolean = false
)
