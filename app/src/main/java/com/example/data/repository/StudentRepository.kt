package com.example.data.repository

import com.example.core.Formatters
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.domain.model.Student
import com.example.domain.model.StudentStatus
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

class StudentRepository(private val db: FirebaseFirestore) {

    fun observeStudents(currentUid: String, role: UserRole): Flow<List<Student>> {
        val query = if (role == UserRole.ADMINISTRATOR || role == UserRole.INSTRUCTOR) {
            db.collection("students")
        } else {
            db.collection("students").whereEqualTo("userId", currentUid)
        }
        return query.snapshots()
            .map { snap ->
                snap.toObjects(Student::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.fullName.lowercase() }
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "students")
                throw error
            }
    }

    suspend fun saveStudentProfile(
        student: Student,
        isAdminOrInstructor: Boolean,
        existingStudents: List<Student>
    ): Result<String> = runCatching {
        val normalizedEmail = student.email.trim().lowercase()
        val normalizedPhone = student.phoneNumber.trim()
        val duplicate = existingStudents.firstOrNull { other ->
            other.id != student.id &&
                other.status != StudentStatus.ARCHIVED.name &&
                (other.email.trim().lowercase() == normalizedEmail ||
                    (normalizedPhone.isNotBlank() && other.phoneNumber.trim() == normalizedPhone))
        }
        require(duplicate == null) {
            "Duplicate student registration detected for ${duplicate?.fullName ?: normalizedEmail}."
        }

        val docId = student.id.ifBlank {
            if (!isAdminOrInstructor) student.userId else db.collection("students").document().id
        }
        val docRef = db.collection("students").document(docId)
        val studentCode = student.studentCode.ifBlank { Formatters.generateStudentCode() }
        val regDate = student.registrationDate.ifBlank { Formatters.todayIsoDate() }

        val existingSnap = docRef.get().await()
        if (!existingSnap.exists()) {
            val payload = buildStudentMap(
                student.copy(
                    studentCode = studentCode,
                    registrationDate = regDate,
                    status = student.status.ifBlank { StudentStatus.ACTIVE.name }
                ),
                includeCreatedAt = true
            )
            docRef.set(payload).await()
        } else {
            val payload = buildStudentMap(
                student.copy(studentCode = studentCode, registrationDate = regDate),
                includeCreatedAt = false
            )
            docRef.update(payload).await()
        }

        if (student.userId == docId) {
            runCatching {
                db.collection("directory").document(student.userId).update(
                    mapOf(
                        "displayName" to student.fullName.trim().take(120),
                        "studentCode" to studentCode,
                        "photoUrl" to student.passportPhotoUri.take(600),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }
        docId
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "students")
    }

    suspend fun updateStudentStatus(studentId: String, status: StudentStatus): Result<Unit> = runCatching {
        val docRef = db.collection("students").document(studentId)
        docRef.update(
            mapOf(
                "status" to status.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "students/$studentId")
    }

    suspend fun deleteStudent(studentId: String): Result<Unit> = runCatching {
        db.collection("students").document(studentId).delete().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.DELETE, "students/$studentId")
    }

    private fun buildStudentMap(student: Student, includeCreatedAt: Boolean): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "userId" to student.userId,
            "studentCode" to student.studentCode,
            "fullName" to student.fullName.trim(),
            "gender" to student.gender,
            "phoneNumber" to student.phoneNumber.trim(),
            "email" to student.email.trim(),
            "status" to student.status,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (includeCreatedAt) map["createdAt"] = FieldValue.serverTimestamp()
        if (student.passportPhotoUri.isNotBlank()) map["passportPhotoUri"] = student.passportPhotoUri.take(600)
        if (student.dateOfBirth.isNotBlank()) map["dateOfBirth"] = student.dateOfBirth.trim()
        if (student.residentialAddress.isNotBlank()) map["residentialAddress"] = student.residentialAddress.trim()
        if (student.nextOfKin.isNotBlank()) map["nextOfKin"] = student.nextOfKin.trim()
        if (student.nextOfKinPhone.isNotBlank()) map["nextOfKinPhone"] = student.nextOfKinPhone.trim()
        if (student.registrationDate.isNotBlank()) map["registrationDate"] = student.registrationDate
        if (student.additionalInfo.isNotBlank()) map["additionalInfo"] = student.additionalInfo.trim()
        return map
    }
}
