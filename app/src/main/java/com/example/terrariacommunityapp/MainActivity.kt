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
            TerrariaCommunityAppTheme {
                val navController = rememberNavController()
                val coroutineScope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    refreshPosts()
                }

                NavHost(navController = navController, startDestination = "login") {
                    composable("login") {
                        LoginScreen(
                            modifier = Modifier.fillMaxSize(),
                            googleSignInClient = googleSignInClient,
                            firebaseAuth = firebaseAuth,
                            onSignInSuccess = {
                                // 로그인 성공 시 게시판 화면으로 이동 및 게시물 새로고침
                                refreshPosts()
                                navController.navigate("board") {
                                    popUpTo("login") { inclusive = true }
                                }
                            },
                            onNaverSignInSuccess = { accessToken ->
                                // 네이버 로그인 성공 후 Firebase 연동
                                val provider = OAuthProvider.newBuilder("naver.com")
                                provider.addCustomParameter("access_token", accessToken)

                                firebaseAuth.startActivityForSignInWithProvider(this@MainActivity, provider.build())
                                    .addOnSuccessListener { authResult ->
                                        Log.d(TAG, "Firebase Naver sign-in successful")
                                        refreshPosts()
                                        navController.navigate("board") {
                                            popUpTo("login") { inclusive = true }
                                        }
                                    }
                                    .addOnFailureListener { exception ->
                                        Log.w(TAG, "Firebase Naver sign-in failed", exception)
                                    }
                            }
                        )
                    }
                    composable("board") {
                        BoardScreen(
                            posts = posts,
                            onPostClick = { postId -> navController.navigate("post_detail/$postId") },
                            onAddPostClick = { navController.navigate("post_edit") }
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
                                        postRepository.deletePost(it)
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

    private fun refreshPosts() {
        // Launch a coroutine to fetch posts asynchronously
        lifecycleScope.launch {
            posts.clear()
            posts.addAll(postRepository.getPosts())
        }
    }
}

@Composable
fun LoginScreen(modifier: Modifier = Modifier, googleSignInClient: GoogleSignInClient, firebaseAuth: FirebaseAuth, onSignInSuccess: () -> Unit, onNaverSignInSuccess: (String) -> Unit) {
    val googleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(it.data)
        try {
            val account = task.getResult(ApiException::class.java)!!
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener { authTask ->
                    if (authTask.isSuccessful) {
                        Log.d(TAG, "Firebase Google sign-in successful")
                        onSignInSuccess() // Call success callback
                    } else {
                        Log.w(TAG, "Firebase Google sign-in failed", authTask.exception)
                        // Sign in failed
                    }
                }
        } catch (e: ApiException) {
            Log.w(TAG, "Google sign in failed", e)
        }
    }

    val naverCallback = object : OAuthLoginCallback {
        override fun onSuccess() {
            val accessToken = NaverIdLoginSDK.getAccessToken()
            if (accessToken != null) {
                Log.d(TAG, "Naver Login success: $accessToken")
                onNaverSignInSuccess(accessToken)
            } else {
                Log.w(TAG, "Naver Login success but access token is null")
            }
        }

        override fun onFailure(httpStatus: Int, message: String) {
            val errorCode = NaverIdLoginSDK.getLastErrorCode().code
            val errorDescription = NaverIdLoginSDK.getLastErrorDescription()
            Log.e(TAG, "Naver Login failed: errorCode:$errorCode, errorDesc:$errorDescription, httpStatus:$httpStatus, message:$message")
        }

        override fun onError(errorCode: Int, message: String) {
            onFailure(errorCode, message) // Re-use onFailure for error cases
        }
    }

    val context = LocalContext.current // Get context within Composable

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "테라리아 커뮤니티 앱", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(bottom = 32.dp))
        Button(onClick = { googleLauncher.launch(googleSignInClient.signInIntent) }) {
            Text("Google 로그인")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            NaverIdLoginSDK.authenticate(context, naverCallback)
        }) {
            Text("Naver 로그인")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
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

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    // ... existing code ...
}