package kz.kkm.ui

sealed class Screen(val route: String) {
    object Auth        : Screen("auth")
    object Main        : Screen("main")
    object Payment     : Screen("payment")
    object ReceiptDone : Screen("receipt_done/{receiptId}") {
        fun withId(id: Long) = "receipt_done/$id"
    }
    object Returns     : Screen("returns")
    object ReturnDetail: Screen("return_detail/{receiptId}") {
        fun withId(id: Long) = "return_detail/$id"
    }
    object XReport     : Screen("x_report")
    object ZReport     : Screen("z_report")
    object Journal     : Screen("journal")
    object Catalog     : Screen("catalog")
    object Tax910      : Screen("tax910")
    object Tax910Step1 : Screen("tax910_step1/{periodId}") {
        fun withId(id: Long) = "tax910_step1/$id"
    }
    object Tax910Step2 : Screen("tax910_step2/{periodId}") {
        fun withId(id: Long) = "tax910_step2/$id"
    }
    object Tax910Step3 : Screen("tax910_step3/{periodId}") {
        fun withId(id: Long) = "tax910_step3/$id"
    }
    object Tax910Step4 : Screen("tax910_step4/{periodId}") {
        fun withId(id: Long) = "tax910_step4/$id"
    }
    object Settings    : Screen("settings")
}
