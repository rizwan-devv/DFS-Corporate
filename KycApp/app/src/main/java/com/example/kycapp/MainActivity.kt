package com.example.kycapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.kycapp.navigation.KycNavGraph
import com.example.kycapp.ui.theme.DfsColors
import com.example.kycapp.ui.theme.KycAppTheme

class MainActivity : ComponentActivity() {

    private var inviteTokenState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inviteTokenState.value = extractInviteToken(intent)
        enableEdgeToEdge()
        setContent {
            KycAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DfsColors.Background
                ) {
                    val navController = rememberNavController()
                    var inviteToken by remember { inviteTokenState }
                    KycNavGraph(
                        navController = navController,
                        inviteToken = inviteToken,
                        onInviteConsumed = { inviteToken = null; inviteTokenState.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        inviteTokenState.value = extractInviteToken(intent)
    }

    private fun extractInviteToken(intent: Intent?): String? {
        val data: Uri = intent?.data ?: return null
        // dfscorporate://kyc?token=...
        if (data.scheme.equals("dfscorporate", ignoreCase = true) &&
            data.host.equals("kyc", ignoreCase = true)
        ) {
            return data.getQueryParameter("token")?.takeIf { it.isNotBlank() }
        }
        // Also accept https://.../kyc?token= if added later
        if (data.path?.contains("kyc", ignoreCase = true) == true) {
            return data.getQueryParameter("token")?.takeIf { it.isNotBlank() }
        }
        return data.getQueryParameter("token")?.takeIf { it.isNotBlank() }
    }
}
