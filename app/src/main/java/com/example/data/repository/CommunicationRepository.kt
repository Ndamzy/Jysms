package com.example.data.repository

import com.example.core.InstitutionConfig
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.local.CachedAnnouncementEntity
import com.example.data.local.JySmsDao
import com.example.domain.model.ActivityLog
import com.example.domain.model.Announcement
import com.example.domain.model.AppNotification
import com.example.domain.model.BlockedUser
import com.example.domain.model.ChatMessage
import com.example.domain.model.ChatReport
import com.example.domain.model.Conversation
import com.example.domain.model.DeliveryStatus
import com.example.domain.model.DirectoryMember
import com.example.domain.model.GroupChat
import com.example.domain.model.GroupMember
import com.example.domain.model.GroupType
import com.example.domain.model.MessageType
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

class CommunicationRepository(
    private val db: FirebaseFirestore,
    private val dao: JySmsDao
) {

    fun observeDirectory(isAdmin: Boolean): Flow<List<DirectoryMember>> {
        val query = if (isAdmin) {
            db.collection("directory")
        } else {
            db.collection("directory").whereEqualTo("isActive", true)
        }
        return query.snapshots()
            .map { snap ->
                snap.toObjects(DirectoryMember::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.displayName.lowercase() }
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "directory")
                throw error
            }
    }

    fun observeConversations(currentUid: String): Flow<List<Conversation>> {
        val query = db.collection("conversations").whereArrayContains("participantUids", currentUid)
        return query.snapshots()
            .map { snap ->
                snap.toObjects(Conversation::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.updatedAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "conversations")
                throw error
            }
    }

    suspend fun getOrCreateConversation(
        currentUid: String,
        currentName: String,
        other: DirectoryMember
    ): Result<String> = runCatching {
        val pair = listOf(currentUid, other.uid).sorted()
        val convId = "${pair[0]}_${pair[1]}"
        val docRef = db.collection("conversations").document(convId)
        val snap = docRef.get().await()
        if (!snap.exists()) {
            docRef.set(
                mapOf(
                    "participantUids" to pair,
                    "participantNames" to mapOf(
                        currentUid to currentName,
                        other.uid to other.displayName
                    ),
                    "lastMessageText" to "Conversation started",
                    "lastSenderUid" to currentUid,
                    "typingUid" to "",
                    "unreadCounts" to mapOf(currentUid to 0L, other.uid to 0L),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        convId
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "conversations")
    }

    fun observeDirectMessages(conversationId: String): Flow<List<ChatMessage>> {
        val col = db.collection("conversations").document(conversationId).collection("messages")
        return col.snapshots()
            .map { snap ->
                snap.toObjects(ChatMessage::class.java, ServerTimestampBehavior.ESTIMATE)
                    .filter { !it.isRemoved }
                    .sortedBy { it.createdAt?.seconds ?: Long.MAX_VALUE }
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, col.path)
                throw error
            }
    }

    suspend fun sendDirectMessage(
        conversation: Conversation,
        senderUid: String,
        senderName: String,
        text: String,
        messageType: MessageType = MessageType.TEXT,
        attachmentName: String = "",
        attachmentUrl: String = ""
    ): Result<Unit> = runCatching {
        val convRef = db.collection("conversations").document(conversation.id)
        val msgRef = convRef.collection("messages").document()
        val otherUid = conversation.participantUids.firstOrNull { it != senderUid } ?: senderUid

        val msgPayload = mutableMapOf<String, Any>(
            "senderUid" to senderUid,
            "senderName" to senderName,
            "memberUids" to conversation.participantUids,
            "text" to text.trim().take(2000),
            "messageType" to messageType.name,
            "deliveryStatus" to DeliveryStatus.SENT.name,
            "isRemoved" to false,
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (attachmentName.isNotBlank()) msgPayload["attachmentName"] = attachmentName.take(200)
        if (attachmentUrl.isNotBlank()) msgPayload["attachmentUrl"] = attachmentUrl.take(600)

        val currentUnread = conversation.unreadCounts[otherUid] ?: 0L
        val updatedUnread = mapOf(senderUid to 0L, otherUid to (currentUnread + 1L))

        val batch = db.batch()
        batch.set(msgRef, msgPayload)
        batch.update(
            convRef,
            mapOf(
                "lastMessageText" to text.trim().take(1000),
                "lastSenderUid" to senderUid,
                "typingUid" to "",
                "unreadCounts" to updatedUnread,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.commit().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "conversations/${conversation.id}/messages")
    }

    suspend fun markConversationRead(conversation: Conversation, currentUid: String) {
        val myUnread = conversation.unreadCounts[currentUid] ?: 0L
        if (myUnread == 0L) return
        val updated = conversation.unreadCounts.toMutableMap().apply { put(currentUid, 0L) }
        runCatching {
            db.collection("conversations").document(conversation.id).update(
                mapOf(
                    "unreadCounts" to updated,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    suspend fun updateTypingStatus(conversationId: String, typingUid: String) {
        runCatching {
            db.collection("conversations").document(conversationId).update(
                mapOf(
                    "typingUid" to typingUid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    fun observeGroupChats(isAdmin: Boolean): Flow<List<GroupChat>> {
        val query = if (isAdmin) {
            db.collection("group_chats")
        } else {
            db.collection("group_chats").whereEqualTo("isActive", true)
        }
        return query.snapshots()
            .map { snap ->
                val remote = snap.toObjects(GroupChat::class.java, ServerTimestampBehavior.ESTIMATE)
                if (remote.isEmpty()) {
                    listOf(
                        GroupChat(
                            id = InstitutionConfig.GENERAL_COMMUNITY_GROUP_ID,
                            title = "JY-COMPUTERS General Community",
                            groupType = GroupType.GENERAL_COMMUNITY.name,
                            lastMessageText = "Open student discussion & tech support",
                            isActive = true
                        )
                    )
                } else {
                    remote.sortedBy { if (it.id == InstitutionConfig.GENERAL_COMMUNITY_GROUP_ID) "" else it.title }
                }
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "group_chats")
                throw error
            }
    }

    fun observeGroupMemberships(currentUid: String, role: UserRole): Flow<List<GroupMember>> {
        val query = if (role == UserRole.ADMINISTRATOR || role == UserRole.INSTRUCTOR) {
            db.collection("group_members")
        } else {
            db.collection("group_members").whereEqualTo("userId", currentUid)
        }
        return query.snapshots()
            .map { snap -> snap.toObjects(GroupMember::class.java, ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "group_members")
                throw error
            }
    }

    fun observeGroupMessages(groupId: String): Flow<List<ChatMessage>> {
        val query = db.collection("group_chats").document(groupId)
            .collection("messages")
            .whereEqualTo("groupId", groupId)
        return query.snapshots()
            .map { snap ->
                snap.toObjects(ChatMessage::class.java, ServerTimestampBehavior.ESTIMATE)
                    .filter { !it.isRemoved }
                    .sortedBy { it.createdAt?.seconds ?: Long.MAX_VALUE }
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "group_chats/$groupId/messages")
                throw error
            }
    }

    suspend fun sendGroupMessage(
        groupId: String,
        senderUid: String,
        senderName: String,
        text: String,
        messageType: MessageType = MessageType.TEXT,
        attachmentName: String = "",
        attachmentUrl: String = ""
    ): Result<Unit> = runCatching {
        val groupRef = db.collection("group_chats").document(groupId)
        val msgRef = groupRef.collection("messages").document()
        val payload = mutableMapOf<String, Any>(
            "senderUid" to senderUid,
            "senderName" to senderName,
            "groupId" to groupId,
            "text" to text.trim().take(2000),
            "messageType" to messageType.name,
            "deliveryStatus" to DeliveryStatus.SENT.name,
            "isRemoved" to false,
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (attachmentName.isNotBlank()) payload["attachmentName"] = attachmentName.take(200)
        if (attachmentUrl.isNotBlank()) payload["attachmentUrl"] = attachmentUrl.take(600)

        msgRef.set(payload).await()
        runCatching {
            groupRef.update(
                mapOf(
                    "lastMessageText" to "$senderName: ${text.trim().take(200)}",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "group_chats/$groupId/messages")
    }

    suspend fun removeGroupMessage(groupId: String, messageId: String): Result<Unit> = runCatching {
        db.collection("group_chats").document(groupId)
            .collection("messages").document(messageId)
            .delete()
            .await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.DELETE, "group_chats/$groupId/messages/$messageId")
    }

    suspend fun blockUser(blockerUid: String, blockedUid: String): Result<Unit> = runCatching {
        val id = "${blockerUid}_${blockedUid}"
        db.collection("blocked_users").document(id).set(
            mapOf(
                "blockerUid" to blockerUid,
                "blockedUid" to blockedUid,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "blocked_users")
    }

    fun observeBlockedUsers(currentUid: String): Flow<List<BlockedUser>> {
        return db.collection("blocked_users")
            .whereEqualTo("blockerUid", currentUid)
            .snapshots()
            .map { it.toObjects(BlockedUser::class.java, ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "blocked_users")
                throw error
            }
    }

    suspend fun reportUserOrMessage(
        reporterUid: String,
        reportedUid: String,
        targetChatId: String,
        messageId: String,
        reason: String
    ): Result<Unit> = runCatching {
        val payload = mutableMapOf<String, Any>(
            "reporterUid" to reporterUid,
            "reportedUid" to reportedUid,
            "reason" to reason.trim().take(500),
            "status" to "OPEN",
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (targetChatId.isNotBlank()) payload["conversationOrGroupId"] = targetChatId
        if (messageId.isNotBlank()) payload["messageId"] = messageId
        db.collection("chat_reports").document().set(payload).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "chat_reports")
    }

    fun observeChatReports(currentUid: String, isAdmin: Boolean): Flow<List<ChatReport>> {
        val query = if (isAdmin) {
            db.collection("chat_reports")
        } else {
            db.collection("chat_reports").whereEqualTo("reporterUid", currentUid)
        }
        return query.snapshots()
            .map { it.toObjects(ChatReport::class.java, ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "chat_reports")
                throw error
            }
    }

    suspend fun resolveChatReport(reportId: String): Result<Unit> = runCatching {
        db.collection("chat_reports").document(reportId).update("status", "RESOLVED").await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "chat_reports/$reportId")
    }

    fun observeAnnouncements(role: UserRole): Flow<List<Announcement>> {
        val query = if (role == UserRole.ADMINISTRATOR || role == UserRole.INSTRUCTOR) {
            db.collection("announcements")
        } else {
            db.collection("announcements").whereEqualTo("isPublished", true)
        }
        return query.snapshots()
            .map { snap ->
                snap.toObjects(Announcement::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .onEach { list ->
                runCatching { dao.upsertAnnouncements(list.map { CachedAnnouncementEntity.fromDomain(it) }) }
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "announcements")
                throw error
            }
    }

    suspend fun publishAnnouncement(
        authorUid: String,
        authorName: String,
        title: String,
        body: String,
        targetAudience: String,
        courseId: String = ""
    ): Result<Unit> = runCatching {
        val payload = mutableMapOf<String, Any>(
            "title" to title.trim().take(160),
            "body" to body.trim().take(3000),
            "targetAudience" to targetAudience,
            "authorUid" to authorUid,
            "authorName" to authorName.take(120),
            "isPublished" to true,
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (courseId.isNotBlank()) payload["courseId"] = courseId
        db.collection("announcements").document().set(payload).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "announcements")
    }

    fun observeNotifications(currentUid: String): Flow<List<AppNotification>> {
        return db.collection("notifications")
            .whereEqualTo("userId", currentUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(AppNotification::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "notifications")
                throw error
            }
    }

    suspend fun markNotificationRead(notificationId: String) {
        runCatching {
            db.collection("notifications").document(notificationId).update("isRead", true).await()
        }
    }

    fun observeActivityLogs(): Flow<List<ActivityLog>> {
        return db.collection("activity_logs")
            .snapshots()
            .map { snap ->
                snap.toObjects(ActivityLog::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is CancellationException && error.cause == null) throw error
                handleFirestoreError(error, OperationType.LIST, "activity_logs")
                throw error
            }
    }

    suspend fun logActivity(
        actorUid: String,
        actorName: String,
        action: String,
        targetType: String,
        targetId: String = "",
        details: String = ""
    ) {
        runCatching {
            val payload = mutableMapOf<String, Any>(
                "actorUid" to actorUid,
                "actorName" to actorName.take(120),
                "action" to action.take(120),
                "targetType" to targetType.take(60),
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (targetId.isNotBlank()) payload["targetId"] = targetId.take(128)
            if (details.isNotBlank()) payload["details"] = details.take(500)
            db.collection("activity_logs").document().set(payload).await()
        }
    }
}
