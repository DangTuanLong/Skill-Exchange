package com.skillexchange.app.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.skillexchange.app.BuildConfig
import com.skillexchange.app.core.auth.IFirebaseAuthManager
import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.data.remote.chat.ChatAttachmentDataDto
import com.skillexchange.app.data.remote.chat.ChatRemoteDataSource
import com.skillexchange.app.domain.model.chat.ChatMessage
import com.skillexchange.app.domain.model.chat.ChatRoom
import com.skillexchange.app.domain.repository.IChatRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

class ChatRepositoryImpl(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val firebaseAuthManager: IFirebaseAuthManager,
    private val tokenManager: TokenManager,
    private val chatRemoteDataSource: ChatRemoteDataSource? = null
) : IChatRepository {

    init {
        if (BuildConfig.DEBUG) {
            FirebaseFirestore.setLoggingEnabled(true)
        }
    }

    override fun getChatRoomsFlow(): Flow<List<ChatRoom>> = callbackFlow {
        val currentUserId = tokenManager.getUserId()
        if (currentUserId.isNullOrBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val authResult = firebaseAuthManager.ensureSignedIn()
        if (authResult.isFailure) {
            close(authResult.exceptionOrNull() ?: Exception("Chưa đăng nhập Firebase"))
            return@callbackFlow
        }

        val registration = firestore.collection("chats")
            .whereArrayContains("participants", currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val rooms = snapshot.documents.mapNotNull { it.toChatRoom() }
                        .sortedByDescending { it.lastMessageAt ?: it.updatedAt ?: 0L }
                    trySend(rooms)
                }
            }

        awaitClose { registration.remove() }
    }

    override fun getChatRoom(chatId: String): Flow<ChatRoom?> = callbackFlow {
        val authResult = firebaseAuthManager.ensureSignedIn()
        if (authResult.isFailure) {
            close(authResult.exceptionOrNull() ?: Exception("Chưa đăng nhập Firebase"))
            return@callbackFlow
        }

        val registration = firestore.collection("chats").document(chatId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.takeIf { it.exists() }?.toChatRoom())
            }

        awaitClose { registration.remove() }
    }

    override fun generateNewMessageId(chatId: String): String =
        firestore.collection("chats").document(chatId).collection("messages").document().id

    override fun getMessagesFlow(chatId: String, limit: Long): Flow<List<ChatMessage>> = callbackFlow {
        val authResult = firebaseAuthManager.ensureSignedIn()
        if (authResult.isFailure) {
            close(authResult.exceptionOrNull() ?: Exception("Chưa đăng nhập Firebase"))
            return@callbackFlow
        }

        val registration = firestore.collection("chats").document(chatId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limitToLast(limit)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val messages = snapshot.documents.mapNotNull { it.toChatMessage(chatId) }
                    trySend(messages)
                }
            }

        awaitClose { registration.remove() }
    }

    override suspend fun sendMessage(
        chatId: String,
        content: String,
        participants: List<String>,
        messageId: String,
        type: String,
        fileUrl: String?,
        fileName: String?,
        fileSize: Long?
    ): Result<String> = runCatching {
        val trimmed = content.trim()
        if (type == "TEXT") {
            require(trimmed.isNotEmpty()) { "Nội dung tin nhắn không được để trống" }
            require(trimmed.length <= 1000) { "Tin nhắn không được vượt quá 1000 ký tự" }
        } else {
            require(!fileUrl.isNullOrBlank()) { "URL tệp đính kèm không được để trống" }
            require(!fileName.isNullOrBlank() && fileName.length <= 100) { "Tên tệp không hợp lệ" }
            require(fileSize != null && fileSize > 0) { "Kích thước tệp không hợp lệ" }
        }
        require(participants.isNotEmpty()) { "Danh sách thành viên không được rỗng" }

        val currentUserId = tokenManager.getUserId()
            ?: throw IllegalStateException("Không tìm thấy thông tin phiên đăng nhập người dùng")

        firebaseAuthManager.ensureSignedIn().getOrThrow()

        val finalMsgId = messageId.ifBlank {
            firestore.collection("chats").document(chatId).collection("messages").document().id
        }

        val roomRef = firestore.collection("chats").document(chatId)
        val newMsgRef = roomRef.collection("messages").document(finalMsgId)

        val lastMessagePreview = when (type) {
            "IMAGE" -> "[Hình ảnh]"
            "FILE" -> "[Tệp] $fileName"
            else -> trimmed
        }

        val messageData = hashMapOf<String, Any?>(
            "participants" to participants,
            "senderId" to currentUserId,
            "content" to (if (type == "TEXT") trimmed else lastMessagePreview),
            "type" to type,
            "createdAt" to FieldValue.serverTimestamp(),
            "readAt" to null
        )

        if (type != "TEXT") {
            messageData["fileUrl"] = fileUrl
            messageData["fileName"] = fileName
            messageData["fileSize"] = fileSize
        }

        val roomUpdates = hashMapOf<String, Any>(
            "lastMessage" to lastMessagePreview,
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "lastSenderId" to currentUserId,
            "unreadCount" to FieldValue.increment(1),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        withTimeout(15_000L) {
            firestore.runBatch { batch ->
                batch.set(newMsgRef, messageData)
                batch.update(roomRef, roomUpdates)
            }.await()
        }

        finalMsgId
    }

    override suspend fun uploadAttachment(
        chatId: String,
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String
    ): Result<ChatAttachmentDataDto> = runCatching {
        val token = tokenManager.getAccessToken()
            ?: throw IllegalStateException("Chưa đăng nhập, không tìm thấy access token")
        val dataSource = chatRemoteDataSource
            ?: throw IllegalStateException("ChatRemoteDataSource chưa được cấu hình")
        val response = dataSource.uploadAttachment(
            accessToken = token,
            chatId = chatId,
            fileBytes = fileBytes,
            fileName = fileName,
            mimeType = mimeType
        )
        if (!response.success || response.data == null) {
            throw IllegalStateException(response.message ?: "Tải lên tệp đính kèm thất bại")
        }
        response.data
    }

    override suspend fun setTypingStatus(chatId: String, isTyping: Boolean): Result<Unit> = runCatching {
        val currentUserId = tokenManager.getUserId() ?: return@runCatching
        firebaseAuthManager.ensureSignedIn().getOrThrow()

        val typingDoc = firestore.collection("chats")
            .document(chatId)
            .collection("typing")
            .document(currentUserId)

        val data = hashMapOf<String, Any>(
            "isTyping" to isTyping,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        typingDoc.set(data).await()
    }

    override fun observeOtherUserTyping(chatId: String, otherUserId: String): Flow<Boolean> = callbackFlow {
        val authResult = firebaseAuthManager.ensureSignedIn()
        if (authResult.isFailure) {
            trySend(false)
            close()
            return@callbackFlow
        }

        val registration = firestore.collection("chats")
            .document(chatId)
            .collection("typing")
            .document(otherUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(false)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val isTyping = snapshot.getBoolean("isTyping") ?: false
                    val updatedAt = snapshot.getTimestamp("updatedAt")?.toDate()?.time ?: 0L
                    val now = System.currentTimeMillis()
                    // Người nhận chỉ coi là đang gõ khi updatedAt trong 5 giây gần nhất
                    val isValid = isTyping && (now - updatedAt <= 5_000L)
                    trySend(isValid)
                } else {
                    trySend(false)
                }
            }

        awaitClose { registration.remove() }
    }

    override suspend fun markMessagesAsRead(chatId: String, messageIds: List<String>): Result<Unit> = runCatching {
        if (messageIds.isEmpty()) return@runCatching
        firebaseAuthManager.ensureSignedIn().getOrThrow()

        val roomRef = firestore.collection("chats").document(chatId)
        val messagesColl = roomRef.collection("messages")

        firestore.runBatch { batch ->
            messageIds.forEach { msgId ->
                val msgRef = messagesColl.document(msgId)
                batch.update(msgRef, "readAt", FieldValue.serverTimestamp())
            }
            batch.update(roomRef, "unreadCount", 0)
        }.await()
    }

    private fun DocumentSnapshot.toChatRoom(): ChatRoom? {
        val senderId = getString("senderId") ?: return null
        val receiverId = getString("receiverId") ?: return null
        val exchangeId = getString("exchangeId") ?: id

        @Suppress("UNCHECKED_CAST")
        val participants = (get("participants") as? List<String>) ?: listOf(senderId, receiverId)

        return ChatRoom(
            id = id,
            exchangeId = exchangeId,
            senderId = senderId,
            receiverId = receiverId,
            senderName = getString("senderName") ?: "Đối tác",
            receiverName = getString("receiverName") ?: "Đối tác",
            senderAvatarUrl = getString("senderAvatarUrl"),
            receiverAvatarUrl = getString("receiverAvatarUrl"),
            skillOfferedName = getString("skillOfferedName") ?: "",
            skillWantedName = getString("skillWantedName") ?: "",
            status = getString("status") ?: "ACCEPTED",
            lastMessage = getString("lastMessage"),
            lastMessageAt = getTimestamp("lastMessageAt")?.toDate()?.time,
            lastSenderId = getString("lastSenderId"),
            unreadCount = getLong("unreadCount")?.toInt() ?: 0,
            participants = participants,
            updatedAt = getTimestamp("updatedAt")?.toDate()?.time
        )
    }

    private fun DocumentSnapshot.toChatMessage(chatId: String): ChatMessage? {
        val senderId = getString("senderId") ?: return null
        val content = getString("content") ?: return null
        val type = getString("type") ?: "TEXT"
        val createdAt = getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()
        val readAt = getTimestamp("readAt")?.toDate()?.time
        val fileUrl = getString("fileUrl")
        val fileName = getString("fileName")
        val fileSize = getLong("fileSize")

        @Suppress("UNCHECKED_CAST")
        val participants = (get("participants") as? List<String>) ?: emptyList()
        val isPending = metadata.hasPendingWrites()

        return ChatMessage(
            id = id,
            chatId = chatId,
            senderId = senderId,
            content = content,
            type = type,
            createdAt = createdAt,
            readAt = readAt,
            participants = participants,
            isPending = isPending,
            isFailed = false,
            fileUrl = fileUrl,
            fileName = fileName,
            fileSize = fileSize
        )
    }
}
