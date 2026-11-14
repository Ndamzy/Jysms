package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.AcademicResult
import com.example.domain.model.Announcement
import com.example.domain.model.AttendanceRecord
import com.example.domain.model.Course
import com.example.domain.model.Enrolment

@Entity(tableName = "cached_courses")
data class CachedCourseEntity(
    @PrimaryKey val id: String,
    val code: String,
    val title: String,
    val description: String,
    val durationLabel: String,
    val durationMonths: Long,
    val feeNgn: Long,
    val schedule: String,
    val instructorId: String,
    val instructorName: String,
    val isAvailable: Boolean,
    val isArchived: Boolean
) {
    fun toDomain() = Course(
        id = id,
        code = code,
        title = title,
        description = description,
        durationLabel = durationLabel,
        durationMonths = durationMonths,
        feeNgn = feeNgn,
        schedule = schedule,
        instructorId = instructorId,
        instructorName = instructorName,
        isAvailable = isAvailable,
        isArchived = isArchived
    )

    companion object {
        fun fromDomain(course: Course) = CachedCourseEntity(
            id = course.id,
            code = course.code,
            title = course.title,
            description = course.description,
            durationLabel = course.durationLabel,
            durationMonths = course.durationMonths,
            feeNgn = course.feeNgn,
            schedule = course.schedule,
            instructorId = course.instructorId,
            instructorName = course.instructorName,
            isAvailable = course.isAvailable,
            isArchived = course.isArchived
        )
    }
}

@Entity(tableName = "cached_enrolments")
data class CachedEnrolmentEntity(
    @PrimaryKey val id: String,
    val reference: String,
    val userId: String,
    val studentId: String,
    val studentName: String,
    val courseId: String,
    val courseTitle: String,
    val courseFeeNgn: Long,
    val amountPaidNgn: Long,
    val instructorName: String,
    val registrationDate: String,
    val startDate: String,
    val expectedCompletionDate: String,
    val paymentStatus: String,
    val enrolmentStatus: String,
    val completionStatus: String,
    val instalmentPlan: String
) {
    fun toDomain() = Enrolment(
        id = id,
        reference = reference,
        userId = userId,
        studentId = studentId,
        studentName = studentName,
        courseId = courseId,
        courseTitle = courseTitle,
        courseFeeNgn = courseFeeNgn,
        amountPaidNgn = amountPaidNgn,
        instructorName = instructorName,
        registrationDate = registrationDate,
        startDate = startDate,
        expectedCompletionDate = expectedCompletionDate,
        paymentStatus = paymentStatus,
        enrolmentStatus = enrolmentStatus,
        completionStatus = completionStatus,
        instalmentPlan = instalmentPlan
    )

    companion object {
        fun fromDomain(e: Enrolment) = CachedEnrolmentEntity(
            id = e.id,
            reference = e.reference,
            userId = e.userId,
            studentId = e.studentId,
            studentName = e.studentName,
            courseId = e.courseId,
            courseTitle = e.courseTitle,
            courseFeeNgn = e.courseFeeNgn,
            amountPaidNgn = e.amountPaidNgn,
            instructorName = e.instructorName,
            registrationDate = e.registrationDate,
            startDate = e.startDate,
            expectedCompletionDate = e.expectedCompletionDate,
            paymentStatus = e.paymentStatus,
            enrolmentStatus = e.enrolmentStatus,
            completionStatus = e.completionStatus,
            instalmentPlan = e.instalmentPlan
        )
    }
}

@Entity(tableName = "cached_attendance")
data class CachedAttendanceEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val studentId: String,
    val studentName: String,
    val courseId: String,
    val courseTitle: String,
    val dateIso: String,
    val status: String,
    val notes: String
) {
    fun toDomain() = AttendanceRecord(
        id = id,
        userId = userId,
        studentId = studentId,
        studentName = studentName,
        courseId = courseId,
        courseTitle = courseTitle,
        dateIso = dateIso,
        status = status,
        notes = notes
    )

    companion object {
        fun fromDomain(a: AttendanceRecord) = CachedAttendanceEntity(
            id = a.id,
            userId = a.userId,
            studentId = a.studentId,
            studentName = a.studentName,
            courseId = a.courseId,
            courseTitle = a.courseTitle,
            dateIso = a.dateIso,
            status = a.status,
            notes = a.notes
        )
    }
}

@Entity(tableName = "cached_results")
data class CachedResultEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val studentId: String,
    val studentName: String,
    val courseId: String,
    val courseTitle: String,
    val caScore: Long,
    val examScore: Long,
    val totalScore: Long,
    val grade: String,
    val remark: String,
    val instructorComment: String
) {
    fun toDomain() = AcademicResult(
        id = id,
        userId = userId,
        studentId = studentId,
        studentName = studentName,
        courseId = courseId,
        courseTitle = courseTitle,
        caScore = caScore,
        examScore = examScore,
        totalScore = totalScore,
        grade = grade,
        remark = remark,
        instructorComment = instructorComment
    )

    companion object {
        fun fromDomain(r: AcademicResult) = CachedResultEntity(
            id = r.id,
            userId = r.userId,
            studentId = r.studentId,
            studentName = r.studentName,
            courseId = r.courseId,
            courseTitle = r.courseTitle,
            caScore = r.caScore,
            examScore = r.examScore,
            totalScore = r.totalScore,
            grade = r.grade,
            remark = r.remark,
            instructorComment = r.instructorComment
        )
    }
}

@Entity(tableName = "cached_announcements")
data class CachedAnnouncementEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    val targetAudience: String,
    val courseId: String,
    val authorName: String,
    val isPublished: Boolean
) {
    fun toDomain() = Announcement(
        id = id,
        title = title,
        body = body,
        targetAudience = targetAudience,
        courseId = courseId,
        authorName = authorName,
        isPublished = isPublished
    )

    companion object {
        fun fromDomain(a: Announcement) = CachedAnnouncementEntity(
            id = a.id,
            title = a.title,
            body = a.body,
            targetAudience = a.targetAudience,
            courseId = a.courseId,
            authorName = a.authorName,
            isPublished = a.isPublished
        )
    }
}
