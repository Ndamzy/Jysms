package com.example.domain.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class UserRole {
    ADMINISTRATOR,
    INSTRUCTOR,
    STUDENT;

    companion object {
        fun fromString(value: String?): UserRole {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: STUDENT
        }
    }
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@IgnoreExtraProperties
data class UserAccount(
    @DocumentId val id: String = "",
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val phone: String = "",
    val photoUrl: String = "",
    val role: String = UserRole.STUDENT.name,
    val isSuspended: Boolean = false,
    val notificationsEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val userRole: UserRole get() = UserRole.fromString(role)
}

@IgnoreExtraProperties
data class DirectoryMember(
    @DocumentId val id: String = "",
    val uid: String = "",
    val displayName: String = "",
    val studentCode: String = "",
    val role: String = UserRole.STUDENT.name,
    val photoUrl: String = "",
    val isOnline: Boolean = false,
    val isActive: Boolean = true,
    val isSuspended: Boolean = false,
    @ServerTimestamp val lastSeenAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Instructor(
    @DocumentId val id: String = "",
    val uid: String = "",
    val fullName: String = "",
    val specialization: String = "",
    val phoneNumber: String = "",
    val email: String = "",
    val isActive: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
