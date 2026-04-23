package kz.kkm.data.local.dao

import androidx.room.*
import kz.kkm.data.local.entity.*
import kz.kkm.domain.model.OfdStatus
import kz.kkm.domain.model.ShiftStatus
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDateTime

// ─────────────────── ShiftDao ───────────────────

@Dao
interface ShiftDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(shift: ShiftEntity): Long

    @Update
    suspend fun update(shift: ShiftEntity)

    @Query("SELECT * FROM shifts WHERE status = 'OPEN' LIMIT 1")
    suspend fun getOpenShift(): ShiftEntity?

    @Query("SELECT * FROM shifts WHERE status = 'OPEN' LIMIT 1")
    fun observeOpenShift(): Flow<ShiftEntity?>

    @Query("SELECT * FROM shifts ORDER BY id DESC LIMIT 1")
    suspend fun getLastShift(): ShiftEntity?

    @Query("SELECT MAX(shiftNumber) FROM shifts")
    suspend fun getMaxShiftNumber(): Int?

    @Query("SELECT * FROM shifts ORDER BY id DESC")
    fun observeAll(): Flow<List<ShiftEntity>>

    @Query("""
        UPDATE shifts SET 
          totalSales = totalSales + :amount,
          receiptsCount = receiptsCount + 1
        WHERE id = :shiftId
    """)
    suspend fun addSaleToShift(shiftId: Long, amount: BigDecimal)

    @Query("""
        UPDATE shifts SET 
          totalReturns = totalReturns + :amount,
          returnsCount = returnsCount + 1
        WHERE id = :shiftId
    """)
    suspend fun addReturnToShift(shiftId: Long, amount: BigDecimal)
}

// ─────────────────── ReceiptDao ───────────────────

@Dao
interface ReceiptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceipt(receipt: ReceiptEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ReceiptItemEntity>)

    @Update
    suspend fun updateReceipt(receipt: ReceiptEntity)

    @Query("SELECT * FROM receipts WHERE id = :id")
    suspend fun getById(id: Long): ReceiptEntity?

    @Query("SELECT * FROM receipts WHERE uuid = :uuid")
    suspend fun getByUuid(uuid: String): ReceiptEntity?

    @Query("SELECT * FROM receipt_items WHERE receiptId = :receiptId")
    suspend fun getItemsForReceipt(receiptId: Long): List<ReceiptItemEntity>

    @Query("SELECT * FROM receipts WHERE shiftId = :shiftId ORDER BY id DESC")
    fun observeByShift(shiftId: Long): Flow<List<ReceiptEntity>>

    @Query("SELECT * FROM receipts WHERE ofdStatus = 'PENDING' OR ofdStatus = 'FAILED' ORDER BY createdAt")
    suspend fun getPendingForOfd(): List<ReceiptEntity>

    @Query("UPDATE receipts SET ofdStatus = :status, ofdTicketId = :ticketId WHERE id = :id")
    suspend fun updateOfdStatus(id: Long, status: OfdStatus, ticketId: String?)

    @Query("""
        SELECT MAX(receiptNumber) FROM receipts 
        WHERE shiftId = :shiftId
    """)
    suspend fun getMaxReceiptNumber(shiftId: Long): Int?

    @Query("""
        SELECT SUM(totalAmount) FROM receipts 
        WHERE type = 'SALE'
        AND createdAt BETWEEN :from AND :to
    """)
    suspend fun sumSalesBetween(from: LocalDateTime, to: LocalDateTime): BigDecimal?

    @Query("""
        SELECT SUM(totalAmount) FROM receipts 
        WHERE type = 'RETURN'
        AND createdAt BETWEEN :from AND :to
    """)
    suspend fun sumReturnsBetween(from: LocalDateTime, to: LocalDateTime): BigDecimal?

    @Query("""
        SELECT * FROM receipts
        WHERE createdAt BETWEEN :from AND :to
        ORDER BY createdAt DESC
    """)
    fun observeBetween(from: LocalDateTime, to: LocalDateTime): Flow<List<ReceiptEntity>>
}

// ─────────────────── CatalogDao ───────────────────

@Dao
interface CatalogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: CatalogItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CatalogItemEntity>)

    @Update
    suspend fun update(item: CatalogItemEntity)

    @Delete
    suspend fun delete(item: CatalogItemEntity)

    @Query("SELECT * FROM catalog_items WHERE isFavorite = 1 ORDER BY name")
    fun observeFavorites(): Flow<List<CatalogItemEntity>>

    @Query("SELECT * FROM catalog_items ORDER BY name")
    fun observeAll(): Flow<List<CatalogItemEntity>>

    @Query("""
        SELECT * FROM catalog_items 
        WHERE name LIKE '%' || :query || '%' 
        OR barcode LIKE '%' || :query || '%'
        ORDER BY name
        LIMIT 50
    """)
    suspend fun search(query: String): List<CatalogItemEntity>

    @Query("SELECT * FROM catalog_items WHERE barcode = :barcode LIMIT 1")
    suspend fun getByBarcode(barcode: String): CatalogItemEntity?

    @Query("UPDATE catalog_items SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)
}

// ─────────────────── EmployeeDao ───────────────────

@Dao
interface EmployeeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(employee: EmployeeEntity): Long

    @Update
    suspend fun update(employee: EmployeeEntity)

    @Query("SELECT * FROM employees WHERE isActive = 1 ORDER BY fullName")
    fun observeActive(): Flow<List<EmployeeEntity>>

    @Query("SELECT * FROM employees ORDER BY fullName")
    suspend fun getAll(): List<EmployeeEntity>

    @Query("SELECT * FROM employees WHERE id = :id")
    suspend fun getById(id: Long): EmployeeEntity?

    @Query("UPDATE employees SET isActive = 0 WHERE id = :id")
    suspend fun deactivate(id: Long)
}

// ─────────────────── TaxPeriodDao ───────────────────

@Dao
interface TaxPeriodDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(period: TaxPeriodEntity): Long

    @Update
    suspend fun update(period: TaxPeriodEntity)

    @Query("SELECT * FROM tax_periods ORDER BY year DESC, half DESC")
    fun observeAll(): Flow<List<TaxPeriodEntity>>

    @Query("SELECT * FROM tax_periods WHERE year = :year AND half = :half LIMIT 1")
    suspend fun getByYearHalf(year: Int, half: Int): TaxPeriodEntity?

    @Query("SELECT * FROM tax_periods WHERE id = :id")
    suspend fun getById(id: Long): TaxPeriodEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayrollEntry(entry: PayrollEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllPayrollEntries(entries: List<PayrollEntryEntity>)

    @Update
    suspend fun updatePayrollEntry(entry: PayrollEntryEntity)

    @Query("DELETE FROM payroll_entries WHERE periodId = :periodId")
    suspend fun deletePayrollEntries(periodId: Long)

    @Query("SELECT * FROM payroll_entries WHERE periodId = :periodId")
    suspend fun getPayrollEntries(periodId: Long): List<PayrollEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttempt(attempt: DeclarationAttemptEntity): Long

    @Query("SELECT * FROM declaration_attempts WHERE periodId = :periodId ORDER BY attemptedAt DESC")
    suspend fun getAttempts(periodId: Long): List<DeclarationAttemptEntity>
}
