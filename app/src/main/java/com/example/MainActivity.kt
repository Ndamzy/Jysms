package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.AppPreferencesRepository
import com.example.data.local.UserPreferences
import com.example.data.repository.AuthRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.SplashBrandScreen
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOutUser
import com.example.ui.navigation.MainSessionShell
import com.example.ui.theme.JySmsTheme
import com.example.ui.viewmodel.MainSessionViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val prefsRepo = remember(context) { AppPreferencesRepository(context) }
            val prefs by prefsRepo.preferencesFlow.collectAsStateWithLifecycle(initialValue = UserPreferences())
            val darkTheme = prefs.darkModeOverride ?: isSystemInDarkTheme()

            JySmsTheme(darkTheme = darkTheme) {
                val scope = rememberCoroutineScope()
                val credentialManager = remember(context) { CredentialManager.create(context) }
                val auth = remember { Firebase.auth }
                val db = remember(context) {
                    FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
                }
                val authRepo = remember(db) { AuthRepository(db) }
                val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
                var checkingSession by remember { mutableStateOf(auth.currentUser == null) }

                LaunchedEffect(Unit) {
                    if (auth.currentUser == null) {
                        attemptAutoSignIn(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = { checkingSession = false },
                            onUnauthenticated = { checkingSession = false },
                            scope = scope
                        )
                    } else {
                        checkingSession = false
                    }
                }

                when {
                    checkingSession -> SplashBrandScreen()
                    currentUser == null -> AuthScreen(
                        onAuthSuccess = {
                            Firebase.auth.currentUser?.let { signed ->
                                scope.launch { authRepo.syncSignedInUser(signed) }
                            }
                        }
                    )
                    else -> {
                        val user = currentUser!!
                        key(user.uid) {
                            val sessionVm: MainSessionViewModel = viewModel(
                                key = "session_${user.uid}",
                                factory = MainSessionViewModel.factory(user.uid)
                            )
                            MainSessionShell(
                                viewModel = sessionVm,
                                onSignOut = {
                                    scope.launch {
                                        authRepo.setOnlineStatus(user.uid, isOnline = false)
                                    }
                                    signOutUser(
                                        credentialManager = credentialManager,
                                        scope = scope
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
