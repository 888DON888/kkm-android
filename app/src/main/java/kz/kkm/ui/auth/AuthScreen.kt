package kz.kkm.ui.auth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import kz.kkm.ui.theme.KkmBlue

@Composable
fun AuthScreen(
    onAuthenticated: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state.isAuthenticated) {
        if (state.isAuthenticated) onAuthenticated()
    }

    LaunchedEffect(Unit) {
        viewModel.tryBiometric { promptBiometric(context as FragmentActivity, onAuthenticated) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KkmBlue),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // Logo / Title
            Text(
                text = "ККМ",
                fontSize = 56.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            Text(
                text = "Онлайн касса",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = 48.dp)
            )

            // PIN display
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                repeat(4) { i ->
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                if (i < state.pinInput.length) Color.White
                                else Color.White.copy(alpha = 0.3f)
                            )
                    )
                }
            }

            if (state.error.isNotEmpty()) {
                Text(
                    text = state.error,
                    color = MaterialTheme.colorScheme.errorContainer,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // PIN Keypad
            PinKeypad(
                onDigit   = viewModel::onDigit,
                onDelete  = viewModel::onDelete,
                onBio     = { promptBiometric(context as FragmentActivity, onAuthenticated) }
            )
        }
    }
}

@Composable
private fun PinKeypad(
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    onBio: () -> Unit
) {
    val keys = listOf(
        listOf("1","2","3"),
        listOf("4","5","6"),
        listOf("7","8","9"),
        listOf("bio","0","⌫")
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        keys.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { key ->
                    when (key) {
                        "bio" -> IconButton(
                            onClick = onBio,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.15f))
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = "Биометрия",
                                tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        "⌫" -> PinButton(label = key, onClick = onDelete)
                        else -> PinButton(label = key, onClick = { onDigit(key) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PinButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(72.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White.copy(alpha = 0.15f),
            contentColor = Color.White
        ),
        elevation = ButtonDefaults.buttonElevation(0.dp)
    ) {
        Text(text = label, fontSize = 24.sp, fontWeight = FontWeight.Light)
    }
}

private fun promptBiometric(activity: FragmentActivity, onSuccess: () -> Unit) {
    val executor = ContextCompat.getMainExecutor(activity)
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onSuccess()
        }
    }
    val prompt = BiometricPrompt(activity, executor, callback)
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Вход в ККМ")
        .setSubtitle("Приложите палец или посмотрите в камеру")
        .setNegativeButtonText("PIN-код")
        .build()
    prompt.authenticate(info)
}
