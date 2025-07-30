package com.example.terrariacommunityapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationHistoryScreen(
    userId: String,
    navController: NavController,
    notificationRepository: NotificationRepository = NotificationRepository()
) {
    val coroutineScope = rememberCoroutineScope()
    var notifications by remember { mutableStateOf<List<Notification>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    // 알림 상세 다이얼로그 상태
    var selectedNotification by remember { mutableStateOf<Notification?>(null) }

    LaunchedEffect(userId) {
        isLoading = true
        notifications = notificationRepository.getNotifications(userId)
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "알림 히스토리",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = {
                coroutineScope.launch {
                    notificationRepository.markAllAsRead(userId)
                    notifications = notificationRepository.getNotifications(userId)
                }
            }) {
                Text("모두 읽음")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (notifications.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("알림이 없습니다.")
            }
        } else {
            LazyColumn {
                items(notifications, key = { it.id }) { notification ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = {
                            if (it == SwipeToDismissBoxValue.EndToStart || it == SwipeToDismissBoxValue.StartToEnd) {
                                coroutineScope.launch {
                                    notificationRepository.deleteNotification(notification.id)
                                    notifications = notificationRepository.getNotifications(userId)
                                }
                                true
                            } else {
                                false
                            }
                        }
                    )
                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = { /* 필요시 배경 UI */ },
                        content = {
                            NotificationItem(
                                notification = notification,
                                onClick = {
                                    coroutineScope.launch {
                                        notificationRepository.markAsRead(notification.id)
                                        notifications = notificationRepository.getNotifications(userId)
                                        selectedNotification = notification // 상세 다이얼로그 표시
                                    }
                                }
                            )
                        }
                    )
                }
            }
        }
    }
    // 알림 상세 다이얼로그
    selectedNotification?.let { noti ->
        AlertDialog(
            onDismissRequest = { selectedNotification = null },
            title = { Text("알림 상세") },
            text = {
                Column {
                    Text("내용: ${noti.content}")
                    Text("유형: ${noti.type}")
                    Text("시간: ${SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date(noti.timestamp))}")
                    when (noti.type) {
                        "comment" -> {
                    if (noti.postId.isNotEmpty()) {
                        TextButton(onClick = {
                            navController.navigate("post_detail/${noti.postId}")
                            selectedNotification = null
                        }) { Text("관련 게시글 보기") }
                    }
                    if (noti.commentId.isNotEmpty()) {
                                Text("관련 댓글 ID: ${noti.commentId}")
                            }
                        }
                        "chat" -> {
                            if (noti.postId.isNotEmpty()) {
                                TextButton(onClick = {
                                    navController.navigate("chat_room/${noti.postId}") // postId를 채팅방 ID로 사용한다고 가정
                                    selectedNotification = null
                                }) { Text("채팅방으로 이동") }
                            }
                        }
                        "system" -> {
                            Text("시스템 알림입니다.")
                        }
                        "mention" -> {
                            if (noti.postId.isNotEmpty()) {
                                TextButton(onClick = {
                                    navController.navigate("post_detail/${noti.postId}")
                                    selectedNotification = null
                                }) { Text("언급된 게시글 보기") }
                            }
                        }
                        "popular" -> {
                            if (noti.postId.isNotEmpty()) {
                                TextButton(onClick = {
                                    navController.navigate("post_detail/${noti.postId}")
                                    selectedNotification = null
                                }) { Text("인기글 보기") }
                            }
                        }
                        else -> {
                            // 기타 알림 유형 처리
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedNotification = null }) { Text("닫기") }
            }
        )
    }
}

@Composable
fun NotificationItem(
    notification: Notification,
    onClick: () -> Unit
) {
    val date = remember(notification.timestamp) {
        SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date(notification.timestamp))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(if (notification.isRead) Color(0xFFF5F5F5) else Color(0xFFE3F2FD))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = notification.content,
                fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                text = date,
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
        if (!notification.isRead) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "●",
                color = Color(0xFF1976D2),
                fontSize = 18.sp
            )
        }
    }
} 