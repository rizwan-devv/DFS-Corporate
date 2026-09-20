package com.example.kycapp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.kycapp.camera.CameraCaptureScreen
import com.example.kycapp.dfs.DfsSession
import com.example.kycapp.dfs.DfsSessionStore
import com.example.kycapp.ui.screens.ActionIntroScreen
import com.example.kycapp.ui.screens.FingerprintIntroScreen
import com.example.kycapp.ui.screens.FingerprintNameScreen
import com.example.kycapp.ui.screens.FingerprintRecordViewerScreen
import com.example.kycapp.ui.screens.FingerprintTutorialScreen
import com.example.kycapp.ui.screens.LoginScreen
import com.example.kycapp.ui.screens.MainMenuScreen
import com.example.kycapp.ui.screens.SignatureCaptureScreen

@Composable
fun KycNavGraph(
    navController: NavHostController,
    inviteToken: String? = null,
    onInviteConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val store = remember { DfsSessionStore(context) }
    var session by remember { mutableStateOf(store.load()) }
    var pendingInvite by remember { mutableStateOf(inviteToken) }

    LaunchedEffect(inviteToken) {
        if (!inviteToken.isNullOrBlank()) {
            pendingInvite = inviteToken
            session = null
            store.clear()
        }
    }

    val start = if (session == null) Routes.LOGIN else Routes.MAIN_MENU

    NavHost(navController = navController, startDestination = start) {

        composable(Routes.LOGIN) {
            LoginScreen(
                inviteToken = pendingInvite,
                onLoggedIn = { s ->
                    store.save(s)
                    session = s
                    pendingInvite = null
                    onInviteConsumed()
                    navController.navigate(Routes.MAIN_MENU) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.MAIN_MENU) {
            // Guard: require DFS session
            if (session == null) {
                LaunchedEffect(Unit) {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.MAIN_MENU) { inclusive = true }
                    }
                }
                return@composable
            }
            MainMenuScreen(
                session = session,
                onIdCardClick = { navController.navigate(Routes.ID_CARD_INTRO) },
                onOcrClick = { navController.navigate(Routes.OCR_INTRO) },
                onFaceMatchClick = { navController.navigate(Routes.FACE_MATCH_INTRO) },
                onFingerprintClick = { navController.navigate(Routes.FINGERPRINT_INTRO) },
                onFingerprintRecordsClick = { navController.navigate(Routes.fingerprintRecords()) },
                onSignatureClick = { navController.navigate(Routes.SIGNATURE) },
                onSessionUpdated = { updated: DfsSession ->
                    store.save(updated)
                    session = updated
                },
                onSignOut = {
                    store.clear()
                    session = null
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.MAIN_MENU) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.SIGNATURE) {
            val s = session
            if (s == null) {
                LaunchedEffect(Unit) {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.SIGNATURE) { inclusive = true }
                    }
                }
                return@composable
            }
            SignatureCaptureScreen(
                session = s,
                onDone = { updated ->
                    store.save(updated)
                    session = updated
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.ID_CARD_INTRO) {
            ActionIntroScreen(
                title = "ID Card Detection",
                buttonLabel = "Start Scanning",
                onStartClick = {
                    navController.navigate(Routes.camera(CaptureMode.ID_CARD.name))
                }
            )
        }

        composable(Routes.OCR_INTRO) {
            ActionIntroScreen(
                title = "Perform OCR",
                buttonLabel = "Start OCR",
                onStartClick = {
                    navController.navigate(Routes.camera(CaptureMode.OCR.name))
                }
            )
        }

        composable(Routes.FACE_MATCH_INTRO) {
            ActionIntroScreen(
                title = "Face Matching",
                buttonLabel = "Start Face Scan",
                onStartClick = {
                    navController.navigate(Routes.camera(CaptureMode.FACE_MATCH.name))
                }
            )
        }

        composable(Routes.FINGERPRINT_INTRO) {
            FingerprintIntroScreen(
                onEnrollClick = {
                    navController.navigate(Routes.FINGERPRINT_NAME_ENTRY)
                },
                onMatchClick = { hand ->
                    navController.navigate(
                        Routes.fingerprintTutorial(CaptureMode.FINGERPRINT_MATCH.name, hand.name)
                    )
                },
                onViewRecordsClick = {
                    navController.navigate(Routes.fingerprintRecords())
                }
            )
        }

        composable(
            route = Routes.FINGERPRINT_RECORDS,
            arguments = listOf(navArgument("name") { type = NavType.StringType })
        ) { backStackEntry ->
            val name = Routes.decodeName(backStackEntry.arguments?.getString("name"))
            FingerprintRecordViewerScreen(
                personNameFilter = name,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.FINGERPRINT_NAME_ENTRY) {
            FingerprintNameScreen(
                onContinue = { name ->
                    navController.navigate(
                        Routes.fingerprintTutorial(CaptureMode.FINGERPRINT_ENROLL.name, HandSide.LEFT.name, name)
                    )
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.FINGERPRINT_TUTORIAL,
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType },
                navArgument("hand") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val mode = CaptureMode.fromRoute(backStackEntry.arguments?.getString("mode") ?: "")
            val hand = HandSide.fromRoute(backStackEntry.arguments?.getString("hand") ?: "")
            val name = Routes.decodeName(backStackEntry.arguments?.getString("name"))

            FingerprintTutorialScreen(
                hand = hand,
                onScanClick = { navController.navigate(Routes.camera(mode.name, hand.name, name)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.CAMERA,
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType },
                navArgument("hand") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val mode = CaptureMode.fromRoute(backStackEntry.arguments?.getString("mode") ?: "")
            val hand = HandSide.fromRoute(backStackEntry.arguments?.getString("hand") ?: "")
            val name = Routes.decodeName(backStackEntry.arguments?.getString("name"))

            CameraCaptureScreen(
                mode = mode,
                hand = hand,
                personName = name,
                onBack = { navController.popBackStack() },
                onScanSuccess = {
                    if (mode == CaptureMode.FINGERPRINT_ENROLL && hand == HandSide.LEFT) {
                        navController.navigate(
                            Routes.fingerprintTutorial(CaptureMode.FINGERPRINT_ENROLL.name, HandSide.RIGHT.name, name)
                        ) {
                            popUpTo(Routes.FINGERPRINT_NAME_ENTRY)
                        }
                    } else if (mode == CaptureMode.FINGERPRINT_ENROLL) {
                        navController.navigate(Routes.fingerprintRecords(name)) {
                            popUpTo(Routes.MAIN_MENU)
                        }
                    } else {
                        navController.navigate(Routes.MAIN_MENU) {
                            popUpTo(Routes.MAIN_MENU) { inclusive = true }
                        }
                    }
                }
            )
        }
    }
}
