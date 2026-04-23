package kz.kkm.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kz.kkm.domain.model.Organization
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kkm_settings")

@Singleton
class SettingsRepository @Inject constructor(private val context: Context) {

    private object Keys {
        val BIN          = stringPreferencesKey("bin")
        val NTIN         = stringPreferencesKey("ntin")
        val ORG_NAME     = stringPreferencesKey("org_name")
        val ADDRESS      = stringPreferencesKey("address")
        val IS_VAT_PAYER = booleanPreferencesKey("is_vat_payer")
        val OFD_URL      = stringPreferencesKey("ofd_url")
        val OFD_TOKEN    = stringPreferencesKey("ofd_token")
        val RNM          = stringPreferencesKey("rnm")
        val LANGUAGE     = stringPreferencesKey("language")      // "ru" | "kk"
        val PIN_HASH     = stringPreferencesKey("pin_hash")
        val AUTO_CLOSE_SHIFT = booleanPreferencesKey("auto_close_shift")
        val RECEIPT_FOOTER   = stringPreferencesKey("receipt_footer")
        val ISNA_TOKEN   = stringPreferencesKey("isna_token")
    }

    suspend fun getOrganization(): Organization {
        val prefs = context.dataStore.data.first()
        return Organization(
            bin      = prefs[Keys.BIN] ?: "",
            ntin     = prefs[Keys.NTIN] ?: "",
            name     = prefs[Keys.ORG_NAME] ?: "",
            address  = prefs[Keys.ADDRESS] ?: "",
            isVatPayer = prefs[Keys.IS_VAT_PAYER] ?: false,
            ofdUrl   = prefs[Keys.OFD_URL] ?: "",
            ofdToken = prefs[Keys.OFD_TOKEN] ?: ""
        )
    }

    fun observeOrganization(): Flow<Organization> = context.dataStore.data.map { prefs ->
        Organization(
            bin      = prefs[Keys.BIN] ?: "",
            ntin     = prefs[Keys.NTIN] ?: "",
            name     = prefs[Keys.ORG_NAME] ?: "",
            address  = prefs[Keys.ADDRESS] ?: "",
            isVatPayer = prefs[Keys.IS_VAT_PAYER] ?: false,
            ofdUrl   = prefs[Keys.OFD_URL] ?: "",
            ofdToken = prefs[Keys.OFD_TOKEN] ?: ""
        )
    }

    suspend fun saveOrganization(org: Organization) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BIN]          = org.bin
            prefs[Keys.NTIN]         = org.ntin
            prefs[Keys.ORG_NAME]     = org.name
            prefs[Keys.ADDRESS]      = org.address
            prefs[Keys.IS_VAT_PAYER] = org.isVatPayer
            prefs[Keys.OFD_URL]      = org.ofdUrl
            prefs[Keys.OFD_TOKEN]    = org.ofdToken
        }
    }

    suspend fun getRnm(): String = context.dataStore.data.first()[Keys.RNM] ?: ""
    suspend fun saveRnm(rnm: String) {
        context.dataStore.edit { it[Keys.RNM] = rnm }
    }

    suspend fun getLanguage(): String = context.dataStore.data.first()[Keys.LANGUAGE] ?: "ru"
    suspend fun saveLanguage(lang: String) {
        context.dataStore.edit { it[Keys.LANGUAGE] = lang }
    }

    suspend fun getIsnaToken(): String = context.dataStore.data.first()[Keys.ISNA_TOKEN] ?: ""
    suspend fun saveIsnaToken(token: String) {
        context.dataStore.edit { it[Keys.ISNA_TOKEN] = token }
    }

    // PIN management: stored as SHA-256 hash
    suspend fun savePin(pin: String) {
        val hash = sha256(pin)
        context.dataStore.edit { it[Keys.PIN_HASH] = hash }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val stored = context.dataStore.data.first()[Keys.PIN_HASH] ?: return false
        return sha256(pin) == stored
    }

    suspend fun isPinSet(): Boolean {
        return context.dataStore.data.first()[Keys.PIN_HASH]?.isNotEmpty() ?: false
    }

    private fun sha256(input: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
