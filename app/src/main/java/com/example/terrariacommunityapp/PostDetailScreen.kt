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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(postId: String, postRepository: PostRepository = PostRepository(), onBack: () -> Unit, onEditPost: (String) -> Unit, onDeletePost: (String) -> Unit) {
    val post = remember { mutableStateOf<Post?>(null) }
    val comments = remember { mutableStateListOf<Comment>() }
    val newCommentContent = remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val commentRepository = remember { CommentRepository() }
    val firebaseAuth = Firebase.auth
    val currentUserUid = firebaseAuth.currentUser?.uid

    LaunchedEffect(postId) {
        coroutineScope.launch {
            post.value = postRepository.getPost(postId)
            if (postId.isNotEmpty()) {
                comments.clear()
                comments.addAll(commentRepository.getCommentsForPost(postId))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(post.value?.title ?: "게시물 불러오는 중...") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(id = R.drawable.ic_arrow_back), contentDescription = "뒤로 가기")
                    }
                },
                actions = {
                    post.value?.let { p ->
                        IconButton(onClick = { onEditPost(p.id) }) {
                            Icon(painterResource(id = R.drawable.ic_edit), contentDescription = "수정")
                        }
                        IconButton(onClick = { onDeletePost(p.id) }) {
                            Icon(painterResource(id = R.drawable.ic_delete), contentDescription = "삭제")
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
                Text(text = "카테고리: ${p.category.ifBlank { "없음" }}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "작성자: ${p.author}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "날짜: ${SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date(p.timestamp))}", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = p.content, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))

                // 이미지 미리보기 (기존 코드)
                p.imageUrl?.let { imageUrl ->
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 비디오 임베드 (AndroidView로 교체)
                p.videoUrl?.let { videoUrl ->
                    val videoId = extractYouTubeVideoId(videoUrl)
                    if (!videoId.isNullOrBlank()) {
                        YoutubeWebView(videoId)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isLiked = p.likedBy.contains(currentUserUid)
                    IconButton(onClick = {
                        currentUserUid?.let { uid ->
                            coroutineScope.launch {
                                postRepository.togglePostLike(p.id, uid)
                                post.value = postRepository.getPost(p.id)
                            }
                        }
                    }) {
                        Icon(
                            painterResource(id = if (isLiked) R.drawable.ic_thumb_up else R.drawable.ic_thumb_down),
                            contentDescription = "좋아요",
                            tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(text = "${p.likesCount}")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                OutlinedTextField(
                    value = newCommentContent.value,
                    onValueChange = { newCommentContent.value = it },
                    label = { Text("댓글 작성") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        currentUserUid?.let { _ ->
                            if (newCommentContent.value.isNotBlank()) {
                                coroutineScope.launch {
                                    val authorName = firebaseAuth.currentUser?.displayName ?: if (firebaseAuth.currentUser?.isAnonymous == true) "게스트" else "알 수 없음"
                                    val newComment = Comment(
                                        postId = postId,
                                        author = authorName,
                                        content = newCommentContent.value
                                    )
                                    commentRepository.addComment(newComment)
                                    newCommentContent.value = ""
                                    comments.clear()
                                    comments.addAll(commentRepository.getCommentsForPost(postId))
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("댓글 작성")
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "댓글", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))

                if (comments.isEmpty()) {
                    Text("아직 댓글이 없습니다.")
                } else {
                    LazyColumn {
                        items(comments) { comment ->
                            CommentItem(
                                comment = comment,
                                currentUserUid = currentUserUid,
                                commentRepository = commentRepository,
                                onCommentUpdated = {
                                    coroutineScope.launch {
                                        comments.clear()
                                        comments.addAll(commentRepository.getCommentsForPost(postId))
                                    }
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            } ?: run {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun CommentItem(
    comment: Comment,
    currentUserUid: String?,
    commentRepository: CommentRepository,
    onCommentUpdated: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var showEditDialog by remember { mutableStateOf(false) }
    var editContent by remember { mutableStateOf(comment.content) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = comment.author, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = comment.content, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "날짜: ${SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date(comment.timestamp))}", 
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            // 댓글 액션 버튼들
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 좋아요 버튼
                val isLiked = comment.likedBy.contains(currentUserUid)
                IconButton(
                    onClick = {
                        currentUserUid?.let { uid ->
                            coroutineScope.launch {
                                commentRepository.toggleCommentLike(comment.id, uid)
                                onCommentUpdated()
                            }
                        }
                    }
                ) {
                    Icon(
                        painterResource(id = R.drawable.ic_thumb_up),
                        contentDescription = "댓글 좋아요",
                        tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(text = "${comment.likesCount}", style = MaterialTheme.typography.bodySmall)
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // 싫어요 버튼
                val isDisliked = comment.dislikedBy.contains(currentUserUid)
                IconButton(
                    onClick = {
                        currentUserUid?.let { uid ->
                            coroutineScope.launch {
                                commentRepository.toggleCommentDislike(comment.id, uid)
                                onCommentUpdated()
                            }
                        }
                    }
                ) {
                    Icon(
                        painterResource(id = R.drawable.ic_thumb_down),
                        contentDescription = "댓글 싫어요",
                        tint = if (isDisliked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(text = "${comment.dislikedBy.size}", style = MaterialTheme.typography.bodySmall)
                
                // 댓글 작성자인 경우 수정/삭제 버튼 표시
                if (comment.author == (Firebase.auth.currentUser?.displayName ?: "알 수 없음")) {
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // 수정 버튼
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(
                            painterResource(id = R.drawable.ic_edit),
                            contentDescription = "댓글 수정"
                        )
                    }
                    
                    // 삭제 버튼
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                commentRepository.deleteComment(comment.id)
                                onCommentUpdated()
                            }
                        }
                    ) {
                        Icon(
                            painterResource(id = R.drawable.ic_delete),
                            contentDescription = "댓글 삭제"
                        )
                    }
                }
            }
        }
    }
    
    // 댓글 수정 다이얼로그
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("댓글 수정") },
            text = {
                OutlinedTextField(
                    value = editContent,
                    onValueChange = { editContent = it },
                    label = { Text("댓글 내용") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            commentRepository.updateComment(comment.id, editContent)
                            onCommentUpdated()
                            showEditDialog = false
                        }
                    }
                ) {
                    Text("수정")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("취소")
                }
            }
        )
    }
}

private fun extractYouTubeVideoId(url: String): String? {
    val regex = """(?<=watch\?v=|/videos/|embed\/|youtu.be\/|\/v\/|\/e\/|watch\?v%3D|watch\?feature=player_embedded&v=|%2Fvideos%2F|embedCEmbedCEmbedC|youtu.be%2F|%2Fv\/|eEmbedCEmbedC)([^#\&\?\n]*)""".trimIndent().toRegex()
    val matchResult = regex.find(url)
    return matchResult?.value
}

@Composable
fun YoutubeWebView(videoId: String) {
    val html = """
        <html>
        <body style=\"margin:0; padding:0;\">
        <iframe width=\"100%\" height=\"100%\" src=\"https://www.youtube.com/embed/$videoId\" frameborder=\"0\" allowfullscreen></iframe>
        </body>
        </html>
    """.trimIndent()
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    )
}