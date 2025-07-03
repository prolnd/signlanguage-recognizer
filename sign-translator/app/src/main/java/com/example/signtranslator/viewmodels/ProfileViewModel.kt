package com.example.signtranslator.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.signtranslator.utils.FirebaseTranslationManager
import com.example.signtranslator.utils.HistoryManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

/**
 * ViewModel for user profile management and cloud synchronization.
 * Handles Firebase authentication, account management, and cloud data sync.
 * Manages user statistics and synchronization status.
 */
class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()
    private val firestoreTranslationManager = FirebaseTranslationManager()
    private val localHistoryManager = HistoryManager(application)

    // LiveData for UI updates
    private val _authState = MutableLiveData<AuthState>()
    val authState: LiveData<AuthState> = _authState

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val _successMessage = MutableLiveData<String>()
    val successMessage: LiveData<String> = _successMessage

    private val _syncInfo = MutableLiveData<SyncInfo>()
    val syncInfo: LiveData<SyncInfo> = _syncInfo

    init {
        checkAuthState()
        updateSyncInfo()
    }

    /**
     * Check current authentication state
     */
    private fun checkAuthState() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            _authState.value = AuthState.SignedIn(currentUser.email ?: "Unknown")
        } else {
            _authState.value = AuthState.SignedOut
        }
    }

    /**
     * Sign in with email and password
     */
    suspend fun signIn(email: String, password: String) {
        _isLoading.value = true
        _errorMessage.value = ""

        try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user

            if (user != null) {
                _authState.value = AuthState.SignedIn(user.email ?: "Unknown")
                _successMessage.value = "Signed in successfully!"
                updateSyncInfo()

                // Auto-sync after sign in
                syncWithCloud()
            } else {
                _errorMessage.value = "Sign in failed: No user returned"
            }
        } catch (e: Exception) {
            _errorMessage.value = "Sign in failed: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Create new user account
     */
    suspend fun createAccount(email: String, password: String) {
        _isLoading.value = true
        _errorMessage.value = ""

        try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user

            if (user != null) {
                _authState.value = AuthState.SignedIn(user.email ?: "Unknown")
                _successMessage.value = "Account created successfully!"
                updateSyncInfo()

                // Auto-sync after account creation
                syncWithCloud()
            } else {
                _errorMessage.value = "Account creation failed: No user returned"
            }
        } catch (e: Exception) {
            _errorMessage.value = "Account creation failed: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Send password reset email
     */
    suspend fun sendPasswordReset(email: String) {
        _isLoading.value = true
        _errorMessage.value = ""

        try {
            auth.sendPasswordResetEmail(email).await()
            _successMessage.value = "Password reset email sent!"
        } catch (e: Exception) {
            _errorMessage.value = "Password reset failed: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Sign out current user
     */
    fun signOut() {
        try {
            auth.signOut()
            _authState.value = AuthState.SignedOut
            _successMessage.value = "Signed out successfully!"
            updateSyncInfo()
        } catch (e: Exception) {
            _errorMessage.value = "Sign out failed: ${e.message}"
        }
    }

    /**
     * Synchronize local data with cloud storage
     */
    suspend fun syncWithCloud() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            _errorMessage.value = "Must be signed in to sync with cloud"
            return
        }

        _isLoading.value = true
        _errorMessage.value = ""

        try {
            // Get local translations
            val localTranslations = localHistoryManager.getHistory()

            // Sync local translations to cloud
            val syncResult = firestoreTranslationManager.syncAllLocalTranslations(currentUser.uid, localTranslations)

            // Load cloud translations
            val cloudTranslations = firestoreTranslationManager.loadAllTranslationsFromCloud(currentUser.uid)

            // Smart merge - only add translations that don't exist locally
            if (cloudTranslations.isNotEmpty()) {
                val currentLocal = localHistoryManager.getHistory()
                val localIds = currentLocal.map { it.id }.toSet()

                // Find cloud translations that aren't already local
                val newTranslations = cloudTranslations.filter { cloudTranslation ->
                    !localIds.contains(cloudTranslation.id)
                }

                // Also check for potential duplicates by sentence and timestamp
                val filteredNewTranslations = newTranslations.filter { cloudTranslation ->
                    val isDuplicate = currentLocal.any { localTranslation ->
                        localTranslation.sentence == cloudTranslation.sentence &&
                                kotlin.math.abs(localTranslation.timestamp - cloudTranslation.timestamp) < 60000 // Within 1 minute
                    }
                    !isDuplicate
                }

                // Add only truly new translations to local storage
                var addedCount = 0
                filteredNewTranslations.forEach { translation ->
                    try {
                        val success = localHistoryManager.addTranslation(translation.sentence, translation.signEntries)
                        if (success) {
                            addedCount++
                        }
                    } catch (e: Exception) {
                        _errorMessage.value = "A transaction failed: ${e.message}"
                    }
                }

                _successMessage.value = "Sync completed: ${syncResult.successCount} uploaded, $addedCount downloaded"
            } else {
                _successMessage.value = "Sync completed: ${syncResult.successCount} uploaded, 0 downloaded"
            }

            updateSyncInfo()

        } catch (e: Exception) {
            _errorMessage.value = "Sync failed: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Update synchronization information and statistics
     */
    private fun updateSyncInfo() {
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser
                val localHistory = localHistoryManager.getHistory()

                // Calculate translation statistics
                val totalTranslations = localHistory.size
                val totalSigns = localHistory.sumOf { it.signEntries.size }

                val syncInfo = SyncInfo(
                    isCloudSynced = currentUser != null,
                    translations = totalTranslations,
                    maxLocalTranslations = 100,
                    translationSigns = totalSigns,
                    lastSyncTime = if (currentUser != null) {
                        SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date())
                    } else {
                        ""
                    }
                )

                _syncInfo.value = syncInfo

            } catch (e: Exception) {
                _errorMessage.value = "Update failed: ${e.message}"
            }
        }
    }

    /**
     * Sealed class representing authentication states
     */
    sealed class AuthState {
        object SignedOut : AuthState()
        data class SignedIn(val email: String) : AuthState()
    }

    /**
     * Data class containing synchronization information and user statistics
     */
    data class SyncInfo(
        val isCloudSynced: Boolean,
        val translations: Int,
        val maxLocalTranslations: Int,
        val translationSigns: Int,
        val lastSyncTime: String
    )
}