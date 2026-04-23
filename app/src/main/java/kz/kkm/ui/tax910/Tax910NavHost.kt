package kz.kkm.ui.tax910

import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument

/**
 * Internal nav-host for the multi-step 910.00 declaration wizard.
 * Embedded as a single composable destination in the root NavHost.
 */
@Composable
fun Tax910NavHost(onBack: () -> Unit) {
    val nav = rememberNavController()

    NavHost(nav, startDestination = "tax910_list") {

        // List of existing periods + create new
        composable("tax910_list") {
            Tax910ListScreen(
                onStartNew = { id -> nav.navigate("tax910_step1/$id") },
                onBack     = onBack
            )
        }

        // Step 1 â Income summary (auto-loaded from KKM)
        composable(
            "tax910_step1/{periodId}",
            arguments = listOf(navArgument("periodId") { type = NavType.LongType })
        ) { back ->
            val id = back.arguments?.getLong("periodId") ?: 0L
            Tax910Step1Screen(
                periodId = id,
                onNext   = { nav.navigate("tax910_step2/$id") },
                onBack   = { nav.popBackStack() }
            )
        }

        // Step 2 â Employees & payroll calculation
        composable(
            "tax910_step2/{periodId}",
            arguments = listOf(navArgument("periodId") { type = NavType.LongType })
        ) { back ->
            val id = back.arguments?.getLong("periodId") ?: 0L
            Tax910Step2Screen(
                periodId = id,
                onNext   = { nav.navigate("tax910_step3/$id") },
                onBack   = { nav.popBackStack() }
            )
        }

        // Step 3 â Summary of form 910.00 lines
        composable(
            "tax910_step3/{periodId}",
            arguments = listOf(navArgument("periodId") { type = NavType.LongType })
        ) { back ->
            val id = back.arguments?.getLong("periodId") ?: 0L
            Tax910Step3Screen(
                periodId = id,
                onNext   = { nav.navigate("tax910_step4/$id") },
                onBack   = { nav.popBackStack() }
            )
        }

        // Step 4 â Sign (cloud EDS NCA RK) and submit to ISNA
        composable(
            "tax910_step4/{periodId}",
            arguments = listOf(navArgument("periodId") { type = NavType.LongType })
        ) { back ->
            val id = back.arguments?.getLong("periodId") ?: 0L
            Tax910Step4Screen(
                periodId = id,
                onSubmitted = {
                    nav.navigate("tax910_list") { popUpTo("tax910_list") { inclusive = true } }
                },
                onBack = { nav.popBackStack() }
            )
        }
    }
}
