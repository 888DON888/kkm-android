package kz.kkm.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteOpenHelper
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
                createSqlCipherFactory(passphrase)?.let { builder.openHelperFactory(it) }
            }
            return builder.build()
        }

        @Suppress("UNCHECKED_CAST")
        private fun createSqlCipherFactory(passphrase: ByteArray): SupportSQLiteOpenHelper.Factory? = try {
            val dbCls = Class.forName("net.sqlcipher.database.SQLiteDatabase")
            val key = dbCls.getMethod("getBytes", CharArray::class.java)
                .invoke(null, String(passphrase, Charsets.UTF_8).toCharArray()) as ByteArray
            Class.forName("net.sqlcipher.database.SupportFactory")
                .getConstructor(ByteArray::class.java).newInstance(key) as SupportSQLiteOpenHelper.Factory
        } catch (e: Exception) { null }
    }
}

@Singleton
class DatabasePassphraseProvider @Inject constructor(private val context: Context) {
    private val prefs by lazy { context.getSharedPreferences("kkm_secure", Context.MODE_PRIVATE) }

    fun getOrCreatePassphrase(): ByteArray {
        val existing = prefs.getString("db_pass", null)
        if (existing != null) return existing.toByteArray()
        val bytes = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val encoded = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        prefs.edit().putString("db_pass", encoded).apply()
        return encoded.toByteArray()
    }
}