package com.example.kycapp.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kycapp.dfs.DfsApiClient
import com.example.kycapp.dfs.DfsApiException
import com.example.kycapp.dfs.DfsSession
import com.example.kycapp.ui.components.DfsAnimatedSection
import com.example.kycapp.ui.components.DfsBrandMark
import com.example.kycapp.ui.components.DfsCard
import com.example.kycapp.ui.components.DfsSecondaryButton
import com.example.kycapp.ui.components.DfsMenuButton
import com.example.kycapp.ui.components.DfsPrimaryButton
import com.example.kycapp.ui.components.DfsScreen
import com.example.kycapp.ui.components.DfsStatusBadge
import com.example.kycapp.ui.theme.DfsColors
import kotlinx.coroutines.launch

@Composable
fun MainMenuScreen(
    session: DfsSession?,
    onIdCardClick: () -> Unit,
    onOcrClick: () -> Unit,
    onFaceMatchClick: () -> Unit,
    onFingerprintClick: () -> Unit,
    onFingerprintRecordsClick: () -> Unit = {},
    onSignatureClick: () -> Unit = {},
    onSessionUpdated: (DfsSession) -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val completed = session?.status == "KYC_COMPLETED"
    val bankVisit = session?.status == "BANK_VISIT_REQUIRED"

    DfsScreen {
        DfsAnimatedSection {
            DfsBrandMark()
            Spacer(modifier = Modifier.height(16.dp))
            DfsStatusBadge(label = "Partner KYC")
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Identity verification",
                color = DfsColors.OnBackground,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                letterSpacing = (-0.3).sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (session != null) {
                Text(
                    text = "${session.fullName} · ${session.businessName ?: "Entity"}",
                    color = DfsColors.OnBackground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Status: ${session.status}" +
                        (session.trackingId?.let { " · $it" } ?: ""),
                    color = DfsColors.MutedText,
                    fontSize = 13.sp
                )
            } else {
                Text(
                    text = "On-device identity verification — secure, offline, corporate-ready.",
                    color = DfsColors.MutedText,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (error != null) {
            DfsCard {
                Text(text = error!!, color = DfsColors.Danger, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (message != null) {
            DfsCard {
                Text(text = message!!, color = DfsColors.Success, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (completed) {
            DfsCard {
                Text(
                    text = "KYC already submitted to DFS. Backoffice can approve once all partners are complete. Capture signature if not done yet.",
                    color = DfsColors.Success,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        if (bankVisit) {
            DfsCard {
                Text(
                    text = "Phone KYC failed 3 times. Please visit the bank/office. Backoffice can approve your partner KYC with a reason.",
                    color = DfsColors.Danger,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        DfsAnimatedSection {
            DfsMenuButton(text = "ID Card Detecting", onClick = onIdCardClick)
            Spacer(modifier = Modifier.height(12.dp))
            DfsMenuButton(text = "Perform OCR", onClick = onOcrClick)
            Spacer(modifier = Modifier.height(12.dp))
            DfsMenuButton(text = "Face Matching", onClick = onFaceMatchClick)
            Spacer(modifier = Modifier.height(12.dp))
            DfsMenuButton(text = "Fingerprint Matching", onClick = onFingerprintClick)
            Spacer(modifier = Modifier.height(12.dp))
            DfsMenuButton(text = "All Fingerprint Records", onClick = onFingerprintRecordsClick)
            Spacer(modifier = Modifier.height(12.dp))
            DfsMenuButton(text = "Capture signature (end step)", onClick = onSignatureClick)
        }

        if (session != null && !completed && !bankVisit) {
            Spacer(modifier = Modifier.height(20.dp))
            DfsPrimaryButton(
                text = if (submitting) "Submitting…" else "Submit KYC to DFS",
                onClick = {
                    error = null
                    message = null
                    submitting = true
                    scope.launch {
                        try {
                            val updated = DfsApiClient.complete(session.sessionToken)
                            onSessionUpdated(updated)
                            message = "Submitted to DFS. Status: ${updated.status}. Next: capture signature."
                        } catch (e: Exception) {
                            error = (e as? DfsApiException)?.message ?: (e.message ?: "Submit failed")
                        } finally {
                            submitting = false
                        }
                    }
                },
                enabled = !submitting
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Run ID / OCR / face / fingerprint first, then submit. After submit, capture your signature.",
                color = DfsColors.MutedText,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            DfsSecondaryButton(
                text = "Report KYC failure (e.g. face mismatch)",
                onClick = {
                    error = null
                    message = null
                    submitting = true
                    scope.launch {
                        try {
                            val updated = DfsApiClient.fail(
                                session.sessionToken,
                                "Face did not match ID card / phone KYC verification failed"
                            )
                            onSessionUpdated(updated)
                            message = "KYC fail recorded. Status: ${updated.status}"
                        } catch (e: Exception) {
                            error = (e as? DfsApiException)?.message ?: (e.message ?: "Fail report error")
                        } finally {
                            submitting = false
                        }
                    }
                }
            )
        }

        if (session != null && completed) {
            Spacer(modifier = Modifier.height(16.dp))
            DfsPrimaryButton(text = "Capture / re-upload signature", onClick = onSignatureClick)
        }

        if (session != null) {
            Spacer(modifier = Modifier.height(16.dp))
            DfsSecondaryButton(text = "Sign out", onClick = onSignOut)
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "LAN API · invite link dfscorporate://kyc?token=",
            color = DfsColors.MutedText,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
}
