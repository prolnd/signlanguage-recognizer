package com.example.signtranslator.utils

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

/**
 * Manages Firebase authentication state and provides user information.
 * Handles sign-in/sign-out operations and user session management.
 */
class FirebaseAuthManager(private val context: Context) {

    private val auth = FirebaseAuth.getInstance()

    /**
     * Get the currently authenticated user
     */
    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    /**
     * Check if a user is currently signed in
     */
    fun isSignedIn(): Boolean = getCurrentUser() != null

    /**
     * Get the current user's unique ID
     */
    fun getUserId(): String? = getCurrentUser()?.uid

    /**
     * Add listener for authentication state changes
     * @param listener Callback that receives sign-in status updates
     */
    fun addAuthStateListener(listener: (Boolean) -> Unit) {
        auth.addAuthStateListener { firebaseAuth ->
            val isSignedIn = firebaseAuth.currentUser != null
            listener(isSignedIn)
        }
    }

    /**
     * Sealed class representing authentication operation results
     */
    sealed class AuthResult {
        data class Success(val user: FirebaseUser) : AuthResult()
        data class Error(val message: String) : AuthResult()
    }
}