package com.example.terrariacommunityapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import androidx.compose.ui.Alignment
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomScreen(
    room: ChatRoom,
    userId: String,
    userName: String,
    chatRepository: ChatRepository = ChatRepository(),
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var newMessage by remember { mutableStateOf("") }
    var listenerRegistration by remember { mutableStateOf<com.google.firebase.firestore.ListenerRegistration?>(null) }
    val userRepository = remember { UserRepository() }

    // 실시간 메시지 수신 리스너 등록/해제
    DisposableEffect(room.id) {
        val reg = chatRepository.listenMessages(room.id) { msgs ->
            messages = msgs
        }
        listenerRegistration = reg
        onDispose {
            reg.remove()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(room.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("<-")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            // 프로필 이미지 및 설명 표시
            if (room.profileImageUrl.isNotEmpty() || room.description.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (room.profileImageUrl.isNotEmpty()) {
                        AsyncImage(
                            model = room.profileImageUrl,
                            contentDescription = "채팅방 이미지",
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Column {
                        if (room.description.isNotBlank()) {
                            Text(text = room.description, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp),
                reverseLayout = false
            ) {
                items(messages) { msg ->
                    ChatMessageItem(msg = msg, isMine = msg.senderId == userId)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                OutlinedTextField(
                    value = newMessage,
                    onValueChange = { newMessage = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("메시지 입력...") }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (newMessage.isNotBlank()) {
                            coroutineScope.launch {
                                val msg = ChatMessage(
                                    roomId = room.id,
                                    senderId = userId,
                                    senderName = userName,
                                    content = newMessage
                                )
                                chatRepository.sendMessage(msg)
                                // 메시지 전송 후 FCM 알림 전송
                                chatRepository.sendChatNotificationToParticipants(room, userId, newMessage, userRepository)
                                newMessage = ""
                            }
                        }
                    }
                ) {
                    Text("전송")
                }
            }
            // 채팅방 나가기 버튼
            Button(
                onClick = {
                    coroutineScope.launch {
                        val result = chatRepository.leaveRoom(room.id, userId)
                        if (result) {
                            onBack()
                        } else {
                            // 실패 시 스낵바 등 처리 가능
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text("채팅방 나가기")
            }
        }
    }
}

@Composable
fun ChatMessageItem(msg: ChatMessage, isMine: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Card(
            colors = if (isMine) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                     else CardDefaults.cardColors(),
            modifier = Modifier.widthIn(max = 260.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                if (!isMine) {
                    Text(text = msg.senderName, style = MaterialTheme.typography.labelSmall)
                }
                Text(text = msg.content, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
} 