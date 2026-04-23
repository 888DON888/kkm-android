package kz.kkm.di

import android.content.Context
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kz.kkm.BuildConfig
import kz.kkm.data.local.*
import kz.kkm.data.local.dao.*
import kz.kkm.data.remote.IsnaApiService
import kz.kkm.data.remote.OfdApiService
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun providePassphraseProvider(@ApplicationContext ctx: Context) =
        DatabasePassphraseProvider(ctx)

    @Provides @Singleton
    fun provideDatabase(
        @ApplicationContext ctx: Context,
        passphraseProvider: DatabasePassphraseProvider
    ): KkmDatabase = KkmDatabase.create(ctx, passphraseProvider.getOrCreatePassphrase())

    @Provides fun provideShiftDao(db: KkmDatabase): ShiftDao = db.shiftDao()
    @Provides fun provideReceiptDao(db: KkmDatabase): ReceiptDao = db.receiptDao()
    @Provides fun provideCatalogDao(db: KkmDatabase): CatalogDao = db.catalogDao()
    @Provides fun provideEmployeeDao(db: KkmDatabase): EmployeeDao = db.employeeDao()
    @Provides fun provideTaxPeriodDao(db: KkmDatabase): TaxPeriodDao = db.taxPeriodDao()
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton @Named("ofd")
    fun provideOfdOkHttp(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
        }
        val pinner = if (BuildConfig.OFD_PIN_SHA256.isNotEmpty() && !BuildConfig.DEBUG) {
            CertificatePinner.Builder()
                .add("ofd.kgd.gov.kz", BuildConfig.OFD_PIN_SHA256)
                .build()
        } else null

        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .apply { pinner?.let { certificatePinner(it) } }
            .build()
    }

    @Provides @Singleton @Named("isna")
    fun provideIsnaOkHttp(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
        }
        val pinner = if (BuildConfig.ISNA_PIN_SHA256.isNotEmpty() && !BuildConfig.DEBUG) {
            CertificatePinner.Builder()
                .add("is.kgd.gov.kz", BuildConfig.ISNA_PIN_SHA256)
                .build()
        } else null

        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .apply { pinner?.let { certificatePinner(it) } }
            .build()
    }

    @Provides @Singleton
    fun provideOfdRetrofit(@Named("ofd") client: OkHttpClient): OfdApiService =
        Retrofit.Builder()
            .baseUrl(BuildConfig.OFD_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
            .build()
            .create(OfdApiService::class.java)

    @Provides @Singleton
    fun provideIsnaRetrofit(@Named("isna") client: OkHttpClient): IsnaApiService =
        Retrofit.Builder()
            .baseUrl(BuildConfig.ISNA_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
            .build()
            .create(IsnaApiService::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    fun provideContext(@ApplicationContext ctx: Context): Context = ctx
}
