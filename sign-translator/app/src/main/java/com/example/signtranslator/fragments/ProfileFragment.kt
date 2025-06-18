package com.example.signtranslator.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.signtranslator.R
import com.example.signtranslator.viewmodels.ProfileViewModel
import kotlinx.coroutines.launch

/**
 * Fragment for user profile management and cloud synchronization.
 * Handles authentication, displays usage statistics, and manages cloud sync.
 * Shows different views for signed-in and signed-out states.
 */
class ProfileFragment : Fragment() {

    private lateinit var viewModel: ProfileViewModel

    // Authentication views (signed out state)
    private lateinit var signedOutContainer: LinearLayout
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var signInButton: Button
    private lateinit var createAccountButton: Button
    private lateinit var forgotPasswordButton: Button

    // Profile views (signed in state)
    private lateinit var signedInContainer: LinearLayout
    private lateinit var userEmailText: TextView
    private lateinit var syncStatusText: TextView

    // Usage statistics views
    private lateinit var trainingSessionsText: TextView
    private lateinit var trainingLettersText: TextView
    private lateinit var translationsText: TextView
    private lateinit var translationSignsText: TextView

    // Sync management views
    private lateinit var lastSyncText: TextView
    private lateinit var syncNowButton: Button
    private lateinit var signOutButton: Button

    // Status views
    private lateinit var loadingProgress: ProgressBar
    private lateinit var errorText: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        initViewModel()
        setupObservers()
        setupClickListeners()
    }

    /**
     * Initialize all view references
     */
    private fun initViews(view: View) {
        // Authentication views
        signedOutContainer = view.findViewById(R.id.signed_out_container)
        emailEditText = view.findViewById(R.id.email_edit_text)
        passwordEditText = view.findViewById(R.id.password_edit_text)
        signInButton = view.findViewById(R.id.sign_in_button)
        createAccountButton = view.findViewById(R.id.create_account_button)
        forgotPasswordButton = view.findViewById(R.id.forgot_password_button)

        // Profile views
        signedInContainer = view.findViewById(R.id.signed_in_container)
        userEmailText = view.findViewById(R.id.user_email_text)
        syncStatusText = view.findViewById(R.id.sync_status_text)

        // Statistics views
        translationsText = view.findViewById(R.id.translations_text)
        translationSignsText = view.findViewById(R.id.translation_signs_text)

        // Sync views
        lastSyncText = view.findViewById(R.id.last_sync_text)
        syncNowButton = view.findViewById(R.id.sync_now_button)
        signOutButton = view.findViewById(R.id.sign_out_button)

        // Status views
        loadingProgress = view.findViewById(R.id.loading_progress)
        errorText = view.findViewById(R.id.error_text)
    }

    /**
     * Initialize ViewModel
     */
    private fun initViewModel() {
        viewModel = ViewModelProvider(this)[ProfileViewModel::class.java]
    }

    /**
     * Observe ViewModel changes and update UI
     */
    private fun setupObservers() {
        viewModel.authState.observe(viewLifecycleOwner) { state ->
            updateUIForAuthState(state)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            loadingProgress.visibility = if (isLoading) View.VISIBLE else View.GONE

            // Disable buttons during loading operations
            signInButton.isEnabled = !isLoading
            createAccountButton.isEnabled = !isLoading
            syncNowButton.isEnabled = !isLoading
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (message.isNotEmpty()) {
                showError(message)
            }
        }

        viewModel.successMessage.observe(viewLifecycleOwner) { message ->
            if (message.isNotEmpty()) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.syncInfo.observe(viewLifecycleOwner) { syncInfo ->
            updateSyncInfo(syncInfo)
        }
    }

    /**
     * Configure click listeners for interactive elements
     */
    private fun setupClickListeners() {
        signInButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString()

            if (validateSignInInput(email, password)) {
                lifecycleScope.launch {
                    viewModel.signIn(email, password)
                }
            }
        }

        createAccountButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString()

            if (validateCreateAccountInput(email, password)) {
                lifecycleScope.launch {
                    viewModel.createAccount(email, password)
                }
            }
        }

        forgotPasswordButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()

            if (email.isNotEmpty()) {
                lifecycleScope.launch {
                    viewModel.sendPasswordReset(email)
                }
            } else {
                Toast.makeText(context, "Please enter your email address", Toast.LENGTH_SHORT).show()
            }
        }

        syncNowButton.setOnClickListener {
            lifecycleScope.launch {
                viewModel.syncWithCloud()
            }
        }

        signOutButton.setOnClickListener {
            lifecycleScope.launch {
                viewModel.signOut()
            }
        }
    }

    /**
     * Update UI based on authentication state
     */
    private fun updateUIForAuthState(state: ProfileViewModel.AuthState) {
        when (state) {
            is ProfileViewModel.AuthState.SignedOut -> {
                signedOutContainer.visibility = View.VISIBLE
                signedInContainer.visibility = View.GONE
                clearInputFields()
            }

            is ProfileViewModel.AuthState.SignedIn -> {
                signedOutContainer.visibility = View.GONE
                signedInContainer.visibility = View.VISIBLE
                userEmailText.text = "Signed in as: ${state.email}"
            }
        }
    }

    /**
     * Update sync information and usage statistics
     */
    private fun updateSyncInfo(syncInfo: ProfileViewModel.SyncInfo) {
        // Cloud sync status
        syncStatusText.text = if (syncInfo.isCloudSynced) {
            "☁️ Cloud sync enabled"
        } else {
            "📱 Local only mode"
        }

        // Translation statistics
        translationsText.text = "Translations: ${syncInfo.translations} (max: ${syncInfo.maxLocalTranslations})"
        translationSignsText.text = "Signs captured: ${syncInfo.translationSigns}"

        // Last sync time
        lastSyncText.text = if (syncInfo.lastSyncTime.isNotEmpty()) {
            "Last sync: ${syncInfo.lastSyncTime}"
        } else {
            "Never synced"
        }
    }

    /**
     * Validate sign in input
     */
    private fun validateSignInInput(email: String, password: String): Boolean {
        return if (email.isNotEmpty() && password.isNotEmpty()) {
            true
        } else {
            Toast.makeText(context, "Please enter email and password", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Validate create account input
     */
    private fun validateCreateAccountInput(email: String, password: String): Boolean {
        return if (email.isNotEmpty() && password.length >= 6) {
            true
        } else {
            Toast.makeText(context, "Email required and password must be 6+ characters", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Show error message with auto-hide
     */
    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
        // Hide error after 5 seconds
        errorText.postDelayed({
            errorText.visibility = View.GONE
        }, 5000)
    }

    /**
     * Clear input fields
     */
    private fun clearInputFields() {
        emailEditText.text.clear()
        passwordEditText.text.clear()
    }
}