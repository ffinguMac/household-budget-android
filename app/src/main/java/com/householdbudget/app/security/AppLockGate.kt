package com.householdbudget.app.security

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.householdbudget.app.R
import com.householdbudget.app.data.preferences.UserPreferencesRepository

/**
 * 화면 잠금 게이트. [UserPreferencesRepository.appLockEnabled] 가 켜져 있으면
 * 생체 인증(또는 기기 잠금 자격 증명) 성공 전까지 잠금 화면을 보여준다.
 * 기기가 인증을 지원하지 않으면 잠그지 않고 바로 [content] 를 렌더한다.
 */
@Composable
fun AppLockGate(
    preferences: UserPreferencesRepository,
    content: @Composable () -> Unit,
) {
    val lockEnabled by preferences.appLockEnabled.collectAsState(initial = false)
    var unlocked by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val authenticators =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val canAuthenticate =
        remember(context) {
            BiometricManager.from(context).canAuthenticate(authenticators) ==
                BiometricManager.BIOMETRIC_SUCCESS
        }

    if (!lockEnabled || unlocked || !canAuthenticate) {
        content()
        return
    }

    val promptTitle = stringResource(R.string.app_lock_prompt_title)
    val launchPrompt = launchPrompt@{
        // FragmentActivity 를 못 찾으면 프롬프트 없이 버튼 fallback 으로 해제 (크래시 금지).
        val fragmentActivity = activity ?: run {
            unlocked = true
            return@launchPrompt
        }
        val prompt =
            BiometricPrompt(
                fragmentActivity,
                ContextCompat.getMainExecutor(fragmentActivity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult,
                    ) {
                        unlocked = true
                    }
                },
            )
        val promptInfo =
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(promptTitle)
                .setAllowedAuthenticators(authenticators)
                .build()
        prompt.authenticate(promptInfo)
    }

    LaunchedEffect(Unit) {
        if (activity != null) {
            launchPrompt()
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = stringResource(R.string.app_lock_icon_description),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = launchPrompt,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(text = stringResource(R.string.app_lock_unlock_button))
            }
        }
    }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? =
    when (this) {
        is FragmentActivity -> this
        is ContextWrapper -> baseContext.findFragmentActivity()
        else -> null
    }
