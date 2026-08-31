package com.example.kycapp.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kycapp.ui.components.DfsAnimatedSection
import com.example.kycapp.ui.components.DfsCard
import com.example.kycapp.ui.components.DfsPrimaryButton
import com.example.kycapp.ui.components.DfsScreen
import com.example.kycapp.ui.components.DfsScreenHeader
import com.example.kycapp.ui.components.DfsTextButton
import com.example.kycapp.ui.theme.DfsColors

/**
 * Shown once before enrolling a fingerprint set, so the 8 templates captured
 * across the left/right hand steps (4 non-thumb fingers each) all land under
 * one person's name.
 */
@Composable
fun FingerprintNameScreen(
    onContinue: (name: String) -> Unit,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf("") }

    DfsScreen {
        DfsAnimatedSection {
            DfsScreenHeader(
                title = "Enroll Fingerprint",
                subtitle = "Enter the name this fingerprint set should be enrolled under.",
                badge = "Step 1 · Name",
                onBack = onBack
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        DfsAnimatedSection {
            DfsCard(glow = true) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DfsColors.Primary,
                        unfocusedBorderColor = DfsColors.BorderStrong,
                        focusedLabelColor = DfsColors.Primary,
                        unfocusedLabelColor = DfsColors.MutedText,
                        cursorColor = DfsColors.Primary,
                        focusedTextColor = DfsColors.OnBackground,
                        unfocusedTextColor = DfsColors.OnBackground,
                        focusedContainerColor = DfsColors.SurfaceElevated,
                        unfocusedContainerColor = DfsColors.SurfaceElevated
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
                DfsPrimaryButton(
                    text = "Continue",
                    onClick = { onContinue(name.trim()) },
                    enabled = name.isNotBlank()
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        DfsTextButton(text = "Back", onClick = onBack, color = DfsColors.MutedText)
        Spacer(modifier = Modifier.height(16.dp))
    }
}
