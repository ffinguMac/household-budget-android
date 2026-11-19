package com.householdbudget.app.security

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.SystemClock
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LifecycleStartEffect
import com.householdbudget.app.R
import com.householdbudget.app.data.preferences.UserPreferencesRepository

/** 이 시간 이상 백그라운드에 있다 돌아오면 다시 잠근다. 공유 시트·은행 앱을 잠깐 다녀오는 건 잠그지 않는다. */
internal const val RELOCK_AFTER_BACKGROUND_MILLIS = 60_000L

internal fun shouldRelockAfterBackground(backgroundMillis: Long): Boolean =
    backgroundMillis >= RELOCK_AFTER_BACKGROUND_MILLIS

internal enum class LockGateState { LOADING, LOCKED, OPEN }

/** lockEnabled == null 은 설정을 아직 못 읽은 상태. */
internal fun lockGateState(lockEnabled: Boolean?, unlocked: Boolean, canAuthenticate: Boolean): LockGateState =
    when {
        lockEnabled == null -> LockGateState.LOADING
        lockEnabled && !unlocked && canAuthenticate -> LockGateState.LOCKED
        else -> LockGateState.OPEN
    }

/**
 * 화면 잠금 게이트. [UserPreferencesRepository.appLockEnabled] 가 켜져 있으면
 * 생체 인증(또는 기기 잠금 자격 증명) 성공 전까지 잠금 화면을 보여준다.
 * 기기가 인증을 지원하지 않으면 잠그지 않고 바로 [content] 를 렌더한다.
 * 백그라운드에 [RELOCK_AFTER_BACKGROUND_MILLIS] 이상 있다 돌아오면 다시 잠근다. 잠긴 동안 [content] 는
 * 컴포지션에서 빠지고(별도 창인 다이얼로그·메뉴도 닫힘), 저장 가능한 상태·내비게이션·ViewModel 은 유지된다.
 */
@Composable
fun AppLockGate(
    preferences: UserPreferencesRepository,
    content: @Composable () -> Unit,
) {
    val lockEnabled by preferences.appLockEnabled.collectAsState(initial = null)
    var unlocked by remember { mutableStateOf(false) }
    // 다시 잠글 때마다 올려서, 이미 잠긴 채 오래 나갔다 와도 프롬프트를 다시 띄운다.
    var relockCount by remember { mutableIntStateOf(0) }
    var stoppedAtMillis by remember { mutableStateOf<Long?>(null) }
    val focusManager = LocalFocusManager.current

    LifecycleStartEffect(Unit) {
        val stoppedAt = stoppedAtMillis
        if (lockEnabled == true && stoppedAt != null && shouldRelockAfterBackground(SystemClock.elapsedRealtime() - stoppedAt)) {
            unlocked = false
            relockCount++
            focusManager.clearFocus(force = true) // 키보드가 잠금 화면 위에 남지 않게
        }
        stoppedAtMillis = null
        onStopOrDispose { stoppedAtMillis = SystemClock.elapsedRealtime() }
    }

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

    // 잠금 중 최근 앱 화면에 내용이 비치지 않게 한다. 스크린샷은 막지 않으려고 FLAG_SECURE 는 쓰지 않는다.
    LaunchedEffect(activity, lockEnabled) {
        val enabled = lockEnabled ?: return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity?.setRecentsScreenshotEnabled(!enabled)
        }
    }

    val state = lockGateState(lockEnabled, unlocked, canAuthenticate)
    val background = MaterialTheme.colorScheme.background
    if (state == LockGateState.LOADING) {
        Box(Modifier.fillMaxSize().background(background))
        return
    }

    val promptTitle = stringResource(R.string.app_lock_prompt_title)
    val onUnlocked = { unlocked = true }
    val launchPrompt = launchPrompt@{
        // FragmentActivity 를 못 찾으면 프롬프트 없이 버튼 fallback 으로 해제 (크래시 금지).
        val fragmentActivity = activity ?: run {
            onUnlocked()
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
                        onUnlocked()
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

    // 첫 실행·복귀 재잠금·설정에서 잠금을 켠 직후. 모두 화면이 보이는(STARTED) 때에만 바뀐다.
    // 취소하면 버튼으로 다시 띄운다(기기 PIN 화면에서 돌아올 때 자동으로 다시 띄우지 않는다).
    LaunchedEffect(state, relockCount) {
        if (state == LockGateState.LOCKED && activity != null) {
            launchPrompt()
        }
    }

    // 잠금 해제 사이에 content 의 rememberSaveable 상태(내비게이션 위치 포함)를 보관한다.
    val contentState = rememberSaveableStateHolder()
    if (state == LockGateState.OPEN) {
        contentState.SaveableStateProvider("content") { content() }
        return
    }
    Box(
        modifier = Modifier.fillMaxSize().background(background),
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
