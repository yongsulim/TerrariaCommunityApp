package com.example.terrariacommunityapp

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostEditScreen(postId: String?, postRepository: PostRepository = PostRepository(), onBack: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val title = remember { mutableStateOf("") }
    val content = remember { mutableStateOf("") }
    val author = remember { mutableStateOf("") }
    val firebaseAuth = Firebase.auth

    LaunchedEffect(postId) {
        if (!postId.isNullOrEmpty()) {
            coroutineScope.launch {
                val existingPost = postRepository.getPost(postId)
                existingPost?.let {
                    title.value = it.title
                    content.value = it.content
                    author.value = it.author
                }
            }
        } else {
            // New post: set author based on current user
            val currentUser = firebaseAuth.currentUser
            author.value = when {
                currentUser == null -> "알 수 없음" // Should not happen if user is logged in
                currentUser.isAnonymous -> "게스트"
                currentUser.displayName != null -> currentUser.displayName!!
                else -> "알 수 없음"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (postId.isNullOrEmpty()) "새 게시물 작성" else "게시물 수정") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로 가기")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = title.value,
                onValueChange = { title.value = it },
                label = { Text("제목") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = content.value,
                onValueChange = { content.value = it },
                label = { Text("내용") },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            OutlinedTextField(
                value = author.value,
                onValueChange = { author.value = it },
                label = { Text("작성자") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true // Make author field read-only
            )
            Button(
                onClick = {
                    coroutineScope.launch {
                        if (postId.isNullOrEmpty()) {
                            // Create new post
                            val newPost = Post(
                                title = title.value,
                                content = content.value,
                                author = author.value
                            )
                            postRepository.addPost(newPost)
                        } else {
                            // Update existing post
                            val updatedPost = Post(
                                id = postId,
                                title = title.value,
                                content = content.value,
                                author = author.value,
                                timestamp = postRepository.getPost(postId)?.timestamp ?: System.currentTimeMillis()
                            )
                            postRepository.updatePost(updatedPost)
                        }
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (postId.isNullOrEmpty()) "게시물 작성" else "게시물 수정")
            }
        }
    }
}