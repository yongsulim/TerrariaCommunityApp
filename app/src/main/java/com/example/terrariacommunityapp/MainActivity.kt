package com.example.terrariacommunityapp

import android.content.ContentValues.TAG
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.terrariacommunityapp.ui.theme.TerrariaCommunityAppTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.common.api.ApiException
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.google.firebase.messaging.FirebaseMessaging
import com.navercorp.nid.NaverIdLoginSDK
import com.navercorp.nid.oauth.OAuthLoginCallback
import com.google.firebase.auth.OAuthProvider
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavController
import androidx.compose.runtime.getValue
import androidx.compose.material3.Icon
import com.example.terrariacommunityapp.BoardScreen
import com.example.terrariacommunityapp.PostDetailScreen
import com.example.terrariacommunityapp.PostEditScreen
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ListItem

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Board : Screen("board", "게시판", Icons.Filled.Home)
    object Settings : Screen("settings", "설정", Icons.Filled.Settings)
}

class MainActivity : ComponentActivity() {
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var firebaseAuth: FirebaseAuth
    private val postRepository = PostRepository()
    private val posts = mutableStateListOf<Post>()

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
        }

        enableEdgeToEdge()
        setContent {
            // Use a mutable state to control the theme
            val systemDarkTheme = isSystemInDarkTheme()
            var darkTheme by remember { mutableStateOf(systemDarkTheme) }

            TerrariaCommunityAppTheme(darkTheme = darkTheme) { // Pass the darkTheme state
                val navController = rememberNavController()
                val coroutineScope = rememberCoroutineScope()
                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = currentBackStackEntry?.destination?.route
                var showBottomBar by remember { mutableStateOf(false) }

                Scaffold(
                    bottomBar = {
                        if (showBottomBar) { // Use the state variable here
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
                                refreshPosts()
                                showBottomBar = true // Show after data is loaded
                            }
                            BoardScreen(
                                posts = posts,
                                onPostClick = { postId -> navController.navigate("post_detail/$postId") },
                                onAddPostClick = { navController.navigate("post_edit") }
                            )
                        }
                        composable(Screen.Settings.route) { // 설정 화면 추가
                            LaunchedEffect(Unit) { showBottomBar = true }
                            SettingsScreen(
                                modifier = Modifier.fillMaxSize(),
                                currentDarkTheme = darkTheme, // Pass current theme state
                                onToggleTheme = { darkTheme = it } // Pass callback to toggle theme
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
                                        coroutineScope.launch {
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

    private fun refreshPosts() {
        lifecycleScope.launch {
            posts.clear()
            posts.addAll(postRepository.getPosts())
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
fun SettingsScreen(modifier: Modifier = Modifier, currentDarkTheme: Boolean, onToggleTheme: (Boolean) -> Unit) {
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
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    TerrariaCommunityAppTheme {
        LoginScreen(modifier = Modifier.fillMaxSize(), googleSignInClient = GoogleSignIn.getClient(LocalContext.current, GoogleSignInOptions.Builder().build()), firebaseAuth = Firebase.auth, onSignInSuccess = {})
    }
}