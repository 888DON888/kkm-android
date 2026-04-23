package kz.kkm.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.*

// ─────────────────── OFD API ───────────────────

interface OfdApiService {

    @POST("document")
    suspend fun sendDocument(
        @Header("Authorization") token: String,
        @Header("X-KKM-BIN") bin: String,
        @Header("X-KKM-RNM") rnm: String,
        @Body request: OfdDocumentRequest
    ): Response<OfdDocumentResponse>

    @GET("document/{ticketId}/status")
    suspend fun getStatus(
        @Header("Authorization") token: String,
        @Path("ticketId") ticketId: String
    ): Response<OfdStatusResponse>

    @POST("shift/open")
    suspend fun openShift(
        @Header("Authorization") token: String,
        @Header("X-KKM-BIN") bin: String,
        @Body request: OfdShiftRequest
    ): Response<OfdDocumentResponse>

    @POST("shift/close")
    suspend fun closeShift(
        @Header("Authorization") token: String,
        @Header("X-KKM-BIN") bin: String,
        @Body request: OfdZReportRequest
    ): Response<OfdDocumentResponse>
}

// ─────────────────── OFD DTOs ───────────────────

data class OfdDocumentRequest(
    @SerializedName("docType")    val docType: Int,
    @SerializedName("ffdVersion") val ffdVersion: String = "2.0.3",
    @SerializedName("tlvData")    val tlvData: String,      // Base64-encoded TLV
    @SerializedName("fiscalSign") val fiscalSign: String,
    @SerializedName("docNumber")  val docNumber: Int,
    @SerializedName("shiftNumber")val shiftNumber: Int,
    @SerializedName("dateTime")   val dateTime: String,     // ISO-8601
    @SerializedName("totalAmount")val totalAmount: String,  // in tenge
    @SerializedName("bin")        val bin: String
)

data class OfdShiftRequest(
    @SerializedName("ffdVersion")  val ffdVersion: String = "2.0.3",
    @SerializedName("shiftNumber") val shiftNumber: Int,
    @SerializedName("openDateTime")val openDateTime: String,
    @SerializedName("bin")         val bin: String,
    @SerializedName("fiscalSign")  val fiscalSign: String
)

data class OfdZReportRequest(
    @SerializedName("ffdVersion")   val ffdVersion: String = "2.0.3",
    @SerializedName("shiftNumber")  val shiftNumber: Int,
    @SerializedName("closeDateTime")val closeDateTime: String,
    @SerializedName("totalSales")   val totalSales: String,
    @SerializedName("totalReturns") val totalReturns: String,
    @SerializedName("receiptsCount")val receiptsCount: Int,
    @SerializedName("bin")          val bin: String,
    @SerializedName("fiscalSign")   val fiscalSign: String
)

data class OfdDocumentResponse(
    @SerializedName("status")   val status: String,   // ACCEPTED, PROCESSED, REJECTED
    @SerializedName("ticketId") val ticketId: String?,
    @SerializedName("errors")   val errors: List<String>?
)

data class OfdStatusResponse(
    @SerializedName("ticketId") val ticketId: String,
    @SerializedName("status")   val status: String,
    @SerializedName("message")  val message: String?
)

// ─────────────────── ISNA API (910.00) ───────────────────

interface IsnaApiService {

    @Multipart
    @POST("declarations/submit")
    suspend fun submitDeclaration(
        @Header("Authorization")  authorization: String,
        @Header("X-Signature")    signature: String,
        @Header("X-Certificate")  certificate: String,
        @Part("formCode")         formCode: okhttp3.RequestBody,
        @Part("periodYear")       periodYear: okhttp3.RequestBody,
        @Part("periodHalf")       periodHalf: okhttp3.RequestBody,
        @Part("bin")              bin: okhttp3.RequestBody,
        @Part("signatureType")    signatureType: okhttp3.RequestBody,
        @Part                     file: okhttp3.MultipartBody.Part
    ): Response<IsnaResponse>

    @GET("declarations/{ticketId}/status")
    suspend fun getDeclarationStatus(
        @Header("Authorization") authorization: String,
        @Path("ticketId") ticketId: String
    ): Response<IsnaStatusResponse>

    @GET("declarations/{ticketId}/receipt")
    suspend fun getReceipt(
        @Header("Authorization") authorization: String,
        @Path("ticketId") ticketId: String
    ): Response<okhttp3.ResponseBody>

    @GET("declarations/history")
    suspend fun getHistory(
        @Header("Authorization") authorization: String,
        @Query("bin") bin: String,
        @Query("year") year: Int
    ): Response<List<IsnaHistoryItem>>
}

data class IsnaResponse(
    @SerializedName("status")   val status: String,
    @SerializedName("ticketId") val ticketId: String?,
    @SerializedName("errors")   val errors: List<IsnaError>?
)

data class IsnaStatusResponse(
    @SerializedName("ticketId")    val ticketId: String,
    @SerializedName("status")      val status: String,
    @SerializedName("regNumber")   val regNumber: String?,
    @SerializedName("processedAt") val processedAt: String?
)

data class IsnaError(
    @SerializedName("code")    val code: String,
    @SerializedName("field")   val field: String?,
    @SerializedName("message") val message: String
)

data class IsnaHistoryItem(
    @SerializedName("ticketId")   val ticketId: String,
    @SerializedName("formCode")   val formCode: String,
    @SerializedName("period")     val period: String,
    @SerializedName("status")     val status: String,
    @SerializedName("submittedAt")val submittedAt: String
)
