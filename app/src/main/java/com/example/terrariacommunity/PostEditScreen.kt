package com.example.terrariacommunity

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.res.painterResource
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import android.util.Log

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostEditScreen(postId: String?, postRepository: PostRepository = PostRepository(), onBack: () -> Unit, userRepository: UserRepository = UserRepository()) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val title = remember { mutableStateOf("") }
    val content = remember { mutableStateOf("") }
    val author = remember { mutableStateOf("") }
    val firebaseAuth = Firebase.auth
    val categories = listOf("공지", "질문", "자유") // Define available categories
    var selectedCategory by remember { mutableStateOf(categories[0]) } // Initialize with first category
    var expanded by remember { mutableStateOf(false) }
    var originalAuthorId by remember { mutableStateOf("") } // To store authorId of existing post
    var originalTimestamp by remember { mutableStateOf(System.currentTimeMillis()) } // To store timestamp of existing post
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) } // 선택된 이미지 URI 상태
    val videoUrl = remember { mutableStateOf("") } // 비디오 URL 상태 추가

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        selectedImageUri = uri
    }

    LaunchedEffect(postId) {
        if (!postId.isNullOrEmpty()) {
            coroutineScope.launch {
                val existingPost = postRepository.getPost(postId)
                existingPost?.let {
                    title.value = it.title
                    content.value = it.content
                    author.value = it.author
                    selectedCategory = it.category.ifBlank { categories[0] } // Load existing category
                    originalAuthorId = it.authorId // Store existing authorId
                    originalTimestamp = it.timestamp // Store existing timestamp
                    selectedImageUri = it.imageUrl?.let { Uri.parse(it) } // 기존 이미지 URL 로드
                    videoUrl.value = it.videoUrl ?: "" // 기존 비디오 URL 로드
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
                        Icon(painterResource(id = R.drawable.ic_arrow_back), contentDescription = "뒤로 가기")
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

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = {
                    expanded = !expanded
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedCategory,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("카테고리") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = {
                        expanded = false
                    }
                ) {
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(text = category) },
                            onClick = {
                                selectedCategory = category
                                expanded = false
                            }
                        )
                    }
                }
            }

            // 이미지 선택 버튼
            Button(
                onClick = { imagePickerLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Image, contentDescription = "이미지 선택")
                Spacer(Modifier.width(8.dp))
                Text("이미지 선택")
            }

            // 선택된 이미지 미리보기
            selectedImageUri?.let { uri ->
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = content.value,
                onValueChange = { content.value = it },
                label = { Text("내용") },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )

            OutlinedTextField(
                value = videoUrl.value,
                onValueChange = { videoUrl.value = it },
                label = { Text("비디오 URL (선택 사항)") },
                modifier = Modifier.fillMaxWidth()
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
                        val currentUser = firebaseAuth.currentUser
                        var uploadedImageUrl: String? = null

                        if (selectedImageUri != null) {
                            try {
                                uploadedImageUrl = postRepository.uploadImage(selectedImageUri!!)
                                if (uploadedImageUrl == null) {
                                    Toast.makeText(context, "이미지 업로드 실패", Toast.LENGTH_SHORT).show()
                                    return@launch // 이미지 업로드 실패 시 게시물 저장 중단
                                }
                            } catch (e: Exception) {
                                Log.e("PostEditScreen", "Error uploading image: ${e.message}", e)
                                Toast.makeText(context, "이미지 업로드 중 오류 발생", Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                        }

                        if (postId.isNullOrEmpty()) {
                            // Create new post
                            val newPost = Post(
                                title = title.value,
                                content = content.value,
                                author = author.value,
                                authorId = currentUser?.uid ?: "",
                                category = selectedCategory,
                                imageUrl = uploadedImageUrl, // 업로드된 이미지 URL 저장
                                videoUrl = videoUrl.value.ifBlank { null } // 비디오 URL 저장
                            )
                            postRepository.addOrUpdatePost(newPost) // addOrUpdatePost 사용
                            currentUser?.uid?.let { uid ->
                                userRepository.updatePoints(uid, 10L)
                            }
                        } else {
                            // Update existing post
                            val updatedPost = Post(
                                id = postId,
                                title = title.value,
                                content = content.value,
                                author = author.value,
                                authorId = originalAuthorId, // Use the stored originalAuthorId
                                timestamp = originalTimestamp, // Use the stored originalTimestamp
                                category = selectedCategory,
                                imageUrl = uploadedImageUrl, // 업로드된 이미지 URL 저장
                                videoUrl = videoUrl.value.ifBlank { null } // 비디오 URL 저장
                            )
                            postRepository.addOrUpdatePost(updatedPost) // addOrUpdatePost 사용
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