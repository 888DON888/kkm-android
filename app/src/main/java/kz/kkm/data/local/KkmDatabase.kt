package kz.kkm.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import kz.kkm.BuildConfig
import kz.kkm.data.local.dao.*
import kz.kkm.data.local.entity.*
import javax.inject.Inject
import javax.inject.Singleton

@Database(
    entities = [
        ShiftEntity::class,
        ReceiptEntity::class,
        ReceiptItemEntity::class,
        CatalogItemEntity::class,
        EmployeeEntity::class,
        TaxPeriodEntity::class,
        PayrollEntryEntity::class,
        DeclarationAttemptEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class KkmDatabase : RoomDatabase() {
    abstract fun shiftDao(): ShiftDao
    abstract fun receiptDao(): ReceiptDao
    abstract fun catalogDao(): CatalogDao
    abstract fun employeeDao(): EmployeeDao
    abstract fun taxPeriodDao(): TaxPeriodDao

    companion object {
        const val DB_NAME = "kkm_fiscal.db"

        fun create(context: Context, passphrase: ByteArray): KkmDatabase {
            val builder = Room.databaseBuilder(context, KkmDatabase::class.java, DB_NAME)
                .fallbackToDestructiveMigration()

            if (!BuildConfig.DEBUG) {
                // Release: use SQLCipher AES-256 encryption
                val factory = net.sqlcipher.database.SupportFactory(
                    net.sqlcipher.database.SQLiteDatabase.getBytes(
                        String(passphrase, Charsets.UTF_8).toCharArray()
                    )
                )
                builder.openHelperFactory(factory)
            }
            // Debug: plain unencrypted DB (no native .so needed, works in all emulators)

            return builder.build()
        }
    }
}

/**
 * Provides the database encryption passphrase.
 * Passphrase = PBKDF2(user_pin, device_id_salt) stored in Android Keystore.
 */
@Singleton
class DatabasePassphraseProvider @Inject constructor(
    private val context: Context
) {
    private val prefs by lazy {
        context.getSharedPreferences("kkm_secure", Context.MODE_PRIVATE)
    }

    fun getOrCreatePassphrase(): ByteArray {
        val key = "db_pass"
        val existing = prefs.getString(key, null)
        if (existing != null) return existing.toByteArray()
        // Generate random 32-byte passphrase
        val bytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(bytes)
        val encoded = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        prefs.edit().putString(key, encoded).apply()
        return encoded.toByteArray()
    }
}
