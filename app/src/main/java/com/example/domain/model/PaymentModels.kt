package com.example.domain.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class PaymentProvider { PAYSTACK, FLUTTERWAVE, OPAY_TRANSFER, CASH }
enum class PaymentTxStatus { PENDING, SUCCESSFUL, FAILED, REFUNDED }
enum class SubmissionStatus { PENDING_VERIFICATION, VERIFIED, REJECTED }

@IgnoreExtraProperties
data class Payment(
    @DocumentId val id: String = "",
    val reference: String = "",
    val userId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val enrolmentId: String = "",
    val courseId: String = "",
    val courseTitle: String = "",
    val amountNgn: Long = 0L,
    val currency: String = "NGN",
    val provider: String = PaymentProvider.PAYSTACK.name,
    val channel: String = "",
    val status: String = PaymentTxStatus.PENDING.name,
    val receiptNumber: String = "",
    val verifiedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PaymentSubmission(
    @DocumentId val id: String = "",
    val submissionRef: String = "",
    val userId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val enrolmentId: String = "",
    val courseTitle: String = "",
    val amountNgn: Long = 0L,
    val bankName: String = "OPay",
    val accountNumber: String = "6118366524",
    val transferReference: String = "",
    val receiptProofUri: String = "",
    val status: String = SubmissionStatus.PENDING_VERIFICATION.name,
    val reviewerNotes: String = "",
    val reviewedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
