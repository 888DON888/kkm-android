package kz.kkm.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import kz.kkm.ui.auth.AuthScreen
import kz.kkm.ui.catalog.CatalogScreen
import kz.kkm.ui.main.MainScreen
import kz.kkm.ui.receipt.PaymentScreen
import kz.kkm.ui.receipt.ReceiptDetailScreen
import kz.kkm.ui.receipt.ReceiptDoneScreen
import kz.kkm.ui.reports.ReportsMenuScreen
import kz.kkm.ui.returns.ReturnsScreen
import kz.kkm.ui.settings.SettingsScreen
import kz.kkm.ui.shift.ShiftScreen
import kz.kkm.ui.tax910.Tax910NavHost
import kz.kkm.ui.theme.KkmTheme

// âââââââââââââââââââ Routes âââââââââââââââââââââââââââââââââââ

object Routes {
    const val AUTH            = "auth"
    const val PIN_SETUP       = "pin_setup"
    const val MAIN            = "main"
    const val SHIFT           = "shift"
    const val PAYMENT         = "payment"
    const val RECEIPT_DONE    = "receipt_done/{receiptId}"
    const val RECEIPT_DETAIL  = "receipt_detail/{receiptId}"
    const val RETURNS         = "returns"
    const val REPORTS         = "reports"
    const val CATALOG         = "catalog"
    const val TAX_910         = "tax_910"
    const val SETTINGS        = "settings"

    fun receiptDone(id: Long)   = "receipt_done/$id"
    fun receiptDetail(id: Long) = "receipt_detail/$id"
}

// âââââââââââââââââââ Activity âââââââââââââââââââââââââââââââââ

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Prevent screenshots on all screens
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        enableEdgeToEdge()
        setContent {
            KkmTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    KkmNavHost(rememberNavController())
                }
            }
        }
    }
}

// âââââââââââââââââââ Nav Graph ââââââââââââââââââââââââââââââââ

@Composable
fun KkmNavHost(nav: NavHostController) {
    NavHost(nav, startDestination = Routes.AUTH) {

        // ââ Auth ââââââââââââââââââââââââââââââââââââââââââââââ
        composable(Routes.AUTH) {
            AuthScreen(
                onAuthenticated    = { nav.navigate(Routes.MAIN) { popUpTo(Routes.AUTH) { inclusive = true } } }
            )
        }
            })
        }

        // ââ Main Cash Register ââââââââââââââââââââââââââââââââ
        composable(Routes.MAIN) {
            MainScreen(
                onNavigateToPayment  = { nav.navigate(Routes.PAYMENT) },
                onNavigateToReturns  = { nav.navigate(Routes.RETURNS) },
                onNavigateToXReport  = { nav.navigate(Routes.REPORTS) },
                onNavigateToZReport  = { nav.navigate(Routes.SHIFT) },
                onNavigateToJournal  = { nav.navigate(Routes.REPORTS) },
                onNavigateToCatalog  = { nav.navigate(Routes.CATALOG) },
                onNavigateToTax910   = { nav.navigate(Routes.TAX_910) },
                onNavigateToSettings = { nav.navigate(Routes.SETTINGS) }
            )
        }

        // ââ Shift management ââââââââââââââââââââââââââââââââââ
        composable(Routes.SHIFT) {
            ShiftScreen(onBack = { nav.popBackStack() })
        }

        // ââ Payment âââââââââââââââââââââââââââââââââââââââââââ
        composable(Routes.PAYMENT) {
            PaymentScreen(
                onPaymentComplete = { id -> nav.navigate(Routes.receiptDone(id)) { popUpTo(Routes.PAYMENT) { inclusive = true } } },
                onBack            = { nav.popBackStack() }
            )
        }

        // ââ Receipt done (after payment) ââââââââââââââââââââââ
        composable(
            Routes.RECEIPT_DONE,
            arguments = listOf(navArgument("receiptId") { type = NavType.LongType })
        ) { back ->
            val id = back.arguments?.getLong("receiptId") ?: 0L
            ReceiptDoneScreen(
                receiptId    = id,
                onNewReceipt = { nav.navigate(Routes.MAIN) { popUpTo(Routes.MAIN) { inclusive = true } } },
                onBack       = { nav.navigate(Routes.MAIN) { popUpTo(Routes.MAIN) { inclusive = true } } }
            )
        }

        // ââ Receipt detail (history / journal) ââââââââââââââââ
        composable(
            Routes.RECEIPT_DETAIL,
            arguments = listOf(navArgument("receiptId") { type = NavType.LongType })
        ) { back ->
            val id = back.arguments?.getLong("receiptId") ?: 0L
            ReceiptDetailScreen(receiptId = id, onBack = { nav.popBackStack() })
        }

        // ââ Returns âââââââââââââââââââââââââââââââââââââââââââ
        composable(Routes.RETURNS) {
            ReturnsScreen(
                onReturnComplete = { nav.popBackStack() },
                onBack = { nav.popBackStack() })
        }

        // ââ Reports âââââââââââââââââââââââââââââââââââââââââââ
        composable(Routes.REPORTS) {
            ReportsMenuScreen(
                onOpenXReport = { nav.navigate("x_report") },
                onOpenZReport = { nav.navigate(Routes.SHIFT) },
                onBack        = { nav.popBackStack() }
            )
        }
        composable("x_report") {
            kz.kkm.ui.reports.XReportScreen(onBack = { nav.popBackStack() })
        }

        // ââ Catalog âââââââââââââââââââââââââââââââââââââââââââ
        composable(Routes.CATALOG) {
            CatalogScreen(onBack = { nav.popBackStack() })
        }

        // ââ Tax 910 (multi-step) ââââââââââââââââââââââââââââââ
        composable(Routes.TAX_910) {
            Tax910NavHost(onBack = { nav.popBackStack() })
        }

        // ââ Settings ââââââââââââââââââââââââââââââââââââââââââ
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
