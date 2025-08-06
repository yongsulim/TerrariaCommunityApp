package com.example.terrariacommunity

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateChatRoomScreen(
    currentUserId: String,
    onRoomCreated: (ChatRoom) -> Unit,
    onBack: () -> Unit,
    chatRepository: ChatRepository = ChatRepository()
) {
    val coroutineScope = rememberCoroutineScope()
    var roomName by remember { mutableStateOf("") }
    var participantInput by remember { mutableStateOf("") } // 쉼표로 UID 구분
    var isGroup by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    var profileImageUrl by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("채팅방 생성") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("<-") }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            OutlinedTextField(
                value = roomName,
                onValueChange = { roomName = it },
                label = { Text("방 이름") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = participantInput,
                onValueChange = { participantInput = it },
                label = { Text("참여자 UID (쉼표로 구분)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = profileImageUrl,
                onValueChange = { profileImageUrl = it },
                label = { Text("프로필 이미지 URL") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("채팅방 설명") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isGroup, onCheckedChange = { isGroup = it })
                Text("그룹 채팅")
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (errorMsg.isNotBlank()) {
                Text(errorMsg, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))
            }
            Button(
                onClick = {
                    val participants = participantInput.split(",").map { it.trim() }.filter { it.isNotBlank() }.toMutableSet()
                    participants.add(currentUserId)
                    if (roomName.isBlank()) {
                        errorMsg = "방 이름을 입력하세요."
                        return@Button
                    }
                    if (participants.size < 2) {
                        errorMsg = "참여자를 1명 이상 입력하세요."
                        return@Button
                    }
                    isLoading = true
                    coroutineScope.launch {
                        val room = ChatRoom(
                            name = roomName,
                            participantIds = participants.toList(),
                            isGroup = isGroup,
                            profileImageUrl = profileImageUrl,
                            description = description
                        )
                        val roomId = chatRepository.createRoom(room)
                        isLoading = false
                        if (roomId != null) {
                            onRoomCreated(room.copy(id = roomId))
                        } else {
                            errorMsg = "채팅방 생성에 실패했습니다."
                        }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("채팅방 생성")
            }
        }
    }
} 