package com.example.kycapp.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kycapp.BuildConfig
import com.example.kycapp.dfs.DfsApiClient
import com.example.kycapp.dfs.DfsApiException
import com.example.kycapp.dfs.DfsSession
import com.example.kycapp.ui.components.DfsAnimatedSection
import com.example.kycapp.ui.components.DfsBrandMark
import com.example.kycapp.ui.components.DfsCard
import com.example.kycapp.ui.components.DfsPrimaryButton
import com.example.kycapp.ui.components.DfsScreen
import com.example.kycapp.ui.components.DfsStatusBadge
import com.example.kycapp.ui.theme.DfsColors
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    inviteToken: String?,
    onLoggedIn: (DfsSession) -> Unit
) {
    var phone by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var inviteBusy by remember { mutableStateOf(!inviteToken.isNullOrBlank()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(inviteToken) {
        val token = inviteToken?.trim().orEmpty()
        if (token.isBlank()) return@LaunchedEffect
        inviteBusy = true
        error = null
        try {
            val session = DfsApiClient.openInvite(token)
            onLoggedIn(session)
        } catch (e: Exception) {
            error = (e as? DfsApiException)?.message ?: (e.message ?: "Invalid invite link")
            inviteBusy = false
        }
    }

    fun doLogin() {
        error = null
        loading = true
        scope.launch {
            try {
                val session = DfsApiClient.login(phone, pin)
                onLoggedIn(session)
            } catch (e: Exception) {
                error = (e as? DfsApiException)?.message ?: (e.message ?: "Login failed")
            } finally {
                loading = false
            }
        }
    }

    DfsScreen {
        DfsAnimatedSection {
            DfsBrandMark()
            Spacer(modifier = Modifier.height(16.dp))
            DfsStatusBadge(label = "Partner KYC")
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Identity check",
                color = DfsColors.OnBackground,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                letterSpacing = (-0.3).sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Open your invite link, or sign in with phone + temporary PIN from email.",
                color = DfsColors.MutedText,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (error != null) {
            DfsCard {
                Text(text = error!!, color = DfsColors.Danger, fontSize = 13.sp, lineHeight = 18.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (inviteBusy) {
            DfsCard {
                Text(
                    text = "Verifying invite link…",
                    color = DfsColors.MutedText,
                    fontSize = 14.sp
                )
            }
        } else {
            DfsAnimatedSection {
                DfsCard {
                    Text(
                        text = "Phone (user ID)",
                        color = DfsColors.OnBackground,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    DfsField(
                        value = phone,
                        onValueChange = { phone = it },
                        placeholder = "03XXXXXXXXX",
                        keyboardType = KeyboardType.Phone
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Temporary PIN",
                        color = DfsColors.OnBackground,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    DfsField(
                        value = pin,
                        onValueChange = { pin = it },
                        placeholder = "6-digit PIN",
                        keyboardType = KeyboardType.NumberPassword,
                        password = true
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    DfsPrimaryButton(
                        text = if (loading) "Signing in…" else "Continue",
                        onClick = { doLogin() },
                        enabled = !loading && phone.isNotBlank() && pin.isNotBlank()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "API: ${BuildConfig.DFS_API_BASE}",
                        color = DfsColors.MutedText,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun DfsField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    password: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(placeholder, color = DfsColors.MutedText.copy(alpha = 0.6f)) },
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = DfsColors.OnBackground,
            unfocusedTextColor = DfsColors.OnBackground,
            focusedBorderColor = DfsColors.Primary,
            unfocusedBorderColor = DfsColors.Border,
            cursorColor = DfsColors.Primary,
            focusedContainerColor = DfsColors.Background.copy(alpha = 0.45f),
            unfocusedContainerColor = DfsColors.Background.copy(alpha = 0.45f)
        )
    )
}
