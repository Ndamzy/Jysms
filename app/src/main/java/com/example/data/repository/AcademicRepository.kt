package com.example.data.repository

import com.example.core.Formatters
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.local.CachedAttendanceEntity
import com.example.data.local.CachedResultEntity
import com.example.data.local.JySmsDao
import com.example.domain.model.AcademicResult
import com.example.domain.model.AttendanceQrParser
import com.example.domain.model.AttendanceRecord
import com.example.domain.model.AttendanceSession
import com.example.domain.model.AttendanceStatus
import com.example.domain.model.Certificate
import com.example.domain.model.CertificateVerification
import com.example.domain.model.CompletionStatus
import com.example.domain.model.Course
import com.example.domain.model.Enrolment
import com.example.domain.model.EnrolmentStatus
import com.example.domain.model.Student
import com.example.domain.model.UserRole
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.tasks.await

class AcademicRepository(
    private val db: FirebaseFirestore,
    private val dao: JySmsDao
) {

    fun observeAttendance(currentUid: String, role: UserRole): Flow<List<AttendanceRecord>> {
        val query = if (role == UserRole.ADMINISTRATOR || role == UserRole.INSTRUCTOR) {
            db.collection("attendance")
        } else {
            db.collection("attendance").whereEqualTo("userId", currentUid)
        }
        return query.snapshots()
            .map { snap ->
                snap.toObjects(AttendanceRecord::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.dateIso }
            }
            .onEach { list ->
                runCatching { dao.upsertAttendance(list.map { CachedAttendanceEntity.fromDomain(it) }) }
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "attendance")
                throw error
            }
    }

    suspend fun recordAttendance(
        recordedByUid: String,
        enrolment: Enrolment,
        dateIso: String,
        status: AttendanceStatus,
        notes: String
    ): Result<Unit> = runCatching {
        val docId = "${enrolment.studentId}_${enrolment.courseId}_${dateIso.replace("-", "")}"
        val docRef = db.collection("attendance").document(docId)
        val snap = docRef.get().await()
        val payload = mutableMapOf<String, Any>(
            "userId" to enrolment.userId,
            "studentId" to enrolment.studentId,
            "studentName" to enrolment.studentName,
            "courseId" to enrolment.courseId,
            "courseTitle" to enrolment.courseTitle,
            "dateIso" to dateIso,
            "status" to status.name,
            "recordedByUid" to recordedByUid,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (notes.isNotBlank()) payload["notes"] = notes.trim().take(300)
        if (!snap.exists()) {
            payload["createdAt"] = FieldValue.serverTimestamp()
            docRef.set(payload).await()
        } else {
            docRef.update(payload).await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "attendance")
    }

    fun observeAttendanceSessions(): Flow<List<AttendanceSession>> {
        return db.collection("attendance_sessions")
            .snapshots()
            .map { snap ->
                snap.toObjects(AttendanceSession::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "attendance_sessions")
                throw error
            }
    }

    suspend fun createOrActivateQrSession(
        staffUid: String,
        staffName: String,
        course: Course,
        dateIso: String
    ): Result<AttendanceSession> = runCatching {
        val cleanDate = dateIso.trim().ifBlank { Formatters.todayIsoDate() }
        val sessionId = "sess_${course.id}_${cleanDate.replace("-", "")}"
        val docRef = db.collection("attendance_sessions").document(sessionId)
        val snap = docRef.get().await()
        val sessionCode = if (snap.exists()) {
            snap.getString("sessionCode")?.takeIf { it.isNotBlank() }
                ?: Formatters.generateReference("QR")
        } else {
            Formatters.generateReference("QR")
        }

        val payload = mutableMapOf<String, Any>(
            "sessionCode" to sessionCode,
            "courseId" to course.id,
            "courseTitle" to course.title,
            "dateIso" to cleanDate,
            "isActive" to true,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!snap.exists()) {
            payload["createdByUid"] = staffUid
            if (staffName.isNotBlank()) payload["createdByName"] = staffName.trim().take(120)
            payload["createdAt"] = FieldValue.serverTimestamp()
            docRef.set(payload).await()
        } else {
            docRef.update(payload).await()
        }

        AttendanceSession(
            id = sessionId,
            sessionCode = sessionCode,
            courseId = course.id,
            courseTitle = course.title,
            dateIso = cleanDate,
            createdByUid = snap.getString("createdByUid") ?: staffUid,
            createdByName = snap.getString("createdByName") ?: staffName,
            isActive = true
        )
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "attendance_sessions")
    }

    suspend fun setQrSessionActive(sessionId: String, isActive: Boolean): Result<Unit> = runCatching {
        db.collection("attendance_sessions").document(sessionId)
            .update(
                mapOf(
                    "isActive" to isActive,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "attendance_sessions/$sessionId")
    }

    suspend fun markAttendanceViaQrPayload(
        studentUid: String,
        enrolments: List<Enrolment>,
        rawPayload: String
    ): Result<AttendanceRecord> = runCatching {
        val parsed = AttendanceQrParser.parse(rawPayload)
            ?: error("Unrecognized QR code. Please scan an official JY-COMPUTERS class attendance QR code.")

        val sessionSnap = db.collection("attendance_sessions").document(parsed.sessionId).get().await()
        if (!sessionSnap.exists()) {
            error("Attendance QR session was not found or has expired.")
        }
        val session = sessionSnap.toObject(AttendanceSession::class.java, ServerTimestampBehavior.ESTIMATE)
            ?: error("Unable to read attendance QR session.")

        if (!session.isActive) {
            error("This QR attendance session for ${session.courseTitle} has been closed by the instructor.")
        }
        if (session.courseId != parsed.courseId || session.dateIso != parsed.dateIso) {
            error("QR code details do not match the active session.")
        }

        val enrolment = enrolments.firstOrNull {
            it.userId == studentUid &&
                it.courseId == session.courseId &&
                it.enrolmentStatus != EnrolmentStatus.CANCELLED.name &&
                it.enrolmentStatus != EnrolmentStatus.SUSPENDED.name
        } ?: error("You are not enrolled in ${session.courseTitle}.")

        val docId = "${enrolment.studentId}_${session.courseId}_${session.dateIso.replace("-", "")}"
        val docRef = db.collection("attendance").document(docId)
        val existingSnap = docRef.get().await()
        if (existingSnap.exists()) {
            error("Your attendance for ${session.courseTitle} on ${session.dateIso} is already recorded.")
        }

        val note = "Marked via QR Scan (${session.sessionCode})"
        val payload = mapOf(
            "userId" to studentUid,
            "studentId" to enrolment.studentId,
            "studentName" to enrolment.studentName,
            "courseId" to session.courseId,
            "courseTitle" to session.courseTitle,
            "dateIso" to session.dateIso,
            "status" to AttendanceStatus.PRESENT.name,
            "recordedByUid" to studentUid,
            "sessionId" to session.id,
            "notes" to note,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()

        AttendanceRecord(
            id = docId,
            userId = studentUid,
            studentId = enrolment.studentId,
            studentName = enrolment.studentName,
            courseId = session.courseId,
            courseTitle = session.courseTitle,
            dateIso = session.dateIso,
            status = AttendanceStatus.PRESENT.name,
            recordedByUid = studentUid,
            sessionId = session.id,
            notes = note
        )
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "attendance")
    }

    fun observeResults(currentUid: String, role: UserRole): Flow<List<AcademicResult>> {
        val query = if (role == UserRole.ADMINISTRATOR || role == UserRole.INSTRUCTOR) {
            db.collection("results")
        } else {
            db.collection("results").whereEqualTo("userId", currentUid)
        }
        return query.snapshots()
            .map { snap ->
                snap.toObjects(AcademicResult::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .onEach { list ->
                runCatching { dao.upsertResults(list.map { CachedResultEntity.fromDomain(it) }) }
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "results")
                throw error
            }
    }

    suspend fun saveResult(
        enteredByUid: String,
        enrolment: Enrolment,
        caScore: Int,
        examScore: Int,
        comment: String
    ): Result<Unit> = runCatching {
        val ca = caScore.coerceIn(0, 40)
        val exam = examScore.coerceIn(0, 60)
        val total = ca + exam
        val (grade, remark) = Formatters.computeGrade(total)

        val docId = "${enrolment.studentId}_${enrolment.courseId}"
        val docRef = db.collection("results").document(docId)
        val snap = docRef.get().await()
        val payload = mutableMapOf<String, Any>(
            "userId" to enrolment.userId,
            "studentId" to enrolment.studentId,
            "studentName" to enrolment.studentName,
            "courseId" to enrolment.courseId,
            "courseTitle" to enrolment.courseTitle,
            "caScore" to ca,
            "examScore" to exam,
            "totalScore" to total,
            "grade" to grade,
            "remark" to remark,
            "enteredByUid" to enteredByUid,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (comment.isNotBlank()) payload["instructorComment"] = comment.trim().take(400)
        if (!snap.exists()) {
            payload["createdAt"] = FieldValue.serverTimestamp()
            docRef.set(payload).await()
        } else {
            docRef.update(payload).await()
        }

        runCatching {
            db.collection("notifications").document().set(
                mapOf(
                    "userId" to enrolment.userId,
                    "title" to "Result Published: ${enrolment.courseTitle}",
                    "body" to "Total Score: $total/100 ($grade - $remark)",
                    "category" to "RESULT",
                    "isRead" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "results")
    }

    fun observeCertificates(currentUid: String, role: UserRole): Flow<List<Certificate>> {
        val query = if (role == UserRole.ADMINISTRATOR || role == UserRole.INSTRUCTOR) {
            db.collection("certificates")
        } else {
            db.collection("certificates").whereEqualTo("userId", currentUid)
        }
        return query.snapshots()
            .map { snap ->
                snap.toObjects(Certificate::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "certificates")
                throw error
            }
    }

    suspend fun issueCertificate(
        adminUid: String,
        enrolment: Enrolment,
        student: Student?,
        grade: String
    ): Result<Certificate> = runCatching {
        val certNumber = Formatters.generateReference("JYC")
        val completionDate = Formatters.todayIsoDate()
        val studentCode = student?.studentCode?.ifBlank { enrolment.studentId } ?: enrolment.studentId
        val fullName = student?.fullName?.ifBlank { enrolment.studentName } ?: enrolment.studentName
        val certRef = db.collection("certificates").document(certNumber)
        val verifyRef = db.collection("certificate_verifications").document(certNumber)
        val enrRef = db.collection("enrolments").document(enrolment.id)

        val batch = db.batch()
        batch.set(
            certRef,
            mapOf(
                "certificateNumber" to certNumber,
                "userId" to enrolment.userId,
                "studentId" to enrolment.studentId,
                "studentCode" to studentCode,
                "studentFullName" to fullName,
                "courseId" to enrolment.courseId,
                "courseTitle" to enrolment.courseTitle,
                "completionDate" to completionDate,
                "grade" to grade.ifBlank { "Pass" },
                "issuedByUid" to adminUid,
                "isRevoked" to false,
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
        batch.set(
            verifyRef,
            mapOf(
                "certificateNumber" to certNumber,
                "studentFullName" to fullName,
                "studentCode" to studentCode,
                "courseTitle" to enrolment.courseTitle,
                "completionDate" to completionDate,
                "isValid" to true,
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            enrRef,
            mapOf(
                "completionStatus" to CompletionStatus.COMPLETED.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.set(
            db.collection("notifications").document(),
            mapOf(
                "userId" to enrolment.userId,
                "title" to "Certificate Ready!",
                "body" to "Your certificate ($certNumber) for ${enrolment.courseTitle} is now available for download.",
                "category" to "CERTIFICATE",
                "isRead" to false,
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
        batch.commit().await()

        Certificate(
            id = certNumber,
            certificateNumber = certNumber,
            userId = enrolment.userId,
            studentId = enrolment.studentId,
            studentCode = studentCode,
            studentFullName = fullName,
            courseId = enrolment.courseId,
            courseTitle = enrolment.courseTitle,
            completionDate = completionDate,
            grade = grade.ifBlank { "Pass" },
            issuedByUid = adminUid,
            isRevoked = false
        )
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "certificates")
    }

    suspend fun verifyCertificateByNumber(certNumber: String): Result<CertificateVerification?> = runCatching {
        val clean = certNumber.trim().uppercase()
        require(clean.matches(Regex("^[A-Za-z0-9_\\-]+$"))) { "Invalid certificate number format." }
        val snap = db.collection("certificate_verifications").document(clean).get().await()
        if (!snap.exists()) null else snap.toObject(CertificateVerification::class.java, ServerTimestampBehavior.ESTIMATE)
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.GET, "certificate_verifications/$certNumber")
    }
}
