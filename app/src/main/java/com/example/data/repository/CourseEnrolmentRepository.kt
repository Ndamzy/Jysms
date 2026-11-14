package com.example.data.repository

import com.example.core.Formatters
import com.example.core.InstitutionConfig
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.local.CachedCourseEntity
import com.example.data.local.CachedEnrolmentEntity
import com.example.data.local.JySmsDao
import com.example.domain.model.CompletionStatus
import com.example.domain.model.Course
import com.example.domain.model.Enrolment
import com.example.domain.model.EnrolmentStatus
import com.example.domain.model.GroupType
import com.example.domain.model.PaymentStatus
import com.example.domain.model.PreloadedCourses
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

class CourseEnrolmentRepository(
    private val db: FirebaseFirestore,
    private val dao: JySmsDao
) {

    fun observeCourses(role: UserRole): Flow<List<Course>> {
        val query = if (role == UserRole.ADMINISTRATOR || role == UserRole.INSTRUCTOR) {
            db.collection("courses")
        } else {
            db.collection("courses").whereEqualTo("isAvailable", true)
        }
        return query.snapshots()
            .map { snap ->
                val remote = snap.toObjects(Course::class.java, ServerTimestampBehavior.ESTIMATE)
                val merged = if (remote.isEmpty()) {
                    PreloadedCourses.OFFICIAL_CATALOG
                } else {
                    val byId = remote.associateBy { it.id }
                    val defaultsMissing = PreloadedCourses.OFFICIAL_CATALOG.filter { it.id !in byId }
                    (remote + defaultsMissing).filter { !it.isArchived }
                }
                merged.sortedBy { it.code }
            }
            .onEach { list ->
                runCatching { dao.upsertCourses(list.map { CachedCourseEntity.fromDomain(it) }) }
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "courses")
                throw error
            }
    }

    fun observeCachedCourses(): Flow<List<Course>> =
        dao.observeCourses().map { entities ->
            if (entities.isEmpty()) PreloadedCourses.OFFICIAL_CATALOG else entities.map { it.toDomain() }
        }

    suspend fun seedDefaultCatalogAndGroupsIfAdmin() {
        runCatching {
            val existingCourses = db.collection("courses").get().await()
            val existingIds = existingCourses.documents.map { it.id }.toSet()
            val batch = db.batch()
            var hasWrites = false

            PreloadedCourses.OFFICIAL_CATALOG.forEach { course ->
                if (course.id !in existingIds) {
                    val ref = db.collection("courses").document(course.id)
                    batch.set(ref, courseMap(course, includeCreatedAt = true))
                    hasWrites = true
                }
                val groupId = "grp_${course.id}"
                val groupRef = db.collection("group_chats").document(groupId)
                batch.set(
                    groupRef,
                    mapOf(
                        "title" to "${course.title} Group",
                        "courseId" to course.id,
                        "groupType" to GroupType.COURSE_GROUP.name,
                        "lastMessageText" to "Welcome to the ${course.title} learning community.",
                        "isActive" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                hasWrites = true
            }

            val communityRef = db.collection("group_chats").document(InstitutionConfig.GENERAL_COMMUNITY_GROUP_ID)
            batch.set(
                communityRef,
                mapOf(
                    "title" to "JY-COMPUTERS General Community",
                    "groupType" to GroupType.GENERAL_COMMUNITY.name,
                    "lastMessageText" to "Share coding tips, hardware insights, and connect with fellow students.",
                    "isActive" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (hasWrites) batch.commit().await()
        }
    }

    suspend fun saveCourse(course: Course): Result<Unit> = runCatching {
        val id = course.id.ifBlank { db.collection("courses").document().id }
        val docRef = db.collection("courses").document(id)
        val snap = docRef.get().await()
        if (!snap.exists()) {
            docRef.set(courseMap(course.copy(id = id), includeCreatedAt = true)).await()
            db.collection("group_chats").document("grp_$id").set(
                mapOf(
                    "title" to "${course.title} Group",
                    "courseId" to id,
                    "groupType" to GroupType.COURSE_GROUP.name,
                    "lastMessageText" to "Official course group for ${course.title}.",
                    "isActive" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            docRef.update(courseMap(course.copy(id = id), includeCreatedAt = false)).await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "courses")
    }

    suspend fun deleteOrArchiveCourse(courseId: String, archiveOnly: Boolean): Result<Unit> = runCatching {
        val ref = db.collection("courses").document(courseId)
        if (archiveOnly) {
            ref.update(
                mapOf(
                    "isArchived" to true,
                    "isAvailable" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            ref.delete().await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.DELETE, "courses/$courseId")
    }

    fun observeEnrolments(currentUid: String, role: UserRole): Flow<List<Enrolment>> {
        val query = if (role == UserRole.ADMINISTRATOR || role == UserRole.INSTRUCTOR) {
            db.collection("enrolments")
        } else {
            db.collection("enrolments").whereEqualTo("userId", currentUid)
        }
        return query.snapshots()
            .map { snap ->
                snap.toObjects(Enrolment::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .onEach { list ->
                runCatching { dao.upsertEnrolments(list.map { CachedEnrolmentEntity.fromDomain(it) }) }
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "enrolments")
                throw error
            }
    }

    fun observeCachedEnrolments(uid: String): Flow<List<Enrolment>> =
        dao.observeEnrolmentsForUser(uid).map { it.map { e -> e.toDomain() } }

    suspend fun createEnrolment(
        student: Student,
        course: Course,
        instalmentPlan: String = "Full Payment"
    ): Result<String> = runCatching {
        val docRef = db.collection("enrolments").document()
        val refCode = Formatters.generateReference("ENR")
        val regDate = Formatters.todayIsoDate()
        val expectedEnd = Formatters.addMonthsIsoDate(course.durationMonths.toInt().coerceAtLeast(1))
        val payload = mapOf(
            "reference" to refCode,
            "userId" to student.userId,
            "studentId" to student.id,
            "studentName" to student.fullName,
            "courseId" to course.id,
            "courseTitle" to course.title,
            "courseFeeNgn" to course.feeNgn,
            "amountPaidNgn" to 0L,
            "instructorId" to course.instructorId,
            "instructorName" to course.instructorName,
            "registrationDate" to regDate,
            "startDate" to regDate,
            "expectedCompletionDate" to expectedEnd,
            "paymentStatus" to PaymentStatus.UNPAID.name,
            "enrolmentStatus" to EnrolmentStatus.PENDING.name,
            "completionStatus" to CompletionStatus.NOT_STARTED.name,
            "instalmentPlan" to instalmentPlan,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { !(it is String && it.isBlank()) }

        docRef.set(payload).await()
        docRef.id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "enrolments")
    }

    suspend fun confirmEnrolmentAndJoinGroup(enrolment: Enrolment): Result<Unit> = runCatching {
        val enrRef = db.collection("enrolments").document(enrolment.id)
        enrRef.update(
            mapOf(
                "enrolmentStatus" to EnrolmentStatus.CONFIRMED.name,
                "completionStatus" to if (enrolment.completionStatus == CompletionStatus.NOT_STARTED.name) {
                    CompletionStatus.IN_PROGRESS.name
                } else {
                    enrolment.completionStatus
                },
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        val groupId = "grp_${enrolment.courseId}"
        val memberId = "${groupId}_${enrolment.userId}"
        db.collection("group_members").document(memberId).set(
            mapOf(
                "groupId" to groupId,
                "userId" to enrolment.userId,
                "displayName" to enrolment.studentName.ifBlank { "Student" },
                "role" to "MEMBER",
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "enrolments/${enrolment.id}")
    }

    suspend fun updateCompletionStatus(enrolmentId: String, status: CompletionStatus): Result<Unit> = runCatching {
        db.collection("enrolments").document(enrolmentId).update(
            mapOf(
                "completionStatus" to status.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "enrolments/$enrolmentId")
    }

    private fun courseMap(course: Course, includeCreatedAt: Boolean): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "code" to course.code.trim(),
            "title" to course.title.trim(),
            "description" to course.description.trim(),
            "durationLabel" to course.durationLabel.trim(),
            "durationMonths" to course.durationMonths,
            "feeNgn" to course.feeNgn,
            "schedule" to course.schedule.trim(),
            "instructorName" to course.instructorName.trim(),
            "isAvailable" to course.isAvailable,
            "isArchived" to course.isArchived,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (includeCreatedAt) map["createdAt"] = FieldValue.serverTimestamp()
        if (course.instructorId.isNotBlank()) map["instructorId"] = course.instructorId
        return map
    }
}
