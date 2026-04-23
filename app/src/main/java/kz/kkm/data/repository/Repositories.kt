package kz.kkm.data.repository

import android.util.Base64
import kz.kkm.data.local.dao.*
import kz.kkm.data.local.entity.*
import kz.kkm.data.remote.OfdApiService
import kz.kkm.data.remote.OfdDocumentRequest
import kz.kkm.data.remote.OfdShiftRequest
import kz.kkm.data.remote.OfdZReportRequest
import kz.kkm.domain.model.*
import kz.kkm.fiscal.FiscalManager
import kz.kkm.fiscal.QrCodeGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShiftRepository @Inject constructor(
    private val shiftDao: ShiftDao,
    private val ofdApiService: OfdApiService,
    private val fiscalManager: FiscalManager,
    private val settingsRepo: SettingsRepository
) {
    fun observeOpenShift(): Flow<Shift?> =
        shiftDao.observeOpenShift().map { it?.toDomain() }

    suspend fun openShift(): Result<Shift> = runCatching {
        val existing = shiftDao.getOpenShift()
        if (existing != null) return Result.success(existing.toDomain())

        val lastNumber = shiftDao.getMaxShiftNumber() ?: 0
        val org = settingsRepo.getOrganization()
        val shift = ShiftEntity(
            shiftNumber = lastNumber + 1,
            openedAt = LocalDateTime.now(),
            status = ShiftStatus.OPEN
        )
        val id = shiftDao.insert(shift)
        val savedShift = shift.copy(id = id)

        // Send to OFD
        val fp = fiscalManager.generateFiscalSign(Receipt(
            shiftId = id, shiftNumber = savedShift.shiftNumber,
            receiptNumber = 0, type = ReceiptType.SALE,
            items = emptyList(), paymentType = PaymentType.CASH,
            totalAmount = BigDecimal.ZERO, vatTotal = BigDecimal.ZERO,
            sellerBin = org.bin
        ))
        try {
            val resp = ofdApiService.openShift(
                token = "Bearer ${org.ofdToken}",
                bin = org.bin,
                request = OfdShiftRequest(
                    shiftNumber = savedShift.shiftNumber,
                    openDateTime = savedShift.openedAt.format(DateTimeFormatter.ISO_DATE_TIME),
                    bin = org.bin,
                    fiscalSign = fp
                )
            )
            if (resp.isSuccessful) {
                shiftDao.update(savedShift.copy(ofdTicketOpen = resp.body()?.ticketId))
            }
        } catch (_: Exception) { /* offline — continue */ }

        savedShift.toDomain()
    }

    suspend fun closeShift(): Result<ZReport> = runCatching {
        val openShift = shiftDao.getOpenShift()
            ?: throw IllegalStateException("No open shift")
        val closedAt = LocalDateTime.now()
        val org = settingsRepo.getOrganization()

        // Build Z-report data
        val fp = fiscalManager.generateFiscalSign(Receipt(
            shiftId = openShift.id, shiftNumber = openShift.shiftNumber,
            receiptNumber = 0, type = ReceiptType.SALE,
            items = emptyList(), paymentType = PaymentType.CASH,
            totalAmount = openShift.totalSales, vatTotal = BigDecimal.ZERO,
            sellerBin = org.bin
        ))

        var ofdTicket: String? = null
        try {
            val resp = ofdApiService.closeShift(
                token = "Bearer ${org.ofdToken}",
                bin = org.bin,
                request = OfdZReportRequest(
                    shiftNumber = openShift.shiftNumber,
                    closeDateTime = closedAt.format(DateTimeFormatter.ISO_DATE_TIME),
                    totalSales = openShift.totalSales.toPlainString(),
                    totalReturns = openShift.totalReturns.toPlainString(),
                    receiptsCount = openShift.receiptsCount,
                    bin = org.bin,
                    fiscalSign = fp
                )
            )
            ofdTicket = resp.body()?.ticketId
        } catch (_: Exception) { /* offline */ }

        shiftDao.update(openShift.copy(
            status = ShiftStatus.CLOSED,
            closedAt = closedAt,
            ofdTicketClose = ofdTicket
        ))

        ZReport(
            shiftNumber = openShift.shiftNumber,
            openedAt = openShift.openedAt,
            closedAt = closedAt,
            ofdTicket = ofdTicket,
            xReport = XReport(
                shiftNumber = openShift.shiftNumber,
                openedAt = openShift.openedAt,
                printedAt = closedAt,
                totalSales = openShift.totalSales,
                totalReturns = openShift.totalReturns,
                totalCancelled = BigDecimal.ZERO,
                totalCash = BigDecimal.ZERO,
                totalCard = BigDecimal.ZERO,
                vatTotal = BigDecimal.ZERO,
                receiptsCount = openShift.receiptsCount,
                returnsCount = openShift.returnsCount
            )
        )
    }

    suspend fun getXReport(): XReport? {
        val shift = shiftDao.getOpenShift() ?: return null
        return XReport(
            shiftNumber = shift.shiftNumber,
            openedAt = shift.openedAt,
            printedAt = LocalDateTime.now(),
            totalSales = shift.totalSales,
            totalReturns = shift.totalReturns,
            totalCancelled = BigDecimal.ZERO,
            totalCash = BigDecimal.ZERO,
            totalCard = BigDecimal.ZERO,
            vatTotal = BigDecimal.ZERO,
            receiptsCount = shift.receiptsCount,
            returnsCount = shift.returnsCount
        )
    }

    private fun ShiftEntity.toDomain() = Shift(
        id, shiftNumber, openedAt, closedAt, status,
        ofdTicketOpen, ofdTicketClose, totalSales, totalReturns, receiptsCount, returnsCount
    )
}

@Singleton
class ReceiptRepository @Inject constructor(
    private val receiptDao: ReceiptDao,
    private val shiftDao: ShiftDao,
    private val ofdApiService: OfdApiService,
    private val fiscalManager: FiscalManager,
    private val settingsRepo: SettingsRepository
) {
    fun observeByShift(shiftId: Long): Flow<List<Receipt>> =
        receiptDao.observeByShift(shiftId).map { list ->
            list.map { it.toDomain(receiptDao.getItemsForReceipt(it.id).map { i -> i.toDomain() }) }
        }

    suspend fun createReceipt(
        items: List<ReceiptItem>,
        paymentType: PaymentType,
        cashAmount: BigDecimal,
        cardAmount: BigDecimal,
        change: BigDecimal = BigDecimal.ZERO,
        type: ReceiptType = ReceiptType.SALE,
        originalReceiptId: Long? = null
    ): Result<Receipt> = runCatching {
        val org = settingsRepo.getOrganization()
        val shift = shiftDao.getOpenShift()
            ?: throw IllegalStateException("Необходимо открыть смену")
        val receiptNumber = (receiptDao.getMaxReceiptNumber(shift.id) ?: 0) + 1

        val totalAmount = items.sumOf { it.subtotal }
        val vatTotal = items.sumOf { it.vatAmount }

        // Build receipt without FP first
        val draft = Receipt(
            shiftId = shift.id,
            shiftNumber = shift.shiftNumber,
            receiptNumber = receiptNumber,
            type = type,
            items = items,
            paymentType = paymentType,
            cashAmount = cashAmount,
            cardAmount = cardAmount,
            totalAmount = totalAmount,
            vatTotal = vatTotal,
            change = change,
            originalReceiptId = originalReceiptId,
            sellerBin = org.bin,
            sellerName = org.name,
            sellerAddress = org.address
        )

        // Generate fiscal sign
        val fp = fiscalManager.generateFiscalSign(draft)
        val fpNum = fiscalManager.generateFiscalSignNum()
        val receipt = draft.copy(fiscalSign = fp, fiscalSignNum = fpNum)

        // Save to DB
        val receiptEntity = receipt.toEntity()
        val savedId = receiptDao.insertReceipt(receiptEntity)
        receiptDao.insertItems(items.map { it.toEntity(savedId) })

        // Update shift totals
        when (type) {
            ReceiptType.SALE   -> shiftDao.addSaleToShift(shift.id, totalAmount)
            ReceiptType.RETURN -> shiftDao.addReturnToShift(shift.id, totalAmount)
            ReceiptType.CANCEL -> {}
        }

        val savedReceipt = receipt.copy(id = savedId)

        // Send to OFD (async, don't block)
        sendToOfdSafe(savedReceipt, org)

        savedReceipt
    }

    private suspend fun sendToOfdSafe(receipt: Receipt, org: Organization) {
        try {
            val tlv = fiscalManager.buildTlvPacket(receipt, org)
            val tlvB64 = Base64.encodeToString(tlv, Base64.NO_WRAP)
            val resp = ofdApiService.sendDocument(
                token = "Bearer ${org.ofdToken}",
                bin = org.bin,
                rnm = settingsRepo.getRnm(),
                request = OfdDocumentRequest(
                    docType = receipt.type.toDocType(),
                    tlvData = tlvB64,
                    fiscalSign = receipt.fiscalSign,
                    docNumber = receipt.receiptNumber,
                    shiftNumber = receipt.shiftNumber,
                    dateTime = receipt.createdAt.format(DateTimeFormatter.ISO_DATE_TIME),
                    totalAmount = receipt.totalAmount.toPlainString(),
                    bin = receipt.sellerBin
                )
            )
            val status = if (resp.isSuccessful) OfdStatus.CONFIRMED else OfdStatus.FAILED
            receiptDao.updateOfdStatus(receipt.id, status, resp.body()?.ticketId)
        } catch (_: Exception) {
            receiptDao.updateOfdStatus(receipt.id, OfdStatus.FAILED, null)
        }
    }

    suspend fun getPendingOfdReceipts(): List<Receipt> =
        receiptDao.getPendingForOfd().map {
            it.toDomain(receiptDao.getItemsForReceipt(it.id).map { i -> i.toDomain() })
        }

    suspend fun retryPendingOfd() {
        val org = settingsRepo.getOrganization()
        getPendingOfdReceipts().forEach { sendToOfdSafe(it, org) }
    }

    suspend fun getIncomeForPeriod(from: LocalDateTime, to: LocalDateTime): BigDecimal {
        val sales = receiptDao.sumSalesBetween(from, to) ?: BigDecimal.ZERO
        val returns = receiptDao.sumReturnsBetween(from, to) ?: BigDecimal.ZERO
        return sales - returns
    }

    private fun ReceiptType.toDocType() = when (this) {
        ReceiptType.SALE   -> 2
        ReceiptType.RETURN -> 4
        ReceiptType.CANCEL -> 5
    }

    private fun Receipt.toEntity() = ReceiptEntity(
        uuid = uuid, shiftId = shiftId, shiftNumber = shiftNumber,
        receiptNumber = receiptNumber, type = type, paymentType = paymentType,
        cashAmount = cashAmount, cardAmount = cardAmount,
        totalAmount = totalAmount, vatTotal = vatTotal, change = change,
        fiscalSign = fiscalSign, fiscalSignNum = fiscalSignNum,
        ofdStatus = ofdStatus, originalReceiptId = originalReceiptId,
        sellerBin = sellerBin, sellerName = sellerName, sellerAddress = sellerAddress
    )

    private fun ReceiptItem.toEntity(receiptId: Long) = ReceiptItemEntity(
        receiptId = receiptId, name = name, barcode = barcode,
        unit = unit, price = price, quantity = quantity,
        discount = discount, vatRate = vatRate
    )

    private fun ReceiptEntity.toDomain(items: List<ReceiptItem>) = Receipt(
        id = id, uuid = uuid, shiftId = shiftId, shiftNumber = shiftNumber,
        receiptNumber = receiptNumber, type = type, items = items,
        paymentType = paymentType, cashAmount = cashAmount, cardAmount = cardAmount,
        totalAmount = totalAmount, vatTotal = vatTotal, change = change,
        fiscalSign = fiscalSign, fiscalSignNum = fiscalSignNum,
        ofdStatus = ofdStatus, ofdTicketId = ofdTicketId, createdAt = createdAt,
        originalReceiptId = originalReceiptId,
        sellerBin = sellerBin, sellerName = sellerName, sellerAddress = sellerAddress
    )

    private fun ReceiptItemEntity.toDomain() = ReceiptItem(
        id = id, name = name, barcode = barcode, unit = unit,
        price = price, quantity = quantity, discount = discount, vatRate = vatRate
    )

    private fun List<ReceiptItem>.sumOf(selector: (ReceiptItem) -> BigDecimal) =
        fold(BigDecimal.ZERO) { a, b -> a + selector(b) }
}
