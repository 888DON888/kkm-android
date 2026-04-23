package kz.kkm.tax

import kz.kkm.domain.model.Organization
import kz.kkm.domain.model.PayrollEntry
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.io.StringWriter
import java.math.BigDecimal
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Builds the XML file for form 910.00 per KGD MF RK schema.
 * Schema: https://cabinet.salyk.kz/xsd/910.00.xsd
 */
@Singleton
class XmlFormBuilder @Inject constructor() {

    fun build910Xml(
        organization: Organization,
        year: Int,
        half: Int,
        summary: Form910Summary,
        payrollEntries: List<PayrollEntry>,
        correctionNumber: Int = 0
    ): String {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.newDocument()

        val root = doc.createElement("Deklaracia").apply {
            setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance")
            setAttribute("xsi:noNamespaceSchemaLocation", "910.00.xsd")
        }
        doc.appendChild(root)

        // Header
        root.appendElement(doc, "FormCode", "910.00")
        root.appendElement(doc, "FormVersion", "1")
        root.appendElement(doc, "KNP", if (correctionNumber == 0) "1" else "2") // 1=первичная, 2=корректировка
        root.appendElement(doc, "CorrectionNumber", correctionNumber.toString())

        // Taxpayer
        val taxpayer = doc.createElement("Nalogoplatelshik")
        root.appendChild(taxpayer)
        val isOrg = organization.bin.length == 12 && organization.bin.take(6).all { it.isDigit() }
        if (isOrg) {
            taxpayer.appendElement(doc, "BIN", organization.bin)
        } else {
            taxpayer.appendElement(doc, "IIN", organization.bin)
        }
        taxpayer.appendElement(doc, "NameRu", organization.name)
        if (organization.ntin.isNotEmpty()) {
            taxpayer.appendElement(doc, "NTIN", organization.ntin)
        }

        // Period
        val period = doc.createElement("Period")
        root.appendChild(period)
        period.appendElement(doc, "Year", year.toString())
        period.appendElement(doc, "HalfYear", half.toString())

        // Tax authority
        root.appendElement(doc, "OGD", "")  // Код органа госдоходов (заполняется при подаче)

        // Income fields
        root.appendDecimalField(doc, "F910_00_001", summary.f001)
        root.appendDecimalField(doc, "F910_00_002", summary.f002)
        root.appendDecimalField(doc, "F910_00_003", summary.f003)
        root.appendDecimalField(doc, "F910_00_004", summary.f004)
        root.appendDecimalField(doc, "F910_00_004_1", summary.f004_1)
        root.appendDecimalField(doc, "F910_00_004_2", summary.f004_2)

        // Employees section
        if (payrollEntries.isNotEmpty()) {
            root.appendDecimalField(doc, "F910_00_005", summary.f005)
            root.appendDecimalField(doc, "F910_00_006", summary.f006)
            root.appendDecimalField(doc, "F910_00_007", summary.f007)
            root.appendDecimalField(doc, "F910_00_008", summary.f008)
            root.appendDecimalField(doc, "F910_00_009", summary.f009)
            root.appendDecimalField(doc, "F910_00_010", summary.f010)
            root.appendDecimalField(doc, "F910_00_011", summary.f011)

            val employeesEl = doc.createElement("Employees")
            root.appendChild(employeesEl)

            payrollEntries.forEachIndexed { index, entry ->
                val emp = doc.createElement("Employee")
                employeesEl.appendChild(emp)
                emp.appendElement(doc, "RowNumber", (index + 1).toString())
                emp.appendElement(doc, "IIN", "")        // IIN decrypted at sign time
                emp.appendElement(doc, "FullName", entry.employeeName)
                emp.appendDecimalField(doc, "Income", entry.grossIncome)
                emp.appendDecimalField(doc, "OPV", entry.opv)
                emp.appendDecimalField(doc, "IPN", entry.ipn)
                emp.appendDecimalField(doc, "SN", entry.sn)
                emp.appendDecimalField(doc, "SO", entry.so)
                emp.appendDecimalField(doc, "OSMS", entry.osms)
                emp.appendDecimalField(doc, "VOSMS", entry.vosms)
            }
        }

        return docToString(doc)
    }

    fun sha256(xmlContent: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(xmlContent.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun saveToFile(xmlContent: String, outputDir: File, year: Int, half: Int): File {
        outputDir.mkdirs()
        val file = File(outputDir, "910_00_${year}_${half}_${System.currentTimeMillis()}.xml")
        file.writeText(xmlContent, Charsets.UTF_8)
        return file
    }

    private fun docToString(doc: Document): String {
        val transformer = TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
        }
        val writer = StringWriter()
        transformer.transform(DOMSource(doc), StreamResult(writer))
        return writer.toString()
    }

    private fun Element.appendElement(doc: Document, tag: String, value: String) {
        val el = doc.createElement(tag)
        el.textContent = value
        appendChild(el)
    }

    private fun Element.appendDecimalField(doc: Document, tag: String, value: BigDecimal) {
        val el = doc.createElement(tag)
        el.setAttribute("val", value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString())
        appendChild(el)
    }
}
