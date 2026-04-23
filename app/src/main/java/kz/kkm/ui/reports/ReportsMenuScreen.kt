package kz.kkm.ui.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kz.kkm.ui.theme.*

/**
 * Entry-point screen that lists available reports.
 * Actual X/Z-report rendering is in XReportScreen / ZReportScreen (ReportScreens.kt).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsMenuScreen(
    onOpenXReport: () -> Unit,
    onOpenZReport: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Отчёты") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ReportCard(
                icon  = Icons.Default.Assessment,
                title = "X-Отчёт",
                desc  = "Промежуточный отчёт без закрытия смены",
                color = KkmBlueLight,
                onClick = onOpenXReport
            )
            ReportCard(
                icon  = Icons.Default.TaskAlt,
                title = "Z-Отчёт / Закрытие смены",
                desc  = "Сформировать итоговый отчёт и закрыть смену",
                color = KkmRed,
                onClick = onOpenZReport
            )
        }
    }
}

@Composable
private fun ReportCard(
    icon: ImageVector, title: String, desc: String,
    color: Color, onClick: () -> Unit
) {
    Card(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(shape = RoundedCornerShape(10.dp), color = color.copy(alpha = .15f)) {
                Icon(icon, null, tint = color, modifier = Modifier.padding(10.dp).size(28.dp))
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = KkmGray)
            }
        }
    }
}
