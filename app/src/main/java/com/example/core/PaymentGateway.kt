package com.example.core

import com.example.domain.model.PaymentProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class GatewayInitResult(
    val provider: PaymentProvider,
    val reference: String,
    val authorizationUrl: String,
    val isLiveGatewayConfigured: Boolean,
    val message: String
)

data class GatewayVerifyResult(
    val verified: Boolean,
    val reference: String,
    val amountNgn: Int,
    val currency: String,
    val channel: String,
    val message: String
)

interface PaymentGateway {
    val provider: PaymentProvider
    suspend fun initializeCheckout(
        email: String,
        amountNgn: Int,
        reference: String,
        courseTitle: String,
        backendBaseUrl: String
    ): GatewayInitResult

    suspend fun verifyOnBackend(
        reference: String,
        expectedAmountNgn: Int,
        backendBaseUrl: String
    ): GatewayVerifyResult
}

class PaystackGateway(
    private val httpClient: OkHttpClient = OkHttpClient()
) : PaymentGateway {
    override val provider: PaymentProvider = PaymentProvider.PAYSTACK

    override suspend fun initializeCheckout(
        email: String,
        amountNgn: Int,
        reference: String,
        courseTitle: String,
        backendBaseUrl: String
    ): GatewayInitResult = withContext(Dispatchers.IO) {
        val trimmedUrl = backendBaseUrl.trim().trimEnd('/')
        if (trimmedUrl.isBlank()) {
            return@withContext GatewayInitResult(
                provider = provider,
                reference = reference,
                authorizationUrl = "",
                isLiveGatewayConfigured = false,
                message = "Paystack backend Cloud Function URL is not configured yet. Set your verification server URL in Admin Settings or use OPay Bank Transfer."
            )
        }
        runCatching {
            val payload = JSONObject().apply {
                put("email", email)
                put("amountKobo", amountNgn.toLong() * 100L)
                put("currency", "NGN")
                put("reference", reference)
                put("courseTitle", courseTitle)
            }.toString()

            val request = Request.Builder()
                .url("$trimmedUrl/paystack/initialize")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    GatewayInitResult(
                        provider = provider,
                        reference = reference,
                        authorizationUrl = "",
                        isLiveGatewayConfigured = true,
                        message = "Gateway initialization failed (${response.code}): $bodyStr"
                    )
                } else {
                    val json = JSONObject(bodyStr)
                    val authUrl = json.optString("authorization_url", "")
                    GatewayInitResult(
                        provider = provider,
                        reference = reference,
                        authorizationUrl = authUrl,
                        isLiveGatewayConfigured = authUrl.isNotBlank(),
                        message = if (authUrl.isNotBlank()) "Paystack checkout ready" else "Missing authorization URL from backend"
                    )
                }
            }
        }.getOrElse { error ->
            GatewayInitResult(
                provider = provider,
                reference = reference,
                authorizationUrl = "",
                isLiveGatewayConfigured = true,
                message = error.localizedMessage ?: "Network error contacting payment backend"
            )
        }
    }

    override suspend fun verifyOnBackend(
        reference: String,
        expectedAmountNgn: Int,
        backendBaseUrl: String
    ): GatewayVerifyResult = withContext(Dispatchers.IO) {
        val trimmedUrl = backendBaseUrl.trim().trimEnd('/')
        if (trimmedUrl.isBlank()) {
            return@withContext GatewayVerifyResult(
                verified = false,
                reference = reference,
                amountNgn = expectedAmountNgn,
                currency = "NGN",
                channel = "card",
                message = "Backend verification URL missing. Never mark online payments paid without server verification."
            )
        }
        runCatching {
            val request = Request.Builder()
                .url("$trimmedUrl/paystack/verify?reference=$reference")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    GatewayVerifyResult(false, reference, 0, "NGN", "", "Verification HTTP ${response.code}")
                } else {
                    val json = JSONObject(bodyStr)
                    val status = json.optString("status", "failed")
                    val amountKobo = json.optLong("amountKobo", 0L)
                    val currency = json.optString("currency", "")
                    val channel = json.optString("channel", "card")
                    val amountNgn = (amountKobo / 100L).toInt()
                    val valid = status == "success" && currency == "NGN" && amountNgn >= expectedAmountNgn
                    GatewayVerifyResult(
                        verified = valid,
                        reference = reference,
                        amountNgn = amountNgn,
                        currency = currency,
                        channel = channel,
                        message = if (valid) "Payment verified on server" else "Amount/currency/status mismatch"
                    )
                }
            }
        }.getOrElse { e ->
            GatewayVerifyResult(false, reference, 0, "NGN", "", e.localizedMessage ?: "Verification error")
        }
    }
}

class FlutterwaveGateway : PaymentGateway {
    override val provider: PaymentProvider = PaymentProvider.FLUTTERWAVE

    override suspend fun initializeCheckout(
        email: String,
        amountNgn: Int,
        reference: String,
        courseTitle: String,
        backendBaseUrl: String
    ): GatewayInitResult = GatewayInitResult(
        provider = provider,
        reference = reference,
        authorizationUrl = "",
        isLiveGatewayConfigured = false,
        message = "Flutterwave adapter ready. Configure /flutterwave/initialize on your backend server to enable live checkout."
    )

    override suspend fun verifyOnBackend(
        reference: String,
        expectedAmountNgn: Int,
        backendBaseUrl: String
    ): GatewayVerifyResult = GatewayVerifyResult(
        verified = false,
        reference = reference,
        amountNgn = expectedAmountNgn,
        currency = "NGN",
        channel = "bank_transfer",
        message = "Flutterwave backend verification endpoint not configured."
    )
}
