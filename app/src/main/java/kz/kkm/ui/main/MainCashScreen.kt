package kz.kkm.ui.main

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kz.kkm.ui.receipt.PaymentScreen
import kz.kkm.ui.receipt.ReceiptDoneScreen

@Composable
fun MainCashScreen(
    onOpenShiftManager: () -> Unit,
    onOpenReceipt: (Long) -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenReturns: () -> Unit,
    onOpenTax910: () -> Unit,
    onOpenSettings: () -> Unit,
    mainVm: MainViewModel = hiltViewModel()
) {
    val innerNav = rememberNavController()

    NavHost(navController = innerNav, startDestination = "cash_main") {

        composable("cash_main") {
            MainScreen(
                onNavigateToPayment  = { innerNav.navigate("payment") },
                onNavigateToReturns  = onOpenReturns,
                onNavigateToXReport  = onOpenReports,
                onNavigateToZReport  = onOpenShiftManager,
                onNavigateToJournal  = onOpenReports,
                onNavigateToCatalog  = onOpenCatalog,
                onNavigateToTax910   = onOpenTax910,
                onNavigateToSettings = onOpenSettings,
                viewModel            = mainVm
            )
        }

        composable("payment") {
            PaymentScreen(
                onBack = { innerNav.popBackStack() },
                onPaymentComplete = { receiptId ->
                    innerNav.navigate("receipt_done/$receiptId") {
                        popUpTo("cash_main") { inclusive = false }
                    }
                },
                mainVm = mainVm
            )
        }

        composable("receipt_done/{receiptId}") { back ->
            val receiptId = back.arguments?.getString("receiptId")?.toLong() ?: 0L
            ReceiptDoneScreen(
                receiptId    = receiptId,
                onNewReceipt = {
                    innerNav.navigate("cash_main") {
                        popUpTo("cash_main") { inclusive = true }
                    }
                },
                onBack = {
                    innerNav.navigate("cash_main") {
                        popUpTo("cash_main") { inclusive = true }
                    }
                }
            )
        }
    }
}
