package com.yongsulim.terrariacommunity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomListScreen(
    userId: String,
    chatRepository: ChatRepository = ChatRepository(),
    onRoomClick: (ChatRoom) -> Unit,
    onCreateRoomClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var rooms by remember { mutableStateOf(listOf<ChatRoom>()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(userId) {
        coroutineScope.launch {
            rooms = chatRepository.getRoomsForUser(userId)
            isLoading = false
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("채팅방 목록") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateRoomClick) {
                Text("+")
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else if (rooms.isEmpty()) {
                Text("참여 중인 채팅방이 없습니다.", modifier = Modifier.padding(16.dp))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(rooms) { room ->
                        ChatRoomListItem(room = room, onClick = { onRoomClick(room) })
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
fun ChatRoomListItem(room: ChatRoom, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        if (room.profileImageUrl.isNotEmpty()) {
            AsyncImage(
                model = room.profileImageUrl,
                contentDescription = "채팅방 이미지",
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column {
            Text(text = room.name, style = MaterialTheme.typography.titleMedium)
            if (room.description.isNotBlank()) {
                Text(text = room.description, style = MaterialTheme.typography.bodySmall)
            }
            if (room.isGroup) {
                Text(text = "그룹 채팅", style = MaterialTheme.typography.bodySmall)
            } else {
                Text(text = "1:1 채팅", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
} 