package com.yongsulim.terrariacommunity

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminReportScreen(
    reportRepository: ReportRepository = ReportRepository(),
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var reports by remember { mutableStateOf<List<Report>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedReport by remember { mutableStateOf<Report?>() }

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            isLoading = true
            reports = reportRepository.getAllReports()
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("신고 내역(관리자)") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("<-") }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (reports.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("신고 내역이 없습니다.")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(reports) { report ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                                .clickable { selectedReport = report },
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("신고 유형: ${report.targetType}", style = MaterialTheme.typography.titleSmall)
                                Text("대상 ID: ${report.targetId}", style = MaterialTheme.typography.bodySmall)
                                Text("신고자: ${report.reporterId}", style = MaterialTheme.typography.bodySmall)
                                Text("사유: ${report.reason}", style = MaterialTheme.typography.bodySmall)
                                Text("시간: ${SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date(report.timestamp))}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        // 상세 다이얼로그
        selectedReport?.let { report ->
            AlertDialog(
                onDismissRequest = { selectedReport = ,
                title = { Text("신고 상세 정보") },
                text = {
                    Column {
                        Text("신고 유형: ${report.targetType}")
                        Text("대상 ID: ${report.targetId}")
                        Text("신고자: ${report.reporterId}")
                        Text("사유: ${report.reason}")
                        Text("시간: ${SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date(report.timestamp))}")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedReport = ) { Text("닫기") }
                }
            )
        }
    }
} 