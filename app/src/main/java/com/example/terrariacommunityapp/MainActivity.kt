package com.example.terrariacommunityapp

import android.content.ContentValues.TAG
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.terrariacommunityapp.ui.theme.TerrariaCommunityAppTheme
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.navercorp.nid.NaverIdLoginSDK
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PhotoCamera
import android.net.Uri
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import com.example.terrariacommunityapp.ThemeSettingsScreen
import com.example.terrariacommunityapp.ThemeMode
import androidx.compose.ui.graphics.Color
import com.example.terrariacommunityapp.ui.theme.NotoSansKR
import com.example.terrariacommunityapp.ui.theme.DefaultFontFamily
import androidx.compose.ui.text.font.FontFamily

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Board : Screen("board", "게시판", Icons.Filled.Home)
    object Search : Screen("search", "검색", Icons.Filled.Search)
    object Favorite : Screen("favorite", "즐겨찾기", Icons.Filled.Favorite)
    object MyPage : Screen("mypage", "마이페이지", Icons.Filled.Person)
    object Settings : Screen("settings", "설정", Icons.Filled.Settings) // 기존 유지(마이페이지에서 이동 가능)
}

class MainActivity : ComponentActivity() {
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var firebaseAuth: FirebaseAuth
    private val postRepository = PostRepository()
    private val userRepository = UserRepository()
    private val posts = mutableStateListOf<Post>()
    private val popularPosts = mutableStateListOf<Post>()
    private var mInterstitialAd: InterstitialAd? = null
    private var _selectedCategory by mutableStateOf<String?>(null)
    private var _searchQuery by mutableStateOf("")
    private var _currentBoardContentType by mutableStateOf("최신글")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
        firebaseAuth = Firebase.auth

        // Naver Login SDK 초기화
        NaverIdLoginSDK.initialize(this,
            getString(R.string.naver_client_id),
            getString(R.string.naver_client_secret),
            getString(R.string.naver_client_name))

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }
            // Get new FCM registration token
            val token = task.result
            // Log and toast
            Log.d(TAG, "FCM Registration Token: $token")
            // Update user's FCM token in Firestore
            firebaseAuth.currentUser?.uid?.let { uid ->
                lifecycleScope.launch {
                    val user = userRepository.getUser(uid)
                    val updatedUser = user?.copy(fcmToken = token) ?: User(uid = uid, displayName = firebaseAuth.currentUser?.displayName ?: "알 수 없음", fcmToken = token)
                    userRepository.createUserOrUpdate(updatedUser)
                }
            }
        }

        // Initialize Google Mobile Ads SDK
        MobileAds.initialize(this) {}

        // Load Interstitial Ad
        loadInterstitialAd()

        enableEdgeToEdge()
        setContent {
            // Use a mutable state to control the theme
            val systemDarkTheme = isSystemInDarkTheme()
            var darkThemeMode by remember { mutableStateOf(ThemeMode.SYSTEM) }
            var darkTheme by remember { mutableStateOf(systemDarkTheme) }
            var primaryColor by remember { mutableStateOf(Color(0xFF6650a4)) } // 기본 프라이머리 컬러
            var fontFamily by remember { mutableStateOf<FontFamily>(DefaultFontFamily) }
            val startDestination = remember { mutableStateOf<String?>(null) }
            val startArgs = remember { mutableStateOf<String?>(null) }

            // FCM 딥링크 인텐트 처리
            LaunchedEffect(Unit) {
                intent?.extras?.let { extras ->
                    val type = extras.getString("type")
                    when (type) {
                        "comment", "mention", "popular" -> {
                            val postId = extras.getString("postId")
                            if (!postId.isNullOrBlank()) {
                                startDestination.value = "post_detail/$postId"
                            }
                        }
                        "chat" -> {
                            val chatRoomId = extras.getString("postId")
                            if (!chatRoomId.isNullOrBlank()) {
                                startDestination.value = "chat_room/$chatRoomId"
                            }
                        }
                        // 기타 유형 확장 가능
                    }
                }
            }

            // 테마 모드 적용
            val isDark = when (darkThemeMode) {
                ThemeMode.SYSTEM -> systemDarkTheme
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            TerrariaCommunityAppTheme(
                darkTheme = isDark,
                fontFamily = fontFamily
            ) {
                val navController = rememberNavController()
                val coroutineScope = rememberCoroutineScope() // Compose scope
                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = currentBackStackEntry?.destination?.route
                var showBottomBar by remember { mutableStateOf(false) }

                LaunchedEffect(startDestination.value) {
                    startDestination.value?.let { route ->
                        navController.navigate(route) {
                            popUpTo(0) { inclusive = true }
                        }
                        startDestination.value = null // 한 번만 이동
                    }
                }

                Scaffold(
                    bottomBar = {
                        if (showBottomBar) { // Use the state variable here
                            NavigationBar {
                                val items = listOf(Screen.Board, Screen.Search, Screen.Favorite, Screen.MyPage)
                                items.forEach { screen ->
                                    NavigationBarItem(
                                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                                        label = { Text(screen.title) },
                                        selected = currentRoute == screen.route,
                                        onClick = {
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.startDestinationId) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    )
                                }
                            }
                            AdBanner(modifier = Modifier.fillMaxWidth())
                        }
                    }
                ) { paddingValues ->
                    NavHost(
                        navController = navController,
                        startDestination = "login",
                        modifier = Modifier.padding(paddingValues)
                    ) {
                        composable("login") {
                            LaunchedEffect(Unit) { showBottomBar = false }
                            LoginScreen(
                                modifier = Modifier.fillMaxSize(),
                                googleSignInClient = googleSignInClient,
                                firebaseAuth = firebaseAuth,
                                onSignInSuccess = {
                                    navController.navigate(Screen.Board.route) {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable(Screen.Board.route) {
                            LaunchedEffect(Unit) { // 게시판 화면이 구성될 때 게시물 새로고침 및 하단 바 표시
                                refreshPosts(_selectedCategory, _currentBoardContentType, _searchQuery)
                                showBottomBar = true // Show after data is loaded
                            }
                            BoardScreen(
                                posts = posts,
                                popularPosts = popularPosts,
                                onPostClick = { postId ->
                                    navController.navigate("post_detail/$postId")
                                },
                                onAddPostClick = {
                                    navController.navigate("post_edit")
                                }
                            )
                        }
                        composable(Screen.Search.route) {
                            LaunchedEffect(Unit) { showBottomBar = true }
                            SearchScreen(
                                postRepository = postRepository,
                                onPostClick = { postId -> navController.navigate("post_detail/$postId") }
                            )
                        }
                        composable(Screen.Favorite.route) {
                            LaunchedEffect(Unit) { showBottomBar = true }
                            FavoriteScreen(
                                postRepository = postRepository,
                                onPostClick = { postId -> navController.navigate("post_detail/$postId") }
                            )
                        }
                        composable(Screen.MyPage.route) {
                            LaunchedEffect(Unit) { showBottomBar = true }
                            MyPageScreen(
                                onSettingsClick = { navController.navigate(Screen.Settings.route) },
                                onMyPostsClick = { navController.navigate("my_posts") },
                                onFavoritePostsClick = { navController.navigate("favorite_posts") },
                                onLogoutClick = {
                                    firebaseAuth.signOut()
                                    Toast.makeText(this@MainActivity, "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show()
                                    navController.navigate("login") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                },
                                onEditProfileClick = { navController.navigate("edit_profile") },
                                onNotificationSettingsClick = { navController.navigate("notification_settings") },
                                onNotificationHistoryClick = { navController.navigate("notification_history") },
                                onAdminReportsClick = { navController.navigate("admin_reports") } // 추가
                            )
                        }
                        composable("notification_history") {
                            val userId = firebaseAuth.currentUser?.uid ?: ""
                            NotificationHistoryScreen(userId = userId, navController = navController)
                        }
                        composable("my_posts") {
                            MyPostsScreen(postRepository = postRepository, onBack = { navController.popBackStack() })
                        }
                        composable("favorite_posts") {
                            FavoritePostsScreen(postRepository = postRepository, onBack = { navController.popBackStack() })
                        }
                        composable(Screen.Settings.route) {
                            LaunchedEffect(Unit) { showBottomBar = true }
                            SettingsScreen(
                                modifier = Modifier.fillMaxSize(),
                                currentDarkTheme = darkTheme, // Pass current theme state
                                onToggleTheme = { darkTheme = it }, // Pass callback to toggle theme
                                firebaseAuth = firebaseAuth,
                                userRepository = userRepository // 인스턴스 전달
                            )
                        }
                        composable(
                            "post_detail/{postId}",
                            arguments = listOf(navArgument("postId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val postId = backStackEntry.arguments?.getString("postId")
                            if (postId != null) {
                                PostDetailScreen(
                                    postId = postId,
                                    postRepository = postRepository,
                                    onBack = { navController.popBackStack() },
                                    onEditPost = { editPostId -> navController.navigate("post_edit/$editPostId") },
                                    onDeletePost = {
                                        coroutineScope.launch { // Use Compose coroutineScope
                                            postRepository.deletePost(postId)
                                            refreshPosts()
                                            navController.popBackStack() // Go back after delete
                                        }
                                    }
                                )
                            }
                        }
                        composable(
                            "post_edit/{postId}",
                            arguments = listOf(navArgument("postId") { type = NavType.StringType; nullable = true })
                        ) { backStackEntry ->
                            val postId = backStackEntry.arguments?.getString("postId")
                            PostEditScreen(
                                postId = postId,
                                postRepository = postRepository,
                                onBack = {
                                    refreshPosts()
                                    navController.popBackStack()
                                }
                            )
                        }
                        composable("post_edit") {
                            PostEditScreen(
                                postId = null,
                                postRepository = postRepository,
                                onBack = {
                                    refreshPosts()
                                    navController.popBackStack()
                                }
                            )
                        }
                        composable("edit_profile") {
                            EditProfileScreen(
                                userRepository = userRepository,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("notification_settings") {
                            NotificationSettingsScreen(
                                userRepository = userRepository,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        // 테마 설정 화면 진입 예시 (설정/마이페이지 등에서 이동)
                        composable("theme_settings") {
                            ThemeSettingsScreen(
                                currentThemeMode = darkThemeMode,
                                currentPrimaryColor = primaryColor,
                                onThemeModeChange = { mode ->
                                    darkThemeMode = mode
                                },
                                onPrimaryColorChange = { color ->
                                    primaryColor = color
                                },
                                onFontFamilyChange = { family ->
                                    fontFamily = family
                                },
                                currentFontFamily = fontFamily,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        // 관리자 신고 내역 페이지 진입점 추가 (예시: 마이페이지에서)
                        composable("admin_reports") {
                            AdminReportScreen(onBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }

    private fun refreshPosts(category: String? = null, currentTab: String = "최신글", searchQuery: String = "") {
        lifecycleScope.launch {
            posts.clear()
            popularPosts.clear()

            if (currentTab == "최신글") {
                if (searchQuery.isBlank()) {
                    posts.addAll(postRepository.getPosts(category))
                } else {
                    posts.addAll(postRepository.searchPosts(searchQuery, category))
                }
            } else {
                popularPosts.addAll(postRepository.getPopularPosts())
            }
        }
    }

    private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(this, "ca-app-pub-9471537431449262/8513788914", adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                mInterstitialAd = interstitialAd
                Log.d(TAG, "Interstitial Ad was loaded.")
                mInterstitialAd?.fullScreenContentCallback = object: com.google.android.gms.ads.FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        Log.d(TAG, "Ad was dismissed.")
                        mInterstitialAd = null
                        loadInterstitialAd() // Load a new ad after dismissal
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                        Log.d(TAG, "Ad failed to show.")
                        mInterstitialAd = null
                        loadInterstitialAd() // Load a new ad after failure
                    }

                    override fun onAdShowedFullScreenContent() {
                        Log.d(TAG, "Ad showed fullscreen content.")
                    }
                }
            }

            override fun onAdFailedToLoad(loadAdError: com.google.android.gms.ads.LoadAdError) {
                Log.d(TAG, loadAdError.message)
                mInterstitialAd = null
            }
        })
    }

    private fun showInterstitialAd() {
        if (mInterstitialAd != null) {
            mInterstitialAd?.show(this)
        } else {
            Log.d(TAG, "The interstitial ad wasn't ready yet.")
        }
    }
}

@Composable
fun LoginScreen(modifier: Modifier = Modifier, googleSignInClient: GoogleSignInClient, firebaseAuth: FirebaseAuth, onSignInSuccess: () -> Unit) {
    val googleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(it.data)
        try {
            val account = task.getResult(ApiException::class.java)!!
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener { authTask ->
                    if (authTask.isSuccessful) {
                        Log.d(TAG, "Firebase Google sign-in successful")
                        onSignInSuccess()
                    } else {
                        Log.w(TAG, "Firebase Google sign-in failed", authTask.exception)
                    }
                }
        } catch (e: ApiException) {
            Log.w(TAG, "Google sign in failed", e)
        }
    }

    // 슬라이드 애니메이션을 위한 상태
    var slideOffset by remember { mutableStateOf(0f) }
    
    // 무한 슬라이드 애니메이션
    LaunchedEffect(Unit) {
        while (true) {
            animate(
                initialValue = 0f,
                targetValue = -100f,
                animationSpec = tween(8000, easing = LinearEasing)
            ) { value, _ ->
                slideOffset = value
            }
            delay(1000)
            animate(
                initialValue = -100f,
                targetValue = 0f,
                animationSpec = tween(8000, easing = LinearEasing)
            ) { value, _ ->
                slideOffset = value
            }
            delay(1000)
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // 슬라이드 애니메이션 배경 이미지
        Image(
            painter = painterResource(id = R.drawable.main),
            contentDescription = "배경 이미지",
            modifier = Modifier
                .fillMaxSize()
                .offset(x = with(LocalDensity.current) { slideOffset.dp }),
            contentScale = ContentScale.Crop
        )
        
        // 어두운 오버레이 (텍스트 가독성을 위해)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
        )
        
        // 로그인 UI (배경 위에 오버레이)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 앱 로고/제목
            Text(
                text = "테라리아 커뮤니티 앱",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 48.dp)
            )
            
            // 서브타이틀
            Text(
                text = "테라리아 팬들을 위한 커뮤니티",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = Color.White.copy(alpha = 0.9f)
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 64.dp)
            )
            
            // 로그인 버튼들
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = {
                            Log.d(TAG, "Google Login button clicked")
                            googleLauncher.launch(googleSignInClient.signInIntent)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Google 로그인",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedButton(
                        onClick = {
                            Log.d(TAG, "Guest Login button clicked")
                            firebaseAuth.signInAnonymously()
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        Log.d(TAG, "Firebase Guest sign-in successful")
                                        onSignInSuccess()
                                    } else {
                                        Log.w(TAG, "Firebase Guest sign-in failed", task.exception)
                                    }
                                }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "게스트 로그인",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 하단 정보 텍스트
            Text(
                text = "로그인하여 커뮤니티에 참여하세요",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White.copy(alpha = 0.8f)
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun SettingsScreen(modifier: Modifier = Modifier, currentDarkTheme: Boolean, onToggleTheme: (Boolean) -> Unit, firebaseAuth: FirebaseAuth, userRepository: UserRepository) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var nickname by remember { mutableStateOf(firebaseAuth.currentUser?.displayName ?: "게스트") }
    var currentUserData by remember { mutableStateOf<User?>(null) }

    LaunchedEffect(firebaseAuth.currentUser?.uid) {
        firebaseAuth.currentUser?.uid?.let { uid ->
            currentUserData = userRepository.getUser(uid)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (currentDarkTheme) "라이트 모드" else "다크 모드",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.weight(1f))
            Switch(
                checked = currentDarkTheme,
                onCheckedChange = { onToggleTheme(it) }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "닉네임 관리", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it },
            label = { Text("닉네임") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                coroutineScope.launch {
                    val user = firebaseAuth.currentUser
                    if (user != null) {
                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .setDisplayName(nickname)
                            .build()
                        try {
                            user.updateProfile(profileUpdates).await()
                            Log.d(TAG, "User profile updated.")
                            val updatedUser = User(uid = user.uid, displayName = nickname, points = currentUserData?.points ?: 0L, badges = currentUserData?.badges ?: emptyList())
                            userRepository.createUserOrUpdate(updatedUser)
                            currentUserData = updatedUser
                            Toast.makeText(context, "닉네임이 업데이트되었습니다.", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Log.w(TAG, "Error updating profile.", e)
                            Toast.makeText(context, "닉네임 업데이트 실패: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(context, "로그인된 사용자가 없습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("닉네임 저장")
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "활동 정보", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        currentUserData?.let { user ->
            Text(text = "포인트: ${user.points}", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "뱃지: ${if (user.badges.isEmpty()) "없음" else user.badges.joinToString()}", style = MaterialTheme.typography.bodyLarge)
        } ?: Text(text = "사용자 정보를 불러오는 중...", style = MaterialTheme.typography.bodyLarge)
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    TerrariaCommunityAppTheme {
        LoginScreen(modifier = Modifier.fillMaxSize(), googleSignInClient = GoogleSignIn.getClient(LocalContext.current, GoogleSignInOptions.Builder().build()), firebaseAuth = Firebase.auth, onSignInSuccess = {})
    }
}

@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier.fillMaxWidth().wrapContentHeight(),
        factory = {
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = "ca-app-pub-9471537431449262/6534492831" // Your actual AdMob Banner ID
                adListener = object : com.google.android.gms.ads.AdListener() {
                    override fun onAdLoaded() {
                        Log.d(TAG, "AdBanner loaded successfully")
                    }

                    override fun onAdFailedToLoad(adError: com.google.android.gms.ads.LoadAdError) {
                        Log.e(TAG, "AdBanner failed to load: ${adError.message}")
                    }
                }
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(postRepository: PostRepository, onPostClick: (String) -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Post>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            coroutineScope.launch {
                searchResults = postRepository.searchPosts(searchQuery, null)
            }
        } else {
            searchResults = emptyList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("검색") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("검색어를 입력하세요") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            if (searchQuery.isBlank()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("검색어를 입력하여 글을 찾아보세요.")
                }
            } else if (searchResults.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("검색 결과가 없습니다.")
                }
            } else {
                LazyColumn {
                    items(searchResults) { post ->
                        PostItem(post = post, onPostClick = onPostClick)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteScreen(postRepository: PostRepository, onPostClick: (String) -> Unit) {
    val firebaseAuth = Firebase.auth
    val userId = firebaseAuth.currentUser?.uid
    val posts = remember { mutableStateListOf<Post>() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(userId) {
        if (userId != null) {
            coroutineScope.launch {
                posts.clear()
                posts.addAll(postRepository.getFavoritePostsByUserId(userId))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("즐겨찾기") })
        }
    ) { paddingValues ->
        if (posts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text("즐겨찾기한 글이 없습니다.")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                items(posts) { post ->
                    PostItem(post = post, onPostClick = onPostClick)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun MyPageScreen(
    onSettingsClick: () -> Unit,
    onMyPostsClick: () -> Unit,
    onFavoritePostsClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onEditProfileClick: () -> Unit,
    onNotificationSettingsClick: () -> Unit,
    onNotificationHistoryClick: () -> Unit,
    onAdminReportsClick: () -> Unit // 추가
) {
    val firebaseAuth = Firebase.auth
    val currentUser = firebaseAuth.currentUser
    val userRepository = remember { UserRepository() }
    val user = remember { mutableStateOf<User?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(currentUser?.uid) {
        currentUser?.uid?.let { uid ->
            coroutineScope.launch {
                user.value = userRepository.getUser(uid)
            }
        }
    }

    // 임시 관리자 판별 (예: 특정 UID)
    val isAdmin = currentUser?.email == "admin@example.com" // 실제 앱에서는 더 안전한 방식 필요

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        // 프로필 섹션
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 프로필 이미지
                Box(
                    modifier = Modifier.size(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (user.value?.profileImageUrl?.isNotEmpty() == true) {
                        AsyncImage(
                            model = user.value?.profileImageUrl,
                            contentDescription = "프로필 이미지",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "프로필 이미지",
                            modifier = Modifier.size(60.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 사용자 정보
                Text(
                    text = user.value?.displayName ?: currentUser?.displayName ?: "사용자",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                if (user.value?.email?.isNotEmpty() == true) {
                    Text(
                        text = user.value?.email ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (user.value?.bio?.isNotEmpty() == true) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = user.value?.bio ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 통계 정보
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = user.value?.points?.toString() ?: "0",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "포인트",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = user.value?.badges?.size?.toString() ?: "0",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "뱃지",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 프로필 편집 버튼
                OutlinedButton(
                    onClick = onEditProfileClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("프로필 편집")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 메뉴 버튼들
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                ListItem(
                    headlineContent = { Text("내 작성글") },
                    leadingContent = { Icon(Icons.Filled.Article, contentDescription = null) },
                    modifier = Modifier.clickable { onMyPostsClick() }
                )
                
                ListItem(
                    headlineContent = { Text("즐겨찾기") },
                    leadingContent = { Icon(Icons.Filled.Favorite, contentDescription = null) },
                    modifier = Modifier.clickable { onFavoritePostsClick() }
                )
                
                ListItem(
                    headlineContent = { Text("알림 설정") },
                    leadingContent = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                    modifier = Modifier.clickable { onNotificationSettingsClick() }
                )
                
                ListItem(
                    headlineContent = { Text("알림 히스토리") },
                    leadingContent = { Icon(Icons.Filled.History, contentDescription = null) },
                    modifier = Modifier.clickable { onNotificationHistoryClick() }
                )
                
                if (isAdmin) {
                    ListItem(
                        headlineContent = { Text("신고 내역(관리자)") },
                        leadingContent = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        modifier = Modifier.clickable { onAdminReportsClick() }
                    )
                }
                
                ListItem(
                    headlineContent = { Text("설정") },
                    leadingContent = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    modifier = Modifier.clickable { onSettingsClick() }
                )
                
                ListItem(
                    headlineContent = { Text("로그아웃") },
                    leadingContent = { Icon(Icons.Filled.Logout, contentDescription = null) },
                    modifier = Modifier.clickable { onLogoutClick() }
                )
            }
        }
    }
}

@Composable
fun BlockedUsersSection(
    currentUser: User?,
    userRepository: UserRepository,
    onUserUnblocked: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val blockedUsers = currentUser?.blockedUserIds ?: emptyList()
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("차단한 사용자 목록", style = MaterialTheme.typography.titleMedium)
        if (blockedUsers.isEmpty()) {
            Text("차단한 사용자가 없습니다.", style = MaterialTheme.typography.bodySmall)
        } else {
            blockedUsers.forEach { blockedUid ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = blockedUid, modifier = Modifier.weight(1f))
                    Button(onClick = {
                        coroutineScope.launch {
                            currentUser?.uid?.let { myUid ->
                                userRepository.removeBlockedUser(myUid, blockedUid)
                                onUserUnblocked()
                            }
                        }
                    }) {
                        Text("차단 해제")
                    }
                }
            }
        }
    }
}

@Composable
fun UserProfileSection(
    currentUser: User?,
    viewedUser: User?,
    userRepository: UserRepository,
    onBlocked: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val isBlocked = currentUser?.blockedUserIds?.contains(viewedUser?.uid) == true
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("프로필", style = MaterialTheme.typography.titleMedium)
        Text("닉네임: ${viewedUser?.displayName ?: "알 수 없음"}")
        Text("이메일: ${viewedUser?.email ?: "알 수 없음"}")
        // ... 기타 정보 ...
        if (viewedUser != null && currentUser != null && viewedUser.uid != currentUser.uid) {
            Button(onClick = {
                coroutineScope.launch {
                    if (!isBlocked) {
                        userRepository.addBlockedUser(currentUser.uid, viewedUser.uid)
                    } else {
                        userRepository.removeBlockedUser(currentUser.uid, viewedUser.uid)
                    }
                    onBlocked()
                }
            }) {
                Text(if (!isBlocked) "차단하기" else "차단 해제")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPostsScreen(postRepository: PostRepository, onBack: () -> Unit) {
    val firebaseAuth = Firebase.auth
    val userId = firebaseAuth.currentUser?.uid
    val posts = remember { mutableStateListOf<Post>() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(userId) {
        if (userId != null) {
            coroutineScope.launch {
                posts.clear()
                posts.addAll(postRepository.getPostsByAuthorId(userId))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("내 작성글") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로 가기")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (posts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text("작성한 글이 없습니다.")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                items(posts) { post ->
                    PostItem(post = post, onPostClick = { /* 상세보기 이동 등 필요시 구현 */ })
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritePostsScreen(postRepository: PostRepository, onBack: () -> Unit) {
    val firebaseAuth = Firebase.auth
    val userId = firebaseAuth.currentUser?.uid
    val posts = remember { mutableStateListOf<Post>() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(userId) {
        if (userId != null) {
            coroutineScope.launch {
                posts.clear()
                posts.addAll(postRepository.getFavoritePostsByUserId(userId))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("즐겨찾기 글 목록") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로 가기")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (posts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text("즐겨찾기한 글이 없습니다.")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                items(posts) { post ->
                    PostItem(post = post, onPostClick = { /* 상세보기 이동 등 필요시 구현 */ })
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    userRepository: UserRepository,
    onBack: () -> Unit
) {
    val firebaseAuth = Firebase.auth
    val currentUser = firebaseAuth.currentUser
    val user = remember { mutableStateOf<User?>(null) }
    val coroutineScope = rememberCoroutineScope()
    
    var displayName by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var profileImageUrl by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            coroutineScope.launch {
                isLoading = true
                try {
                    val uploadedUrl = userRepository.uploadProfileImage(currentUser?.uid ?: "", selectedUri)
                    uploadedUrl?.let { url ->
                        profileImageUrl = url
                        userRepository.updateProfile(currentUser?.uid ?: "", profileImageUrl = url)
                    }
                } catch (e: Exception) {
                    Log.e("EditProfileScreen", "Error uploading image: ${e.message}")
                } finally {
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(currentUser?.uid) {
        currentUser?.uid?.let { uid ->
            coroutineScope.launch {
                val userData = userRepository.getUser(uid)
                user.value = userData
                displayName = userData?.displayName ?: ""
                bio = userData?.bio ?: ""
                profileImageUrl = userData?.profileImageUrl ?: ""
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("프로필 편집") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로 가기")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                isLoading = true
                                try {
                                    userRepository.updateProfile(
                                        currentUser?.uid ?: "",
                                        displayName = displayName,
                                        bio = bio
                                    )
                                    onBack()
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        enabled = !isLoading
                    ) {
                        Text("저장")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // 프로필 이미지 섹션
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (profileImageUrl.isNotEmpty()) {
                            AsyncImage(
                                model = profileImageUrl,
                                contentDescription = "프로필 이미지",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = "프로필 이미지",
                                modifier = Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedButton(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("프로필 이미지 변경")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 프로필 정보 입력
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("닉네임") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text("자기소개") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 통계 정보 표시
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "활동 통계",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = user.value?.points?.toString() ?: "0",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "포인트",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = user.value?.badges?.size?.toString() ?: "0",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "뱃지",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    userRepository: UserRepository,
    onBack: () -> Unit
) {
    val firebaseAuth = Firebase.auth
    val currentUser = firebaseAuth.currentUser
    val user = remember { mutableStateOf<User?>(null) }
    val coroutineScope = rememberCoroutineScope()
    
    var newCommentNotification by remember { mutableStateOf(true) }
    var popularPostNotification by remember { mutableStateOf(true) }
    var mentionNotification by remember { mutableStateOf(true) }
    var marketingNotification by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(currentUser?.uid) {
        currentUser?.uid?.let { uid ->
            coroutineScope.launch {
                val userData = userRepository.getUser(uid)
                user.value = userData
                userData?.notificationSettings?.let { settings ->
                    newCommentNotification = settings.newComment
                    popularPostNotification = settings.popularPost
                    mentionNotification = settings.mention
                    marketingNotification = settings.marketing
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("알림 설정") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로 가기")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                isLoading = true
                                try {
                                    val settings = NotificationSettings(
                                        newComment = newCommentNotification,
                                        popularPost = popularPostNotification,
                                        mention = mentionNotification,
                                        marketing = marketingNotification
                                    )
                                    currentUser?.uid?.let { uid ->
                                        userRepository.updateNotificationSettings(uid, settings)
                                    }
                                    onBack()
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        enabled = !isLoading
                    ) {
                        Text("저장")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "알림 설정",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 새 댓글 알림
                    ListItem(
                        headlineContent = { Text("새 댓글 알림") },
                        supportingContent = { Text("내 게시글에 새 댓글이 달리면 알림을 받습니다") },
                        trailingContent = {
                            Switch(
                                checked = newCommentNotification,
                                onCheckedChange = { newCommentNotification = it }
                            )
                        }
                    )
                    
                    // 인기글 선정 알림
                    ListItem(
                        headlineContent = { Text("인기글 선정 알림") },
                        supportingContent = { Text("내 게시글이 인기글에 선정되면 알림을 받습니다") },
                        trailingContent = {
                            Switch(
                                checked = popularPostNotification,
                                onCheckedChange = { popularPostNotification = it }
                            )
                        }
                    )
                    
                    // 멘션 알림
                    ListItem(
                        headlineContent = { Text("멘션 알림") },
                        supportingContent = { Text("댓글에서 @로 언급되면 알림을 받습니다") },
                        trailingContent = {
                            Switch(
                                checked = mentionNotification,
                                onCheckedChange = { mentionNotification = it }
                            )
                        }
                    )
                    
                    // 마케팅 알림
                    ListItem(
                        headlineContent = { Text("마케팅 알림") },
                        supportingContent = { Text("이벤트, 업데이트 등의 마케팅 알림을 받습니다") },
                        trailingContent = {
                            Switch(
                                checked = marketingNotification,
                                onCheckedChange = { marketingNotification = it }
                            )
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 알림 히스토리 버튼
            OutlinedButton(
                onClick = { /* 알림 히스토리 화면으로 이동 */ },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.History, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("알림 히스토리")
            }
        }
    }
}