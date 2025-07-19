package com.example.terrariacommunityapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(
    posts: List<Post>,
    popularPosts: List<Post>,
    onPostClick: (String) -> Unit,
    onAddPostClick: () -> Unit
) {
    val tabs = listOf("최신글", "인기글")
    var selectedTab by remember { mutableStateOf(tabs[0]) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("게시판") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddPostClick) {
                Icon(Icons.Default.Add, "새 게시물 추가")
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            TabRow(selectedTabIndex = tabs.indexOf(selectedTab)) {
                tabs.forEachIndexed { _, title ->
                    Tab(
                        selected = selectedTab == title,
                        onClick = { selectedTab = title },
                        text = { Text(title) }
                    )
                }
            }

            val currentPosts = if (selectedTab == "최신글") posts else popularPosts

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(currentPosts) { post ->
                    PostItem(post = post, onPostClick = onPostClick)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun PostItem(post: Post, onPostClick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPostClick(post.id) }
            .padding(16.dp)
    ) {
        Column {
            Text(text = post.title, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "작성자: ${post.author}", style = MaterialTheme.typography.bodySmall)
            Text(text = "날짜: ${SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date(post.timestamp))}", style = MaterialTheme.typography.bodySmall)
        }
    }
}