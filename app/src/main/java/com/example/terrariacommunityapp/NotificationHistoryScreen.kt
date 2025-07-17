package com.example.terrariacommunityapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.DismissDirection
import androidx.compose.material3.DismissValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismiss
import androidx.compose.material3.rememberDismissState
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
                    val dismissState = rememberDismissState(
                        confirmValueChange = {
                            if (it == DismissValue.DismissedToStart || it == DismissValue.DismissedToEnd) {
                                coroutineScope.launch {
                                    notificationRepository.deleteNotification(notification.id)
                                    notifications = notificationRepository.getNotifications(userId)
                                }
                            }
                            true
                        }
                    )
                    SwipeToDismiss(
                        state = dismissState,
                        background = {},
                        directions = setOf(DismissDirection.EndToStart, DismissDirection.StartToEnd),
                        dismissContent = {
                            NotificationItem(
                                notification = notification,
                                onClick = {
                                    coroutineScope.launch {
                                        notificationRepository.markAsRead(notification.id)
                                        notifications = notificationRepository.getNotifications(userId)
                                        // postId가 있으면 게시글 상세로 이동
                                        if (notification.postId.isNotEmpty()) {
                                            navController.navigate("post_detail/${notification.postId}")
                                        }
                                        // 추후 commentId 등 상세 이동 추가 가능
                                    }
                                }
                            )
                        }
                    )
                }
            }
        }
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