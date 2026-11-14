package com.example.data.repository

import com.example.core.InstitutionConfig
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.domain.model.DirectoryMember
import com.example.domain.model.Instructor
import com.example.domain.model.UserAccount
import com.example.domain.model.UserRole
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class AuthRepository(private val db: FirebaseFirestore) {

    suspend fun syncSignedInUser(user: FirebaseUser): Result<UserAccount> = runCatching {
        val uid = user.uid
        val userRef = db.collection("users").document(uid)
        val isBootstrapAdmin = user.isEmailVerified &&
            user.email.equals(InstitutionConfig.BOOTSTRAP_ADMIN_EMAIL, ignoreCase = true)

        val existingSnap = try {
            userRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, userRef.path)
            throw e
        }

        val displayName = user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
            ?: "JY Member"
        val email = user.email?.takeIf { it.isNotBlank() } ?: "$uid@jy-computers.com"
        val photoUrl = user.photoUrl?.toString().orEmpty()

        if (!existingSnap.exists()) {
            val initialRole = if (isBootstrapAdmin) UserRole.ADMINISTRATOR.name else UserRole.STUDENT.name
            val payload = mapOf(
                "uid" to uid,
                "displayName" to displayName.take(120),
                "email" to email.take(160),
                "photoUrl" to photoUrl.take(600),
                "role" to initialRole,
                "isSuspended" to false,
                "notificationsEnabled" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                userRef.set(payload).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, userRef.path)
                throw e
            }

            if (isBootstrapAdmin) {
                val adminRef = db.collection("admins").document(uid)
                runCatching {
                    adminRef.set(
                        mapOf(
                            "uid" to uid,
                            "email" to email.take(160),
                            "grantedBy" to uid,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }

            syncDirectoryProfile(uid, displayName, initialRole, photoUrl, isOnline = true)
            UserAccount(
                id = uid,
                uid = uid,
                displayName = displayName,
                email = email,
                photoUrl = photoUrl,
                role = initialRole,
                isSuspended = false,
                notificationsEnabled = true
            )
        } else {
            val account = existingSnap.toObject(UserAccount::class.java, ServerTimestampBehavior.ESTIMATE)
                ?: UserAccount(id = uid, uid = uid, displayName = displayName, email = email)
            syncDirectoryProfile(uid, account.displayName.ifBlank { displayName }, account.role, account.photoUrl.ifBlank { photoUrl }, isOnline = true)
            account
        }
    }

    private suspend fun syncDirectoryProfile(
        uid: String,
        displayName: String,
        role: String,
        photoUrl: String,
        isOnline: Boolean
    ) {
        val dirRef = db.collection("directory").document(uid)
        runCatching {
            val existing = dirRef.get().await()
            if (!existing.exists()) {
                dirRef.set(
                    mapOf(
                        "uid" to uid,
                        "displayName" to displayName.take(120),
                        "role" to role,
                        "photoUrl" to photoUrl.take(600),
                        "isOnline" to isOnline,
                        "isActive" to true,
                        "isSuspended" to false,
                        "lastSeenAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                dirRef.update(
                    mapOf(
                        "isOnline" to isOnline,
                        "lastSeenAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }
    }

    suspend fun setOnlineStatus(uid: String, isOnline: Boolean) {
        val dirRef = db.collection("directory").document(uid)
        runCatching {
            dirRef.update(
                mapOf(
                    "isOnline" to isOnline,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    fun observeUserAccount(uid: String): Flow<UserAccount?> {
        val docRef = db.collection("users").document(uid)
        return docRef.snapshots()
            .map { snap -> snap.toObject(UserAccount::class.java, ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.GET, docRef.path)
                throw error
            }
    }

    fun observeAllUsers(): Flow<List<UserAccount>> {
        val col = db.collection("users")
        return col.snapshots()
            .map { snap -> snap.toObjects(UserAccount::class.java, ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, col.path)
                throw error
            }
    }

    fun observeInstructors(isAdmin: Boolean): Flow<List<Instructor>> {
        val query = if (isAdmin) {
            db.collection("instructors")
        } else {
            db.collection("instructors").whereEqualTo("isActive", true)
        }
        return query.snapshots()
            .map { snap -> snap.toObjects(Instructor::class.java, ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "instructors")
                throw error
            }
    }

    suspend fun assignRoleAndInstructor(
        adminUid: String,
        targetUser: UserAccount,
        newRole: UserRole,
        specialization: String = "Computer Training"
    ): Result<Unit> = runCatching {
        val uid = targetUser.uid
        val userRef = db.collection("users").document(uid)
        val dirRef = db.collection("directory").document(uid)
        val batch = db.batch()
        batch.update(userRef, mapOf("role" to newRole.name, "updatedAt" to FieldValue.serverTimestamp()))
        batch.update(dirRef, mapOf("role" to newRole.name, "updatedAt" to FieldValue.serverTimestamp()))

        if (newRole == UserRole.ADMINISTRATOR) {
            batch.set(
                db.collection("admins").document(uid),
                mapOf(
                    "uid" to uid,
                    "email" to targetUser.email,
                    "grantedBy" to adminUid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
        } else if (newRole == UserRole.INSTRUCTOR) {
            batch.set(
                db.collection("instructors").document(uid),
                mapOf(
                    "uid" to uid,
                    "fullName" to targetUser.displayName,
                    "specialization" to specialization.ifBlank { "Computer Training" },
                    "phoneNumber" to targetUser.phone,
                    "email" to targetUser.email,
                    "isActive" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        batch.commit().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "users/${targetUser.uid}")
    }

    suspend fun setUserSuspended(targetUid: String, suspended: Boolean): Result<Unit> = runCatching {
        val batch = db.batch()
        batch.update(
            db.collection("users").document(targetUid),
            mapOf("isSuspended" to suspended, "updatedAt" to FieldValue.serverTimestamp())
        )
        batch.update(
            db.collection("directory").document(targetUid),
            mapOf("isSuspended" to suspended, "updatedAt" to FieldValue.serverTimestamp())
        )
        batch.commit().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "users/$targetUid")
    }

    suspend fun addInstructorProfile(instructor: Instructor): Result<Unit> = runCatching {
        val id = instructor.uid.ifBlank { db.collection("instructors").document().id }
        val payload = mapOf(
            "uid" to id,
            "fullName" to instructor.fullName.trim(),
            "specialization" to instructor.specialization.trim(),
            "phoneNumber" to instructor.phoneNumber.trim(),
            "email" to instructor.email.trim(),
            "isActive" to true,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("instructors").document(id).set(payload).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "instructors")
    }
}
