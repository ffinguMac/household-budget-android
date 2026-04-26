package com.householdbudget.app.ui.onboarding

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.householdbudget.app.R
import com.householdbudget.app.data.preferences.ProfileManager
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    profileManager: ProfileManager,
    webClientId: String,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showManualDialog by remember { mutableStateOf(false) }
    var manualName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isWorking by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Filled.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.onboarding_welcome),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.onboarding_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(40.dp))

            Button(
                onClick = {
                    if (webClientId.isBlank()) {
                        errorMessage = context.getString(R.string.onboarding_google_not_configured)
                        return@Button
                    }
                    isWorking = true
                    scope.launch {
                        val activity = context as? ComponentActivity
                        if (activity == null) {
                            errorMessage = context.getString(R.string.onboarding_signin_error, "no activity")
                            isWorking = false
                            return@launch
                        }
                        val name = signInWithGoogle(activity, webClientId)
                        if (name != null) {
                            profileManager.renameProfile(ProfileManager.DEFAULT_PROFILE_ID, name)
                            profileManager.setOnboardingCompleted(true)
                        } else {
                            errorMessage = context.getString(R.string.onboarding_signin_cancelled)
                        }
                        isWorking = false
                    }
                },
                enabled = !isWorking,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(stringResource(R.string.onboarding_google_signin))
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    manualName = ""
                    showManualDialog = true
                },
                enabled = !isWorking,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(stringResource(R.string.onboarding_manual_name))
            }
        }
    }

    if (showManualDialog) {
        AlertDialog(
            onDismissRequest = { showManualDialog = false },
            title = { Text(stringResource(R.string.onboarding_manual_name)) },
            text = {
                OutlinedTextField(
                    value = manualName,
                    onValueChange = { manualName = it },
                    label = { Text(stringResource(R.string.onboarding_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = manualName.trim()
                        if (name.isNotEmpty()) {
                            scope.launch {
                                profileManager.renameProfile(ProfileManager.DEFAULT_PROFILE_ID, name)
                                profileManager.setOnboardingCompleted(true)
                                showManualDialog = false
                            }
                        }
                    },
                    enabled = manualName.trim().isNotEmpty(),
                ) { Text(stringResource(R.string.onboarding_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { showManualDialog = false }) {
                    Text(stringResource(R.string.onboarding_cancel))
                }
            },
        )
    }

    errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text(stringResource(R.string.onboarding_signin_failed)) },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text(stringResource(R.string.onboarding_ok))
                }
            },
        )
    }
}

/**
 * Credential Manager + Google ID 토큰을 받아 displayName(또는 email)을 반환.
 * 취소/실패 시 null. webClientId가 비어 있으면 호출되지 않음 (UI에서 사전 체크).
 */
private suspend fun signInWithGoogle(activity: ComponentActivity, webClientId: String): String? {
    val credentialManager = CredentialManager.create(activity)
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(webClientId)
        .setAutoSelectEnabled(false)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()
    return try {
        val result = credentialManager.getCredential(activity, request)
        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val idTokenCred = GoogleIdTokenCredential.createFrom(credential.data)
            // displayName이 비어 있으면 email(로컬 부분)을 사용.
            idTokenCred.displayName?.takeIf { it.isNotBlank() }
                ?: idTokenCred.id.substringBefore('@')
        } else {
            null
        }
    } catch (e: GetCredentialException) {
        null
    } catch (e: Exception) {
        null
    }
}
