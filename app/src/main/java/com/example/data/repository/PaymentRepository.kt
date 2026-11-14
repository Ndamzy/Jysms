package com.example.data.repository

import com.example.core.Formatters
import com.example.core.InstitutionConfig
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.domain.model.CompletionStatus
import com.example.domain.model.Enrolment
import com.example.domain.model.EnrolmentStatus
import com.example.domain.model.Payment
import com.example.domain.model.PaymentProvider
import com.example.domain.model.PaymentStatus
import com.example.domain.model.PaymentSubmission
import com.example.domain.model.PaymentTxStatus
import com.example.domain.model.SubmissionStatus
import com.example.domain.model.UserRole
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class PaymentRepository(private val db: FirebaseFirestore) {

    fun observePayments(currentUid: String, role: UserRole): Flow<List<Payment>> {
        val query = if (role == UserRole.ADMINISTRATOR) {
            db.collection("payments")
        } else {
            db.collection("payments").whereEqualTo("userId", currentUid)
        }
        return query.snapshots()
            .map { snap ->
                snap.toObjects(Payment::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "payments")
                throw error
            }
    }

    fun observePaymentSubmissions(currentUid: String, role: UserRole): Flow<List<PaymentSubmission>> {
        val query = if (role == UserRole.ADMINISTRATOR) {
            db.collection("payment_submissions")
        } else {
            db.collection("payment_submissions").whereEqualTo("userId", currentUid)
        }
        return query.snapshots()
            .map { snap ->
                snap.toObjects(PaymentSubmission::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "payment_submissions")
                throw error
            }
    }

    suspend fun recordPendingGatewayPayment(
        enrolment: Enrolment,
        amountNgn: Long,
        provider: PaymentProvider,
        reference: String,
        channel: String
    ): Result<String> = runCatching {
        val docRef = db.collection("payments").document()
        val payload = mapOf(
            "reference" to reference,
            "userId" to enrolment.userId,
            "studentId" to enrolment.studentId,
            "studentName" to enrolment.studentName,
            "enrolmentId" to enrolment.id,
            "courseId" to enrolment.courseId,
            "courseTitle" to enrolment.courseTitle,
            "amountNgn" to amountNgn,
            "currency" to "NGN",
            "provider" to provider.name,
            "channel" to channel,
            "status" to PaymentTxStatus.PENDING.name,
            "receiptNumber" to Formatters.generateReference("RCP"),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()
        docRef.id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "payments")
    }

    suspend fun submitOpayBankTransfer(
        enrolment: Enrolment,
        amountNgn: Long,
        transferReference: String,
        receiptProofUri: String
    ): Result<String> = runCatching {
        val docRef = db.collection("payment_submissions").document()
        val submissionRef = Formatters.generateReference("OPAY")
        val payload = mutableMapOf<String, Any>(
            "submissionRef" to submissionRef,
            "userId" to enrolment.userId,
            "studentId" to enrolment.studentId,
            "studentName" to enrolment.studentName,
            "enrolmentId" to enrolment.id,
            "courseTitle" to enrolment.courseTitle,
            "amountNgn" to amountNgn,
            "bankName" to InstitutionConfig.OPAY_BANK_NAME,
            "accountNumber" to InstitutionConfig.OPAY_ACCOUNT_NUMBER,
            "transferReference" to transferReference.trim(),
            "status" to SubmissionStatus.PENDING_VERIFICATION.name,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (receiptProofUri.isNotBlank()) payload["receiptProofUri"] = receiptProofUri.take(600)
        docRef.set(payload).await()
        submissionRef
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "payment_submissions")
    }

    suspend fun adminVerifyOpaySubmission(
        adminUid: String,
        submission: PaymentSubmission,
        enrolment: Enrolment?,
        approve: Boolean,
        notes: String
    ): Result<Unit> = runCatching {
        val subRef = db.collection("payment_submissions").document(submission.id)
        if (!approve) {
            subRef.update(
                mapOf(
                    "status" to SubmissionStatus.REJECTED.name,
                    "reviewerNotes" to notes.trim().take(300),
                    "reviewedBy" to adminUid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            return@runCatching
        }

        val batch = db.batch()
        batch.update(
            subRef,
            mapOf(
                "status" to SubmissionStatus.VERIFIED.name,
                "reviewerNotes" to notes.ifBlank { "Verified by Admin" }.take(300),
                "reviewedBy" to adminUid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        val payRef = db.collection("payments").document()
        batch.set(
            payRef,
            mapOf(
                "reference" to submission.transferReference,
                "userId" to submission.userId,
                "studentId" to submission.studentId,
                "studentName" to submission.studentName,
                "enrolmentId" to submission.enrolmentId,
                "courseId" to (enrolment?.courseId ?: ""),
                "courseTitle" to submission.courseTitle,
                "amountNgn" to submission.amountNgn,
                "currency" to "NGN",
                "provider" to PaymentProvider.OPAY_TRANSFER.name,
                "channel" to "OPay Bank Transfer",
                "status" to PaymentTxStatus.SUCCESSFUL.name,
                "receiptNumber" to Formatters.generateReference("RCP"),
                "verifiedBy" to adminUid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        if (enrolment != null) {
            val newPaid = enrolment.amountPaidNgn + submission.amountNgn
            val payStatus = if (newPaid >= enrolment.courseFeeNgn) {
                PaymentStatus.PAID.name
            } else {
                PaymentStatus.PARTIAL.name
            }
            val enrRef = db.collection("enrolments").document(enrolment.id)
            batch.update(
                enrRef,
                mapOf(
                    "amountPaidNgn" to newPaid,
                    "paymentStatus" to payStatus,
                    "enrolmentStatus" to EnrolmentStatus.CONFIRMED.name,
                    "completionStatus" to if (enrolment.completionStatus == CompletionStatus.NOT_STARTED.name) {
                        CompletionStatus.IN_PROGRESS.name
                    } else {
                        enrolment.completionStatus
                    },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val groupId = "grp_${enrolment.courseId}"
            batch.set(
                db.collection("group_members").document("${groupId}_${enrolment.userId}"),
                mapOf(
                    "groupId" to groupId,
                    "userId" to enrolment.userId,
                    "displayName" to enrolment.studentName.ifBlank { "Student" },
                    "role" to "MEMBER",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
        }

        batch.set(
            db.collection("notifications").document(),
            mapOf(
                "userId" to submission.userId,
                "title" to "OPay Transfer Verified",
                "body" to "Your transfer of ${Formatters.formatNgn(submission.amountNgn)} for ${submission.courseTitle} has been confirmed.",
                "category" to "PAYMENT",
                "isRead" to false,
                "createdAt" to FieldValue.serverTimestamp()
            )
        )

        batch.commit().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "payment_submissions/${submission.id}")
    }

    suspend fun adminRecordVerifiedPayment(
        adminUid: String,
        enrolment: Enrolment,
        amountNgn: Long,
        provider: PaymentProvider,
        reference: String
    ): Result<Unit> = runCatching {
        val batch = db.batch()
        val payRef = db.collection("payments").document()
        batch.set(
            payRef,
            mapOf(
                "reference" to reference,
                "userId" to enrolment.userId,
                "studentId" to enrolment.studentId,
                "studentName" to enrolment.studentName,
                "enrolmentId" to enrolment.id,
                "courseId" to enrolment.courseId,
                "courseTitle" to enrolment.courseTitle,
                "amountNgn" to amountNgn,
                "currency" to "NGN",
                "provider" to provider.name,
                "channel" to provider.name,
                "status" to PaymentTxStatus.SUCCESSFUL.name,
                "receiptNumber" to Formatters.generateReference("RCP"),
                "verifiedBy" to adminUid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        val newPaid = enrolment.amountPaidNgn + amountNgn
        val payStatus = if (newPaid >= enrolment.courseFeeNgn) PaymentStatus.PAID.name else PaymentStatus.PARTIAL.name
        batch.update(
            db.collection("enrolments").document(enrolment.id),
            mapOf(
                "amountPaidNgn" to newPaid,
                "paymentStatus" to payStatus,
                "enrolmentStatus" to EnrolmentStatus.CONFIRMED.name,
                "completionStatus" to if (enrolment.completionStatus == CompletionStatus.NOT_STARTED.name) {
                    CompletionStatus.IN_PROGRESS.name
                } else {
                    enrolment.completionStatus
                },
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.commit().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "payments")
    }

    suspend fun adminUpdatePaymentStatus(
        adminUid: String,
        payment: Payment,
        newStatus: PaymentTxStatus
    ): Result<Unit> = runCatching {
        db.collection("payments").document(payment.id).update(
            mapOf(
                "status" to newStatus.name,
                "verifiedBy" to adminUid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "payments/${payment.id}")
    }
}
