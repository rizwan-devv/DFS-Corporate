package com.example.kycapp.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.kycapp.R
import com.example.kycapp.navigation.HandSide
import com.example.kycapp.ui.components.DfsAnimatedSection
import com.example.kycapp.ui.components.DfsCard
import com.example.kycapp.ui.components.DfsPrimaryButton
import com.example.kycapp.ui.components.DfsScreen
import com.example.kycapp.ui.components.DfsScreenHeader
import com.example.kycapp.ui.theme.DfsColors

/**
 * Instruction screen shown before each fingerprint capture.
 * Reused for both hands -- just pass which HandSide this is for.
 */
@Composable
fun FingerprintTutorialScreen(
    hand: HandSide,
    onScanClick: () -> Unit,
    onBack: () -> Unit
) {
    DfsScreen {
        DfsAnimatedSection {
            DfsScreenHeader(
                title = "Instructions",
                subtitle = "Line up your hand with the guide.\nKeep your fingers together.\nThen stay still.",
                badge = "${hand.label} hand",
                onBack = onBack
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        DfsAnimatedSection {
            DfsCard {
                Image(
                    painter = painterResource(id = R.drawable.fingerprint_instruction_hand),
                    contentDescription = "Line up your hand with the guide",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExampleBadge(good = true)
                    ExampleBadge(good = false)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        DfsPrimaryButton(
            text = "SCAN ${hand.label.uppercase()}",
            onClick = onScanClick
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ExampleBadge(good: Boolean) {
    Box(
        modifier = Modifier.size(44.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (good) Icons.Default.Check else Icons.Default.Close,
            contentDescription = if (good) "Correct example" else "Incorrect example",
            tint = if (good) DfsColors.Success else DfsColors.Danger,
            modifier = Modifier.size(36.dp)
        )
    }
}
