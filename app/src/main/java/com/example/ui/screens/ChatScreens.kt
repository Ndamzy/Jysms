package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.Formatters
import com.example.domain.model.BlockedUser
import com.example.domain.model.ChatMessage
import com.example.domain.model.ChatReport
import com.example.domain.model.Conversation
import com.example.domain.model.DirectoryMember
import com.example.domain.model.GroupChat
import com.example.domain.model.MessageType
import com.example.domain.model.UserAccount
import com.example.domain.model.UserRole
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.JyEmerald
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import kotlinx.coroutines.flow.Flow

private sealed interface ActiveChatTarget {
    data class Direct(val conversation: Conversation) : ActiveChatTarget
    data class Group(val group: GroupChat) : ActiveChatTarget
}

@Composable
fun ChatHubScreen(
    currentUid: String,
    userAccount: UserAccount?,
    directory: List<DirectoryMember>,
    conversations: List<Conversation>,
    groupChats: List<GroupChat>,
    blockedUsers: List<BlockedUser>,
    chatReports: List<ChatReport>,
    observeDirectMessages: (String) -> Flow<List<ChatMessage>>,
    observeGroupMessages: (String) -> Flow<List<ChatMessage>>,
    onStartConversation: (DirectoryMember, (String) -> Unit) -> Unit,
    onSendDirectMessage: (Conversation, String, MessageType, String, String) -> Unit,
    onSendGroupMessage: (String, String, MessageType, String, String) -> Unit,
    onMarkRead: (Conversation) -> Unit,
    onUpdateTyping: (String, Boolean) -> Unit,
    onRemoveGroupMessage: (String, String) -> Unit,
    onBlockUser: (String) -> Unit,
    onReportMessage: (String, String, String, String) -> Unit,
    onResolveReport: (String) -> Unit,
    onToggleSuspendUser: (String, Boolean) -> Unit
) {
    val isAdmin = userAccount?.userRole == UserRole.ADMINISTRATOR
    var activeTarget by remember { mutableStateOf<ActiveChatTarget?>(null) }
    var pendingConvId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pendingConvId, conversations) {
        val targetId = pendingConvId ?: return@LaunchedEffect
        conversations.firstOrNull { it.id == targetId }?.let { found ->
            activeTarget = ActiveChatTarget.Direct(found)
            pendingConvId = null
        }
    }

    if (activeTarget != null) {
        BackHandler {
            activeTarget = null
        }
        ChatThreadScreen(
            currentUid = currentUid,
            isAdmin = isAdmin,
            target = activeTarget!!,
            blockedUids = blockedUsers.map { it.blockedUid }.toSet(),
            observeDirectMessages = observeDirectMessages,
            observeGroupMessages = observeGroupMessages,
            onBack = { activeTarget = null },
            onSendDirectMessage = onSendDirectMessage,
            onSendGroupMessage = onSendGroupMessage,
            onMarkRead = onMarkRead,
            onUpdateTyping = onUpdateTyping,
            onRemoveGroupMessage = onRemoveGroupMessage,
            onBlockUser = onBlockUser,
            onReportMessage = onReportMessage
        )
        return
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var searchMember by remember { mutableStateOf("") }
    val blockedSet = remember(blockedUsers) { blockedUsers.map { it.blockedUid }.toSet() }

    val tabs = if (isAdmin) listOf("Direct Chat", "Course Groups", "Moderation") else listOf("Direct Chat", "Course Groups")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab.coerceIn(0, tabs.lastIndex)) {
            tabs.forEachIndexed { idx, title ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = { Text(title) }
                )
            }
        }

        when (selectedTab) {
            0 -> {
                val filteredMembers = remember(directory, searchMember, currentUid, blockedSet) {
                    val q = searchMember.trim().lowercase()
                    directory.filter {
                        it.uid != currentUid &&
                            it.uid !in blockedSet &&
                            (q.isEmpty() || it.displayName.lowercase().contains(q) || it.studentCode.lowercase().contains(q))
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = searchMember,
                            onValueChange = { searchMember = it },
                            label = { Text("Search students, instructors, or admins") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (conversations.isNotEmpty()) {
                        item {
                            Text("Active Conversations", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        items(conversations, key = { it.id }) { conv ->
                            val otherUid = conv.participantUids.firstOrNull { it != currentUid } ?: currentUid
                            val otherName = conv.participantNames[otherUid] ?: "JY Member"
                            val unread = conv.unreadCounts[currentUid] ?: 0L
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { activeTarget = ActiveChatTarget.Direct(conv) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(otherName.take(1).uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(otherName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(
                                            if (conv.typingUid == otherUid) "Typing..." else conv.lastMessageText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (conv.typingUid == otherUid) JyEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                    if (unread > 0L) {
                                        StatusPill(
                                            text = unread.toString(),
                                            containerColor = JyEmerald,
                                            contentColor = MaterialTheme.colorScheme.onSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text("Student & Faculty Directory", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    if (filteredMembers.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Default.Person,
                                title = "No Directory Members Found",
                                subtitle = "Registered students and faculty members appear here for private chat."
                            )
                        }
                    } else {
                        items(filteredMembers, key = { it.uid }) { member ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onStartConversation(member) { convId ->
                                            val existing = conversations.firstOrNull { it.id == convId }
                                            if (existing != null) {
                                                activeTarget = ActiveChatTarget.Direct(existing)
                                            } else {
                                                pendingConvId = convId
                                                activeTarget = ActiveChatTarget.Direct(
                                                    Conversation(
                                                        id = convId,
                                                        participantUids = listOf(currentUid, member.uid).sorted(),
                                                        participantNames = mapOf(
                                                            currentUid to (userAccount?.displayName ?: "Me"),
                                                            member.uid to member.displayName
                                                        )
                                                    )
                                                )
                                            }
                                        }
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(if (member.isOnline) StatusSuccess else MaterialTheme.colorScheme.outline)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(member.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${member.role}${if (member.studentCode.isNotBlank()) " • ${member.studentCode}" else ""}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Message ${member.displayName}", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (groupChats.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Default.Groups,
                                title = "No Course Groups Yet",
                                subtitle = "Confirmed course enrolments automatically unlock your course group chat."
                            )
                        }
                    } else {
                        items(groupChats, key = { it.id }) { grp ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { activeTarget = ActiveChatTarget.Group(grp) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Forum, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(grp.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(
                                            grp.lastMessageText.ifBlank { "Tap to join course discussion" },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                    StatusPill(
                                        text = if (grp.id == "general_community") "COMMUNITY" else "COURSE",
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text("Reported Messages & Users (${chatReports.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    if (chatReports.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Default.Flag,
                                title = "No Reports Submitted",
                                subtitle = "Student safety reports will appear here for administrator review."
                            )
                        }
                    } else {
                        items(chatReports, key = { it.id }) { rep ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Reported UID: ${rep.reportedUid.take(10)}...", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        StatusPill(
                                            text = rep.status,
                                            containerColor = if (rep.status == "OPEN") ErrorRed.copy(alpha = 0.15f) else StatusSuccess.copy(alpha = 0.15f),
                                            contentColor = if (rep.status == "OPEN") ErrorRed else StatusSuccess
                                        )
                                    }
                                    Text("Reason: ${rep.reason}", style = MaterialTheme.typography.bodySmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (rep.status == "OPEN") {
                                            Button(onClick = { onResolveReport(rep.id) }) { Text("Resolve") }
                                        }
                                        OutlinedButton(onClick = { onToggleSuspendUser(rep.reportedUid, true) }) {
                                            Text("Suspend User")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatThreadScreen(
    currentUid: String,
    isAdmin: Boolean,
    target: ActiveChatTarget,
    blockedUids: Set<String>,
    observeDirectMessages: (String) -> Flow<List<ChatMessage>>,
    observeGroupMessages: (String) -> Flow<List<ChatMessage>>,
    onBack: () -> Unit,
    onSendDirectMessage: (Conversation, String, MessageType, String, String) -> Unit,
    onSendGroupMessage: (String, String, MessageType, String, String) -> Unit,
    onMarkRead: (Conversation) -> Unit,
    onUpdateTyping: (String, Boolean) -> Unit,
    onRemoveGroupMessage: (String, String) -> Unit,
    onBlockUser: (String) -> Unit,
    onReportMessage: (String, String, String, String) -> Unit
) {
    val messagesFlow = remember(target) {
        when (target) {
            is ActiveChatTarget.Direct -> observeDirectMessages(target.conversation.id)
            is ActiveChatTarget.Group -> observeGroupMessages(target.group.id)
        }
    }
    val messages by messagesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val visibleMessages = remember(messages, blockedUids) {
        messages.filter { it.senderUid !in blockedUids }
    }

    var draftText by remember { mutableStateOf("") }
    var reportTarget by remember { mutableStateOf<ChatMessage?>(null) }

    val targetId = when (target) {
        is ActiveChatTarget.Direct -> target.conversation.id
        is ActiveChatTarget.Group -> target.group.id
    }
    val headerTitle = when (target) {
        is ActiveChatTarget.Direct -> {
            val otherUid = target.conversation.participantUids.firstOrNull { it != currentUid } ?: currentUid
            target.conversation.participantNames[otherUid] ?: "Direct Chat"
        }
        is ActiveChatTarget.Group -> target.group.title
    }
    val otherDirectUid = (target as? ActiveChatTarget.Direct)?.conversation?.participantUids?.firstOrNull { it != currentUid }

    LaunchedEffect(target) {
        if (target is ActiveChatTarget.Direct) {
            onMarkRead(target.conversation)
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            val name = uri.lastPathSegment ?: "Shared Image"
            when (target) {
                is ActiveChatTarget.Direct -> onSendDirectMessage(target.conversation, "Shared an image: $name", MessageType.IMAGE, name, uri.toString())
                is ActiveChatTarget.Group -> onSendGroupMessage(target.group.id, "Shared an image: $name", MessageType.IMAGE, name, uri.toString())
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val name = uri.lastPathSegment ?: "Document.pdf"
            val type = if (name.endsWith(".pdf", ignoreCase = true)) MessageType.PDF else MessageType.FILE
            when (target) {
                is ActiveChatTarget.Direct -> onSendDirectMessage(target.conversation, "Shared file: $name", type, name, uri.toString())
                is ActiveChatTarget.Group -> onSendGroupMessage(target.group.id, "Shared file: $name", type, name, uri.toString())
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("chat_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to chat list")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(headerTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (target is ActiveChatTarget.Direct && target.conversation.typingUid == otherDirectUid) {
                        Text("Typing...", style = MaterialTheme.typography.labelSmall, color = JyEmerald)
                    } else if (target is ActiveChatTarget.Group) {
                        Text("Group Discussion", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (otherDirectUid != null) {
                    IconButton(onClick = { onBlockUser(otherDirectUid) }) {
                        Icon(Icons.Default.Block, contentDescription = "Block user", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (visibleMessages.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.AutoMirrored.Filled.Chat,
                        title = "Start the Conversation",
                        subtitle = "Send a message, study material, or emoji to connect."
                    )
                }
            } else {
                items(visibleMessages, key = { it.id }) { msg ->
                    val isMine = msg.senderUid == currentUid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.widthIn(max = 310.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                if (!isMine) {
                                    Text(
                                        msg.senderName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Spacer(Modifier.height(2.dp))
                                }
                                if (msg.attachmentName.isNotBlank()) {
                                    Text(
                                        "[${msg.messageType}] ${msg.attachmentName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(4.dp))
                                }
                                Text(
                                    msg.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        Formatters.formatShortTime(msg.createdAt?.toDate()),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isMine) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (isMine) {
                                        Icon(
                                            Icons.Default.DoneAll,
                                            contentDescription = msg.deliveryStatus,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                        )
                                    } else {
                                        IconButton(
                                            onClick = { reportTarget = msg },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Flag,
                                                contentDescription = "Report message",
                                                modifier = Modifier.size(14.dp),
                                                tint = StatusWarning
                                            )
                                        }
                                    }
                                    if (isAdmin && target is ActiveChatTarget.Group) {
                                        IconButton(
                                            onClick = { onRemoveGroupMessage(target.group.id, msg.id) },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Remove message",
                                                modifier = Modifier.size(14.dp),
                                                tint = ErrorRed
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val emojis = listOf("👍", "👏", "🎉", "💻", "📚", "✅", "🙏")
            items(emojis) { emoji ->
                FilterChip(
                    selected = false,
                    onClick = { draftText += emoji },
                    label = { Text(emoji) }
                )
            }
        }

        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Icon(Icons.Default.Image, contentDescription = "Attach image")
                }
                IconButton(onClick = {
                    filePicker.launch(arrayOf("application/pdf", "*/*"))
                }) {
                    Icon(Icons.Default.AttachFile, contentDescription = "Attach document")
                }
                OutlinedTextField(
                    value = draftText,
                    onValueChange = {
                        draftText = it
                        if (target is ActiveChatTarget.Direct) {
                            onUpdateTyping(targetId, it.isNotBlank())
                        }
                    },
                    placeholder = { Text("Type message...") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                IconButton(
                    onClick = {
                        val trimmed = draftText.trim()
                        if (trimmed.isNotEmpty()) {
                            when (target) {
                                is ActiveChatTarget.Direct -> onSendDirectMessage(target.conversation, trimmed, MessageType.TEXT, "", "")
                                is ActiveChatTarget.Group -> onSendGroupMessage(target.group.id, trimmed, MessageType.TEXT, "", "")
                            }
                            draftText = ""
                            if (target is ActiveChatTarget.Direct) onUpdateTyping(targetId, false)
                        }
                    },
                    modifier = Modifier.testTag("send_message_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    reportTarget?.let { msg ->
        var reason by remember { mutableStateOf("Inappropriate content / spam") }
        AlertDialog(
            onDismissRequest = { reportTarget = null },
            title = { Text("Report Message from ${msg.senderName}") },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason for report") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    onReportMessage(msg.senderUid, targetId, msg.id, reason)
                    reportTarget = null
                }) { Text("Submit Report") }
            },
            dismissButton = {
                TextButton(onClick = { reportTarget = null }) { Text("Cancel") }
            }
        )
    }
}
