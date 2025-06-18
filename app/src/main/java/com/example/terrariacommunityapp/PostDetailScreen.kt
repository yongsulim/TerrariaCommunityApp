package com.example.terrariacommunityapp

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(postId: String, postRepository: PostRepository = PostRepository(), onBack: () -> Unit, onEditPost: (String) -> Unit, onDeletePost: (String) -> Unit) {
    val post = remember { mutableStateOf<Post?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(postId) {
        coroutineScope.launch {
            post.value = postRepository.getPost(postId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(post.value?.title ?: "게시물 불러오는 중...") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로 가기")
                    }
                },
                actions = {
                    post.value?.let { p ->
                        IconButton(onClick = { onEditPost(p.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "수정")
                        }
                        IconButton(onClick = { onDeletePost(p.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "삭제")
                        }
                    }
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
            post.value?.let { p ->
                Text(text = p.title, style = MaterialTheme.typography.headlineLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "작성자: ${p.author}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "날짜: ${SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date(p.timestamp))}", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = p.content, style = MaterialTheme.typography.bodyLarge)
            } ?: run {
                // Loading or Error State
                CircularProgressIndicator()
            }
        }
    }
}