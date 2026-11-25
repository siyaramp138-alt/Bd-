package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.auth.AccessPendingScreen
import com.example.auth.AuthScreen
import com.example.auth.authStateFlow
import com.example.auth.signOutGoogle
import com.example.data.AccessResolution
import com.example.data.BdEntRepository
import com.example.ui.BdEntWorkspace
import com.example.ui.theme.BdEntTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BdEntTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val authFlow = remember(auth) { auth.authStateFlow() }
    val currentUser by authFlow.collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val signedInUser = currentUser
    if (signedInUser == null) {
        AuthScreen(onAuthSuccess = { /* authStateFlow updates automatically */ })
    } else {
        key(signedInUser.uid) {
            AuthorizedAccessGate(
                user = signedInUser,
                auth = auth
            )
        }
    }
}

@Composable
private fun AuthorizedAccessGate(
    user: FirebaseUser,
    auth: FirebaseAuth
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(user.uid) { BdEntRepository(context.applicationContext) }

    var refreshTick by remember { mutableIntStateOf(0) }
    var accessState by remember { mutableStateOf<AccessResolution>(AccessResolution.Loading) }
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(user.uid, refreshTick) {
        if (refreshTick > 0) isRefreshing = true else accessState = AccessResolution.Loading
        accessState = repository.resolveCurrentUserAccess()
        isRefreshing = false
    }

    when (val resolution = accessState) {
        AccessResolution.Loading -> {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(38.dp))
                    Text(
                        text = "Verifying BD Enterprises access...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                }
            }
        }

        is AccessResolution.Unauthorized -> {
            AccessPendingScreen(
                email = resolution.email.ifBlank { user.email.orEmpty() },
                reason = resolution.reason,
                isRefreshing = isRefreshing,
                onRetry = { refreshTick++ },
                onSignOut = {
                    scope.launch { signOutGoogle(context, auth) }
                }
            )
        }

        is AccessResolution.Error -> {
            AccessPendingScreen(
                email = user.email.orEmpty(),
                reason = "Could not verify access right now: ${resolution.message}",
                isRefreshing = isRefreshing,
                onRetry = { refreshTick++ },
                onSignOut = {
                    scope.launch { signOutGoogle(context, auth) }
                }
            )
        }

        is AccessResolution.Authorized -> {
            BdEntWorkspace(
                user = user,
                access = resolution,
                onSignOut = {
                    scope.launch { signOutGoogle(context, auth) }
                }
            )
        }
    }
}
