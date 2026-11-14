package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.base.FirestoreEmulatorTestBase
import com.example.data.local.JySmsDatabase
import com.example.domain.model.AttendanceQrParser
import com.example.domain.model.PreloadedCourses
import com.example.domain.model.Student
import com.example.domain.model.UserRole
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class JySmsRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private lateinit var localDb: JySmsDatabase

    @Before
    fun initRoom() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        localDb = Room.inMemoryDatabaseBuilder(context, JySmsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeRoom() {
        localDb.close()
    }

    @Test
    fun studentRegistration_andEnrolment_succeedsForOwnerAndRejectsCrossUser() = runBlocking {
        val aliceUid = signInTestUser("alice_${UUID.randomUUID()}@jytest.com")
        val studentRepo = StudentRepository(firestore)
        val courseRepo = CourseEnrolmentRepository(firestore, localDb.jySmsDao())

        val student = Student(
            id = aliceUid,
            userId = aliceUid,
            fullName = "Alice Okafor",
            gender = "Female",
            phoneNumber = "0803${(1000000..9999999).random()}",
            email = "alice_${UUID.randomUUID()}@jytest.com"
        )
        val saveResult = withTimeout(TIMEOUT_MS) {
            studentRepo.saveStudentProfile(student, isAdminOrInstructor = false, existingStudents = emptyList())
        }
        assertTrue("Student save failed: ${saveResult.exceptionOrNull()}", saveResult.isSuccess)

        val courses = PreloadedCourses.OFFICIAL_CATALOG
        val enrolmentResult = withTimeout(TIMEOUT_MS) {
            courseRepo.createEnrolment(student.copy(id = saveResult.getOrThrow()), courses.first())
        }
        assertTrue("Enrolment create failed: ${enrolmentResult.exceptionOrNull()}", enrolmentResult.isSuccess)

        val aliceEnrolments = withTimeout(FLOW_TIMEOUT_MS) {
            courseRepo.observeEnrolments(aliceUid, UserRole.STUDENT).first { it.isNotEmpty() }
        }
        assertTrue(aliceEnrolments.any { it.id == enrolmentResult.getOrThrow() })

        val bobUid = signInTestUser("bob_${UUID.randomUUID()}@jytest.com")
        val bobEnrolments = withTimeout(FLOW_TIMEOUT_MS) {
            courseRepo.observeEnrolments(bobUid, UserRole.STUDENT).first()
        }
        assertTrue("Bob must not see Alice's enrolments", bobEnrolments.none { it.userId == aliceUid })
    }

    @Test
    fun opaySubmission_succeedsAsPendingForOwner_andUnauthenticatedQueryFails() = runBlocking {
        val aliceUid = signInTestUser("alice_pay_${UUID.randomUUID()}@jytest.com")
        val courseRepo = CourseEnrolmentRepository(firestore, localDb.jySmsDao())
        val paymentRepo = PaymentRepository(firestore)

        val student = Student(id = aliceUid, userId = aliceUid, fullName = "Alice Pay")
        val enrId = withTimeout(TIMEOUT_MS) {
            courseRepo.createEnrolment(student, PreloadedCourses.OFFICIAL_CATALOG.first()).getOrThrow()
        }
        val enr = withTimeout(FLOW_TIMEOUT_MS) {
            courseRepo.observeEnrolments(aliceUid, UserRole.STUDENT).first { list -> list.any { it.id == enrId } }
        }.first { it.id == enrId }

        val opayRes = withTimeout(TIMEOUT_MS) {
            paymentRepo.submitOpayBankTransfer(enr, 25000L, "OPAY-TRF-88221", "")
        }
        assertTrue("OPay submission failed: ${opayRes.exceptionOrNull()}", opayRes.isSuccess)

        auth.signOut()
        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                paymentRepo.observePayments(aliceUid, UserRole.STUDENT).first()
            }
        } catch (e: Throwable) {
            caught = e
        }
        val fsEx = caught?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }

    @Test
    fun qrAttendance_parsesPayloads_andEnforcesStaffOnlySessionCreation() = runBlocking {
        val parsed = AttendanceQrParser.parse(
            AttendanceQrParser.buildPayload("sess_crs_basic_20261114", "crs_basic", "2026-11-14")
        )
        assertEquals("sess_crs_basic_20261114", parsed?.sessionId)
        assertEquals("crs_basic", parsed?.courseId)
        assertEquals("2026-11-14", parsed?.dateIso)
        assertEquals(null, AttendanceQrParser.parse("https://unrelated-qr.example"))

        val studentUid = signInTestUser("qr_student_${UUID.randomUUID()}@jytest.com")
        val academicRepo = AcademicRepository(firestore, localDb.jySmsDao())
        val course = PreloadedCourses.OFFICIAL_CATALOG.first()

        // Students cannot create QR attendance sessions
        val createSessionRes = withTimeout(TIMEOUT_MS) {
            academicRepo.createOrActivateQrSession(studentUid, "Student", course, "2026-11-14")
        }
        assertTrue("Students must not create QR attendance sessions", createSessionRes.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            createSessionRes.exceptionOrNull()?.unwrapFirestoreException()?.code
        )

        // Invalid or non-existent QR session fails gracefully
        val badQrRes = academicRepo.markAttendanceViaQrPayload(studentUid, emptyList(), "INVALID_QR")
        assertTrue(badQrRes.isFailure)

        val missingSessionRes = withTimeout(TIMEOUT_MS) {
            academicRepo.markAttendanceViaQrPayload(
                studentUid,
                emptyList(),
                AttendanceQrParser.buildPayload("sess_missing_20261114", course.id, "2026-11-14")
            )
        }
        assertTrue("Missing QR session must be rejected", missingSessionRes.isFailure)
    }

    private companion object {
        const val TIMEOUT_MS = 6000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}
