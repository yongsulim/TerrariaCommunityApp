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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
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

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Board : Screen("board", "게시판", Icons.Filled.Home)
    object Settings : Screen("settings", "설정", Icons.Filled.Settings)
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
            var darkTheme by remember { mutableStateOf(systemDarkTheme) }

            TerrariaCommunityAppTheme(darkTheme = darkTheme) { // Pass the darkTheme state
                val navController = rememberNavController()
                val coroutineScope = rememberCoroutineScope() // Compose scope
                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = currentBackStackEntry?.destination?.route
                var showBottomBar by remember { mutableStateOf(false) }

                Scaffold(
                    bottomBar = {
                        if (showBottomBar) { // Use the state variable here
                            Column {
                                NavigationBar {
                                    val items = listOf(Screen.Board, Screen.Settings)
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
                                onPostClick = { postId -> navController.navigate("post_detail/$postId") },
                                onAddPostClick = { navController.navigate("post_edit") },
                                onCategorySelected = { category, currentTabFromBoard ->
                                    _selectedCategory = category
                                    _currentBoardContentType = currentTabFromBoard
                                    refreshPosts(category, currentTabFromBoard, _searchQuery)
                                },
                                searchQuery = _searchQuery,
                                onSearchQueryChanged = { newQuery ->
                                    _searchQuery = newQuery
                                    refreshPosts(_selectedCategory, _currentBoardContentType, newQuery)
                                }
                            )
                        }
                        composable(Screen.Settings.route) { // 설정 화면 추가
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

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "테라리아 커뮤니티 앱", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(bottom = 32.dp))
        Button(onClick = {
            Log.d(TAG, "Google Login button clicked")
            googleLauncher.launch(googleSignInClient.signInIntent)
        }) {
            Text("Google 로그인")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
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
        }) {
            Text("게스트 로그인")
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