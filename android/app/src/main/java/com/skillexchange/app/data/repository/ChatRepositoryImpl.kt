package com.skillexchange.app.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.skillexchange.app.core.auth.IFirebaseAuthManager
import com.skillexchange.app.core.security.TokenManager
import com.skillexchange.app.domain.model.chat.ChatMessage
import com.skillexchange.app.domain.model.chat.ChatRoom
import com.skillexchange.app.domain.repository.IChatRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ChatRepositoryImpl(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val firebaseAuthManager: IFirebaseAuthManager,
    private val tokenManager: TokenManager
) : IChatRepository {

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
        messageId: String
    ): Result<String> = runCatching {
        val trimmed = content.trim()
        require(trimmed.isNotEmpty()) { "Nội dung tin nhắn không được để trống" }
        require(trimmed.length <= 1000) { "Tin nhắn không được vượt quá 1000 ký tự" }
        require(participants.isNotEmpty()) { "Danh sách thành viên không được rỗng" }

        val currentUserId = tokenManager.getUserId()
            ?: throw IllegalStateException("Không tìm thấy thông tin phiên đăng nhập người dùng")

        firebaseAuthManager.ensureSignedIn().getOrThrow()

        val finalMsgId = messageId.ifBlank {
            firestore.collection("chats").document(chatId).collection("messages").document().id
        }

        val roomRef = firestore.collection("chats").document(chatId)
        val newMsgRef = roomRef.collection("messages").document(finalMsgId)

        val messageData = hashMapOf<String, Any?>(
            "participants" to participants,
            "senderId" to currentUserId,
            "content" to trimmed,
            "type" to "TEXT",
            "createdAt" to FieldValue.serverTimestamp(),
            "readAt" to null
        )

        val roomUpdates = hashMapOf<String, Any>(
            "lastMessage" to trimmed,
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "lastSenderId" to currentUserId,
            "unreadCount" to FieldValue.increment(1),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        firestore.runBatch { batch ->
            batch.set(newMsgRef, messageData)
            batch.update(roomRef, roomUpdates)
        }.await()

        finalMsgId
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
            isFailed = false
        )
    }
}
