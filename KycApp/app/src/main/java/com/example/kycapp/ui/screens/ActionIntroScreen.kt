package com.example.kycapp.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kycapp.ui.components.DfsAnimatedSection
import com.example.kycapp.ui.components.DfsCard
import com.example.kycapp.ui.components.DfsPrimaryButton
import com.example.kycapp.ui.components.DfsScreen
import com.example.kycapp.ui.components.DfsScreenHeader
import com.example.kycapp.ui.theme.DfsColors

/**
 * Reused by ID Card scan, OCR, and Face Matching -- each just needs
 * a title and a single "Start ..." button that opens the camera.
 */
@Composable
fun ActionIntroScreen(
    title: String,
    buttonLabel: String,
    onStartClick: () -> Unit
) {
    DfsScreen {
        Spacer(modifier = Modifier.height(12.dp))
        DfsAnimatedSection {
            DfsScreenHeader(
                title = title,
                subtitle = "Follow on-screen guidance. All processing stays on this device.",
                badge = "On-device KYC"
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        DfsAnimatedSection {
            DfsCard(glow = true) {
                Text(
                    text = "Ready when you are",
                    style = MaterialTheme.typography.titleMedium,
                    color = DfsColors.OnBackground
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Camera will open for capture. Keep the document or face well lit.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                DfsPrimaryButton(text = buttonLabel, onClick = onStartClick)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
