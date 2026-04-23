package kz.kkm.tax

import kz.kkm.domain.model.Employee
import kz.kkm.domain.model.PayrollEntry
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tax calculator for Kazakhstan 2025.
 * Rates: OPV 10%, OSMS 2%, IPN 10%, SO 3.5%, VOSMS 3%, SN 9.5% - SO.
 */
@Singleton
class TaxCalculator @Inject constructor() {

    companion object {
        // 2025 values
        val MRP  = BigDecimal("3932")       // Месячный расчётный показатель
        val MZP  = BigDecimal("85000")      // Минимальная зарплата
        val OPV_RATE     = BigDecimal("0.10")
        val OSMS_RATE    = BigDecimal("0.02")
        val IPN_RATE     = BigDecimal("0.10")
        val SO_RATE      = BigDecimal("0.035")
        val VOSMS_RATE   = BigDecimal("0.03")
        val SN_RATE      = BigDecimal("0.095")
        val TAX_910_RATE = BigDecimal("0.03")  // 3% of income
        val OPV_MAX_BASE = MRP * BigDecimal("50")  // 196 600 tenge/month
        val MZP_DEDUCTION_LIMIT = MRP * BigDecimal("25") // 98 300 — upper limit for MZP deduction
        val SN_MIN_PER_MONTH = MRP * BigDecimal("2")     // 7 864 tenge/month
    }

    /**
     * Calculate all taxes for one employee for a given period.
     * [monthlyGross] — gross salary per month.
     * [monthsWorked] — number of months in the period (1–6).
     */
    fun calculateForEmployee(
        employee: Employee,
        monthlyGross: BigDecimal,
        monthsWorked: Int
    ): EmployeeTaxResult {
        val results = (1..monthsWorked).map { calcMonth(employee, monthlyGross) }
        return EmployeeTaxResult(
            grossIncome  = results.sumOf { it.gross },
            opv          = results.sumOf { it.opv },
            osms         = results.sumOf { it.osms },
            ipn          = results.sumOf { it.ipn },
            so           = results.sumOf { it.so },
            vosms        = results.sumOf { it.vosms },
            sn           = results.sumOf { it.sn },
            netIncome    = results.sumOf { it.net }
        )
    }

    private fun calcMonth(employee: Employee, gross: BigDecimal): MonthCalc {
        // Step 1: OPV (employee, 10%, capped at 50 MRP)
        val opvBase = if (gross > OPV_MAX_BASE) OPV_MAX_BASE else gross
        val opv = if (employee.isPensioner) BigDecimal.ZERO
                  else (opvBase * OPV_RATE).round2()

        // Step 2: OSMS (employee, 2%)
        val osms = (gross * OSMS_RATE).round2()

        // Step 3: Taxable income for IPN
        val mzpDeduction = if (gross <= MZP_DEDUCTION_LIMIT) MZP else BigDecimal.ZERO
        val disabilityDeduction = if (employee.isDisabled) MRP * BigDecimal("882") else BigDecimal.ZERO
        var taxableIpn = gross - opv - osms - mzpDeduction - disabilityDeduction
        if (taxableIpn < BigDecimal.ZERO) taxableIpn = BigDecimal.ZERO

        // Step 4: IPN (10%)
        val ipn = (taxableIpn * IPN_RATE).round2()

        // Step 5: SO (employer, 3.5% of gross - opv), min 3.5% * MZP
        val soBase = (gross - opv).let { if (it < BigDecimal.ZERO) BigDecimal.ZERO else it }
        val soCalc = (soBase * SO_RATE).round2()
        val soMin  = (MZP * SO_RATE).round2()
        val so = if (employee.isCivilContract) BigDecimal.ZERO
                 else maxOf(soCalc, soMin)

        // Step 6: VOSMS (employer, 3%)
        val vosms = (gross * VOSMS_RATE).round2()

        // Step 7: Social tax (employer) = 9.5% - SO, min 2 MRP
        val sn = if (employee.isCivilContract) BigDecimal.ZERO
                 else {
                     val snCalc = (gross * SN_RATE).round2() - so
                     maxOf(snCalc, SN_MIN_PER_MONTH)
                 }

        val net = (gross - opv - osms - ipn).round2()

        return MonthCalc(gross, opv, osms, ipn, so, vosms, sn, net)
    }

    /**
     * Calculate 910.00 entrepreneur tax (3% of income).
     */
    fun calculate910Tax(income: BigDecimal): Tax910Result {
        val total = (income * TAX_910_RATE).round2()
        val ipn = (income * BigDecimal("0.015")).round2()
        val sn  = (total - ipn).round2()
        return Tax910Result(income, total, ipn, sn)
    }

    /**
     * Build complete 910.00 summary from payroll + income.
     */
    fun build910Summary(
        income1H: BigDecimal,
        income2H: BigDecimal,
        payrollEntries: List<PayrollEntry>
    ): Form910Summary {
        val totalIncome = income1H + income2H
        val tax910 = calculate910Tax(totalIncome)

        val employeeIncome  = payrollEntries.sumOf { it.grossIncome }
        val employeeIpn     = payrollEntries.sumOf { it.ipn }
        val employeeSn      = payrollEntries.sumOf { it.sn }
        val employeeOpv     = payrollEntries.sumOf { it.opv }
        val employeeOsms    = payrollEntries.sumOf { it.osms }
        val employeeSo      = payrollEntries.sumOf { it.so }
        val employeeVosms   = payrollEntries.sumOf { it.vosms }

        return Form910Summary(
            f001 = income1H,
            f002 = income2H,
            f003 = totalIncome,
            f004 = tax910.totalTax,
            f004_1 = tax910.ipn,
            f004_2 = tax910.sn,
            f005 = employeeIncome,
            f006 = employeeIpn,
            f007 = employeeSn,
            f008 = employeeOpv,
            f009 = employeeOsms,
            f010 = employeeSo,
            f011 = employeeVosms
        )
    }

    private fun BigDecimal.round2() = setScale(2, RoundingMode.HALF_UP)
    private fun List<BigDecimal>.sum() = fold(BigDecimal.ZERO) { a, b -> a + b }
    private fun <T> List<T>.sumOf(selector: (T) -> BigDecimal): BigDecimal =
        fold(BigDecimal.ZERO) { a, b -> a + selector(b) }
}

private data class MonthCalc(
    val gross: BigDecimal, val opv: BigDecimal, val osms: BigDecimal,
    val ipn: BigDecimal, val so: BigDecimal, val vosms: BigDecimal,
    val sn: BigDecimal, val net: BigDecimal
)

data class EmployeeTaxResult(
    val grossIncome: BigDecimal,
    val opv: BigDecimal,
    val osms: BigDecimal,
    val ipn: BigDecimal,
    val so: BigDecimal,
    val vosms: BigDecimal,
    val sn: BigDecimal,
    val netIncome: BigDecimal
) {
    val totalWithheld: BigDecimal get() = opv + osms + ipn
    val totalEmployerCost: BigDecimal get() = so + vosms + sn
}

data class Tax910Result(
    val income: BigDecimal,
    val totalTax: BigDecimal,
    val ipn: BigDecimal,
    val sn: BigDecimal
)

data class Form910Summary(
    val f001: BigDecimal,   // Доход I полугодие
    val f002: BigDecimal,   // Доход II полугодие
    val f003: BigDecimal,   // Совокупный доход
    val f004: BigDecimal,   // Исчисленный налог 3%
    val f004_1: BigDecimal, // ИПН 1.5%
    val f004_2: BigDecimal, // Социальный налог 1.5%
    val f005: BigDecimal,   // Доход работников
    val f006: BigDecimal,   // ИПН работников
    val f007: BigDecimal,   // Социальный налог работодатель
    val f008: BigDecimal,   // ОПВ
    val f009: BigDecimal,   // ООСМС
    val f010: BigDecimal,   // СО
    val f011: BigDecimal    // ВОСМС
)
