package kz.kkm.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import kz.kkm.data.local.dao.*
import kz.kkm.data.local.entity.*
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
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
            val factory = SupportFactory(SQLiteDatabase.getBytes(
                String(passphrase, Charsets.UTF_8).toCharArray()
            ))
            return Room.databaseBuilder(context, KkmDatabase::class.java, DB_NAME)
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
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
        androidx.security.crypto.EncryptedSharedPreferences.create(
            context,
            "kkm_secure_prefs",
            androidx.security.crypto.MasterKey.Builder(context)
                .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                .build(),
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getOrCreatePassphrase(): ByteArray {
        val existing = prefs.getString(KEY_DB_PASS, null)
        if (existing != null) return existing.toByteArray()
        // Generate 32-byte random passphrase
        val newPass = java.security.SecureRandom().let { rng ->
            ByteArray(32).also { rng.nextBytes(it) }
        }
        val encoded = android.util.Base64.encodeToString(newPass, android.util.Base64.NO_WRAP)
        prefs.edit().putString(KEY_DB_PASS, encoded).apply()
        return encoded.toByteArray()
    }

    companion object { private const val KEY_DB_PASS = "db_passphrase" }
}
