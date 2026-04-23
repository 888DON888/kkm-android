package kz.kkm.fiscal

import android.util.Base64
import kz.kkm.domain.model.Receipt
import kz.kkm.domain.model.ReceiptType
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.ZoneOffset
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

// ─────────────────── Fiscal Manager ───────────────────

/**
 * Generates Fiscal Sign (ФП) and TLV packets per KGD protocol 2.0.3.
 * In production: replace HMAC with GOST R 34.10-2012 signing via NCA RK SDK.
 */
@Singleton
class FiscalManager @Inject constructor(
    private val keyProvider: FiscalKeyProvider
) {

    /**
     * Generate Fiscal Sign (ФП) as HMAC-SHA256 of key receipt fields.
     * Production note: replace with GOST signature from NCA RK key container.
     */
    fun generateFiscalSign(receipt: Receipt): String {
        val data = buildFiscalString(receipt)
        val key = keyProvider.getHmacKey()
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        val hash = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    /**
     * Generate sequential fiscal sign number (порядковый номер ФП).
     */
    fun generateFiscalSignNum(): Long {
        return System.currentTimeMillis() / 1000L
    }

    /**
     * Build TLV packet for OFD transmission (protocol 2.0.3).
     */
    fun buildTlvPacket(receipt: Receipt, organization: kz.kkm.domain.model.Organization): ByteArray {
        val builder = TlvBuilder()

        // Tag 1000 - FFD version
        builder.addUint32(1000, 203u) // 2.0.3

        // Tag 1002 - operation type
        val opType = when (receipt.type) {
            ReceiptType.SALE   -> 2u
            ReceiptType.RETURN -> 4u
            ReceiptType.CANCEL -> 5u
        }
        builder.addUint32(1002, opType)

        // Tag 1009 - address
        builder.addString(1009, organization.address)

        // Tag 1012 - datetime
        val epoch = receipt.createdAt.toEpochSecond(ZoneOffset.of("+05:00"))
        builder.addUint32(1012, epoch.toUInt())

        // Tag 1017 - BIN/IIN
        builder.addString(1017, organization.bin)

        // Tag 1021 - cashier (operator)
        builder.addString(1021, "Кассир")

        // Tag 1020 - total amount (in tiyn: tenge * 100)
        val amountTiyn = (receipt.totalAmount * BigDecimal("100")).toLong()
        builder.addVln(1020, amountTiyn)

        // Tag 1023 - quantity for each item
        receipt.items.forEach { item ->
            builder.addString(1030, item.name)
            val priceTiyn = (item.price * BigDecimal("100")).toLong()
            builder.addVln(1043, priceTiyn)
            val quantityMg = (item.quantity * BigDecimal("1000")).toLong()
            builder.addVln(1023, quantityMg)
            val totalTiyn = (item.subtotal * BigDecimal("100")).toLong()
            builder.addVln(1059, totalTiyn)
            builder.addUint32(1197, item.vatRate.ordinal.toUInt())
        }

        // Tag 1054 - payment type: 1=incoming (sale), 2=outgoing (return)
        builder.addUint32(1054, if (receipt.type == ReceiptType.SALE) 1u else 2u)

        // Tag 1031 - cash amount
        if (receipt.cashAmount > BigDecimal.ZERO) {
            val cashTiyn = (receipt.cashAmount * BigDecimal("100")).toLong()
            builder.addVln(1031, cashTiyn)
        }

        // Tag 1081 - card amount
        if (receipt.cardAmount > BigDecimal.ZERO) {
            val cardTiyn = (receipt.cardAmount * BigDecimal("100")).toLong()
            builder.addVln(1081, cardTiyn)
        }

        // Tag 1080 - VAT total
        if (receipt.vatTotal > BigDecimal.ZERO) {
            val vatTiyn = (receipt.vatTotal * BigDecimal("100")).toLong()
            builder.addVln(1080, vatTiyn)
        }

        // Tag 1209 - FFD version
        builder.addUint32(1209, 203u)

        // Tag 1077 - fiscal sign (6 bytes of HMAC)
        val fpBytes = Base64.decode(receipt.fiscalSign, Base64.NO_WRAP).take(6).toByteArray()
        builder.addBytes(1077, fpBytes)

        // Tag 1040 - receipt number
        builder.addUint32(1040, receipt.receiptNumber.toUInt())

        // Tag 1038 - shift number
        builder.addUint32(1038, receipt.shiftNumber.toUInt())

        return builder.build()
    }

    private fun buildFiscalString(receipt: Receipt): String {
        return "${receipt.sellerBin}|${receipt.receiptNumber}|" +
               "${receipt.createdAt}|${receipt.totalAmount}|${receipt.shiftNumber}"
    }
}

// ─────────────────── TLV Builder ───────────────────

class TlvBuilder {
    private val buffer = mutableListOf<Byte>()

    fun addUint32(tag: Int, value: UInt) {
        addTag(tag)
        addLength(4)
        val bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value.toInt()).array()
        buffer.addAll(bytes.toList())
    }

    fun addString(tag: Int, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        addTag(tag)
        addLength(bytes.size)
        buffer.addAll(bytes.toList())
    }

    /** Variable-length number (FVLN) */
    fun addVln(tag: Int, value: Long) {
        val bytes = encodeVln(value)
        addTag(tag)
        addLength(bytes.size)
        buffer.addAll(bytes.toList())
    }

    fun addBytes(tag: Int, value: ByteArray) {
        addTag(tag)
        addLength(value.size)
        buffer.addAll(value.toList())
    }

    fun build(): ByteArray = buffer.toByteArray()

    private fun addTag(tag: Int) {
        // 2-byte little-endian tag
        buffer.add((tag and 0xFF).toByte())
        buffer.add(((tag shr 8) and 0xFF).toByte())
    }

    private fun addLength(len: Int) {
        buffer.add((len and 0xFF).toByte())
        if (len > 127) buffer.add(((len shr 8) and 0xFF).toByte())
    }

    private fun encodeVln(value: Long): ByteArray {
        // Simple 8-byte little-endian for fiscal amounts
        return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array()
    }
}

// ─────────────────── Key Provider ───────────────────

/**
 * Provides HMAC key stored in Android Keystore.
 * Production: use NCA RK GOST key container.
 */
@Singleton
class FiscalKeyProvider @Inject constructor(
    private val context: android.content.Context
) {
    private val keyAlias = "kkm_fiscal_hmac_key"

    fun getHmacKey(): ByteArray {
        val prefs = androidx.security.crypto.EncryptedSharedPreferences.create(
            context,
            "kkm_fiscal_keys",
            androidx.security.crypto.MasterKey.Builder(context)
                .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                .build(),
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        val existing = prefs.getString(keyAlias, null)
        if (existing != null) return Base64.decode(existing, Base64.NO_WRAP)

        val newKey = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        prefs.edit().putString(keyAlias, Base64.encodeToString(newKey, Base64.NO_WRAP)).apply()
        return newKey
    }
}

// ─────────────────── QR Code Generator ───────────────────

object QrCodeGenerator {
    /**
     * Generate QR code URL for KGD receipt verification.
     * Format: https://cabinet.salyk.kz/check?i={BIN}&c={receiptNo}&d={date}&s={amount}&fp={FP}
     */
    fun buildUrl(receipt: Receipt): String {
        val date = receipt.createdAt.toLocalDate().toString().replace("-", "")
        val amount = receipt.totalAmount.toPlainString()
        val fp = receipt.fiscalSign.take(8)
        return "https://cabinet.salyk.kz/esf-web/check" +
               "?i=${receipt.sellerBin}" +
               "&c=${receipt.receiptNumber}" +
               "&d=$date" +
               "&s=$amount" +
               "&fp=$fp"
    }

    /**
     * Generate QR Bitmap using ZXing.
     */
    fun generateBitmap(url: String, size: Int = 512): android.graphics.Bitmap {
        val hints = mapOf(
            com.google.zxing.EncodeHintType.ERROR_CORRECTION to
                com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M,
            com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8",
            com.google.zxing.EncodeHintType.MARGIN to 1
        )
        val matrix = com.google.zxing.qrcode.QRCodeWriter()
            .encode(url, com.google.zxing.BarcodeFormat.QR_CODE, size, size, hints)

        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        return bitmap
    }
}
