package com.example.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class PosUserState(
    val isLoggedIn: Boolean = false,
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String = ""
)

/**
 * Manages Google Sign-In with standard Android Credential Manager and Firebase Authentication.
 * Ensures:
 * 1. Native system account picker modal (no manual typing of email or password).
 * 2. Authenticates with Firebase Auth via GoogleAuthProvider to produce a real Firebase Auth UID.
 * 3. Consistent UID mapping across devices, reinstalls, and sessions for full cloud sync/restore.
 * 4. Satisfies strict Firestore security rules: request.auth != null && request.auth.uid == userId.
 */
class AuthManager(private val context: Context) {

    val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val prefs: SharedPreferences = context.getSharedPreferences("pos_auth_prefs", Context.MODE_PRIVATE)

    private val _userState = MutableStateFlow(loadPersistedUser())
    val userState: StateFlow<PosUserState> = _userState.asStateFlow()

    private val webClientId: String
        get() = try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            "13219776141-sv64d877p2klsfk9nin57iqo9ia9dp9c.apps.googleusercontent.com"
        }

    init {
        // Purge any legacy fake 'pos_uid_' or anonymous guest session from SharedPreferences
        val legacyUid = prefs.getString("uid", "") ?: ""
        val currentUser = firebaseAuth.currentUser
        if (legacyUid.startsWith("pos_uid_") || currentUser == null || currentUser.isAnonymous) {
            prefs.edit().clear().apply()
            if (currentUser?.isAnonymous == true) {
                try {
                    firebaseAuth.signOut()
                } catch (e: Exception) {
                    Log.e("AuthManager", "Error signing out anonymous user", e)
                }
            }
        }

        // Initialize state directly from FirebaseAuth currentUser
        syncWithFirebaseAuth()

        // Keep state synchronized with Firebase Auth at all times
        firebaseAuth.addAuthStateListener {
            syncWithFirebaseAuth()
        }
    }

    private fun syncWithFirebaseAuth() {
        val fbUser = firebaseAuth.currentUser
        val previousUid = prefs.getString("active_account_uid", "") ?: ""
        // Require a genuine non-anonymous user with a valid email
        if (fbUser != null && fbUser.uid.isNotBlank() && !fbUser.uid.startsWith("pos_uid_") && !fbUser.isAnonymous && !fbUser.email.isNullOrBlank()) {
            if (previousUid.isNotBlank() && previousUid != fbUser.uid) {
                // Different user detected! Clear Room database immediately
                try {
                    com.example.data.local.database.CafePosDatabase.getDatabase(context).clearAllTables()
                } catch (e: Exception) {
                    Log.e("AuthManager", "Error clearing tables on user switch", e)
                }
            }
            prefs.edit().putString("active_account_uid", fbUser.uid).apply()
            val state = PosUserState(
                isLoggedIn = true,
                uid = fbUser.uid, // Pure Firebase UID directly from FirebaseAuth.currentUser.uid
                email = fbUser.email ?: "",
                displayName = fbUser.displayName?.ifBlank { fbUser.email?.substringBefore('@') ?: "Cafe Owner" } ?: "Cafe Owner",
                photoUrl = fbUser.photoUrl?.toString() ?: ""
            )
            persistUser(state)
            _userState.value = state
        } else {
            prefs.edit().clear().apply()
            _userState.value = PosUserState(isLoggedIn = false, uid = "")
        }
    }

    val currentFirebaseUid: String
        get() = firebaseAuth.currentUser?.uid ?: ""

    private fun persistUser(user: PosUserState) {
        if (user.uid.startsWith("pos_uid_") || user.uid.isBlank()) {
            prefs.edit().clear().apply()
            return
        }
        prefs.edit()
            .putBoolean("is_logged_in", user.isLoggedIn)
            .putString("uid", user.uid)
            .putString("email", user.email)
            .putString("display_name", user.displayName)
            .putString("photo_url", user.photoUrl)
            .apply()
    }

    private fun loadPersistedUser(): PosUserState {
        val fbUser = firebaseAuth.currentUser
        if (fbUser != null && fbUser.uid.isNotBlank() && !fbUser.uid.startsWith("pos_uid_") && !fbUser.isAnonymous && !fbUser.email.isNullOrBlank()) {
            return PosUserState(
                isLoggedIn = true,
                uid = fbUser.uid,
                email = fbUser.email ?: "",
                displayName = fbUser.displayName?.ifBlank { fbUser.email?.substringBefore('@') ?: "Cafe Owner" } ?: "Cafe Owner",
                photoUrl = fbUser.photoUrl?.toString() ?: ""
            )
        }
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)
        val uid = prefs.getString("uid", "") ?: ""
        val email = prefs.getString("email", "") ?: ""
        // Reject any legacy fake pos_uid_, anonymous sessions, or whenever Firebase user is null
        if (!isLoggedIn || uid.isBlank() || uid.startsWith("pos_uid_") || email.isBlank() || fbUser == null || fbUser.isAnonymous) {
            prefs.edit().clear().apply()
            return PosUserState(isLoggedIn = false, uid = "")
        }
        val name = prefs.getString("display_name", "Cafe Owner") ?: "Cafe Owner"
        val photo = prefs.getString("photo_url", "") ?: ""
        return PosUserState(
            isLoggedIn = true,
            uid = uid,
            email = email,
            displayName = name,
            photoUrl = photo
        )
    }

    /**
     * Finds the nearest Activity from a Context.
     */
    private fun Context.findActivity(): Activity? {
        var current = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    /**
     * Launches the native Android Credential Manager Google Account Picker.
     * Tapping an account extracts the Google ID token and signs in with real Firebase Auth.
     */
    suspend fun signInWithGoogleNative(callingContext: Context): Result<PosUserState> = withContext(Dispatchers.Main) {
        val activity = callingContext.findActivity()
            ?: return@withContext Result.failure(IllegalStateException("Cannot launch account picker: Activity context required"))

        try {
            val credentialManager = CredentialManager.create(activity)

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false) // Show all accounts on device
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false) // Present picker explicitly
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                context = activity,
                request = request
            )

            val credential = result.credential
            val idToken = when {
                credential is CustomCredential &&
                        (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ||
                         credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL) -> {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    googleIdTokenCredential.idToken
                }
                else -> {
                    return@withContext Result.failure(IllegalStateException("Unsupported credential type: ${credential.type}"))
                }
            }

            if (idToken.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Received empty ID token from Google"))
            }

            // Authenticate with Firebase Authentication
            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = withContext(Dispatchers.IO) {
                firebaseAuth.signInWithCredential(firebaseCredential).await()
            }
            val fbUser = authResult.user
                ?: return@withContext Result.failure(IllegalStateException("Firebase Auth user is null"))

            val state = PosUserState(
                isLoggedIn = true,
                uid = fbUser.uid,
                email = fbUser.email ?: "",
                displayName = fbUser.displayName?.ifBlank { fbUser.email?.substringBefore("@") ?: "Merchant" } ?: (fbUser.email?.substringBefore("@") ?: "Merchant"),
                photoUrl = fbUser.photoUrl?.toString() ?: ""
            )
            persistUser(state)
            _userState.value = state
            Log.d("AuthManager", "Google Sign-In successful. Real Firebase UID: ${fbUser.uid}, Email: ${fbUser.email}")
            Result.success(state)
        } catch (e: GetCredentialCancellationException) {
            Log.d("AuthManager", "User dismissed or cancelled Google account picker")
            Result.failure(e)
        } catch (e: NoCredentialException) {
            Log.w("AuthManager", "No Google credentials available on device", e)
            Result.failure(Exception("No Google Account found on this device. Please add a Google Account in your device Settings to sign in."))
        } catch (e: Exception) {
            Log.e("AuthManager", "Google Sign-In with Credential Manager failed", e)
            val msg = e.localizedMessage ?: e.message ?: ""
            val formattedMsg = when {
                msg.contains("NoCredentialException", ignoreCase = true) || msg.contains("No credentials available", ignoreCase = true) -> {
                    "No Google Account found on this device. Please add a Google Account in your device Settings to sign in."
                }
                msg.contains("28444") || msg.contains("Developer console", ignoreCase = true) || msg.contains("10:") -> {
                    "Google Play Services configuration error. Please ensure your Google Play Services are up to date and your Google Account is active."
                }
                else -> msg
            }
            Result.failure(Exception(formattedMsg))
        }
    }

    /**
     * Guest Sign-In using Firebase Anonymous Authentication.
     * Generates a real Firebase Auth UID session instantly without blocking on Google OAuth.
     */
    suspend fun signInAsGuest(): Result<PosUserState> = withContext(Dispatchers.Main) {
        try {
            val authResult = withContext(Dispatchers.IO) {
                firebaseAuth.signInAnonymously().await()
            }
            val fbUser = authResult.user
                ?: return@withContext Result.failure(IllegalStateException("Firebase Auth user is null"))

            val state = PosUserState(
                isLoggedIn = true,
                uid = fbUser.uid,
                email = "guest@cafepos.local",
                displayName = "Cafe Guest / Operator",
                photoUrl = ""
            )
            persistUser(state)
            _userState.value = state
            Log.d("AuthManager", "Guest Sign-In successful. Real Firebase Anonymous UID: ${fbUser.uid}")
            Result.success(state)
        } catch (e: Exception) {
            Log.e("AuthManager", "Guest Sign-In failed", e)
            Result.failure(e)
        }
    }

    /**
     * Signs out of Firebase Auth, clears Credential Manager state, and resets session.
     */
    suspend fun signOut() = withContext(Dispatchers.IO) {
        try {
            firebaseAuth.signOut()
        } catch (e: Exception) {
            Log.e("AuthManager", "Error signing out of Firebase", e)
        }
        try {
            val credentialManager = CredentialManager.create(context)
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.e("AuthManager", "Error clearing credential state", e)
        }
        prefs.edit().clear().apply()
        _userState.value = PosUserState(isLoggedIn = false)
    }
}

