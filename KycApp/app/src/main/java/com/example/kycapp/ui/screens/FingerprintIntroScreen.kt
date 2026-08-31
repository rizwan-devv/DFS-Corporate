package com.example.kycapp.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kycapp.navigation.HandSide
import com.example.kycapp.ui.components.DfsAnimatedSection
import com.example.kycapp.ui.components.DfsCard
import com.example.kycapp.ui.components.DfsPrimaryButton
import com.example.kycapp.ui.components.DfsScreen
import com.example.kycapp.ui.components.DfsScreenHeader
import com.example.kycapp.ui.components.DfsSecondaryButton
import com.example.kycapp.ui.theme.DfsColors

@Composable
fun FingerprintIntroScreen(
    onEnrollClick: () -> Unit,
    onMatchClick: (HandSide) -> Unit,
    onViewRecordsClick: () -> Unit
) {
    DfsScreen {
        DfsAnimatedSection {
            DfsScreenHeader(
                title = "Fingerprint Matching",
                subtitle = "Enroll both hands or verify against stored templates.",
                badge = "Biometrics"
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        DfsAnimatedSection {
            DfsCard(glow = true) {
                Text(
                    text = "Enrollment",
                    style = MaterialTheme.typography.titleMedium,
                    color = DfsColors.OnBackground
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Capture left then right hand under one person name.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(14.dp))
                DfsPrimaryButton(text = "Enroll Fingerprint", onClick = onEnrollClick)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        DfsAnimatedSection {
            DfsCard {
                Text(
                    text = "Match against",
                    style = MaterialTheme.typography.titleMedium,
                    color = DfsColors.OnBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Templates are keyed per hand — choose which hand you are presenting.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(14.dp))
                DfsSecondaryButton(
                    text = "Match Left Hand",
                    onClick = { onMatchClick(HandSide.LEFT) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                DfsSecondaryButton(
                    text = "Match Right Hand",
                    onClick = { onMatchClick(HandSide.RIGHT) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        DfsSecondaryButton(text = "View Stored Records", onClick = onViewRecordsClick)
        Spacer(modifier = Modifier.height(16.dp))
    }
}
