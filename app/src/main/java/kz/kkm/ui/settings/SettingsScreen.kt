package kz.kkm.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kz.kkm.data.repository.SettingsRepository
import kz.kkm.domain.model.Organization
import kz.kkm.ui.theme.KkmBlue
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    val org = settingsRepo.observeOrganization()
        .stateIn(viewModelScope, SharingStarted.Lazily, Organization("", name = "", address = ""))

    private val _saved = MutableStateFlow(false)
    val saved = _saved.asStateFlow()

    fun save(org: Organization, rnm: String, language: String) {
        viewModelScope.launch {
            settingsRepo.saveOrganization(org)
            settingsRepo.saveRnm(rnm)
            settingsRepo.saveLanguage(language)
            _saved.value = true
            kotlinx.coroutines.delay(2000)
            _saved.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val org by viewModel.org.collectAsState()
    val saved by viewModel.saved.collectAsState()

    var bin by remember(org.bin) { mutableStateOf(org.bin) }
    var ntin by remember(org.ntin) { mutableStateOf(org.ntin) }
    var name by remember(org.name) { mutableStateOf(org.name) }
    var address by remember(org.address) { mutableStateOf(org.address) }
    var isVat by remember(org.isVatPayer) { mutableStateOf(org.isVatPayer) }
    var ofdUrl by remember(org.ofdUrl) { mutableStateOf(org.ofdUrl) }
    var ofdToken by remember(org.ofdToken) { mutableStateOf(org.ofdToken) }
    var rnm by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("ru") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = KkmBlue,
                    titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SettingsSection("Организация") {
                SettingsTextField(bin, { bin = it }, "БИН / ИИН*")
                SettingsTextField(ntin, { ntin = it }, "NTIN (при наличии)")
                SettingsTextField(name, { name = it }, "Наименование организации*")
                SettingsTextField(address, { address = it }, "Адрес торговой точки*")
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = isVat, onCheckedChange = { isVat = it })
                    Text("Плательщик НДС (12%)")
                }
                SettingsTextField(rnm, { rnm = it }, "РНМ (рег. номер ККМ)")
            }

            SettingsSection("ОФД") {
                SettingsTextField(ofdUrl, { ofdUrl = it }, "URL ОФД")
                SettingsTextField(ofdToken, { ofdToken = it }, "Токен ОФД")
            }

            SettingsSection("Интерфейс") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(language == "ru", { language = "ru" }, { Text("Русский") })
                    FilterChip(language == "kk", { language = "kk" }, { Text("Қазақша") })
                }
            }

            if (saved) {
                Card(colors = CardDefaults.cardColors(containerColor = kz.kkm.ui.theme.KkmGreen.copy(0.1f)),
                    modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = kz.kkm.ui.theme.KkmGreen)
                        Spacer(Modifier.width(8.dp))
                        Text("Настройки сохранены", color = kz.kkm.ui.theme.KkmGreen)
                    }
                }
            }

            Button(
                onClick = { viewModel.save(
                    Organization(bin = bin, ntin = ntin, name = name, address = address,
                        isVatPayer = isVat, ofdUrl = ofdUrl, ofdToken = ofdToken),
                    rnm, language)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Сохранить настройки") }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, fontWeight = FontWeight.Bold, color = KkmBlue,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

@Composable
private fun SettingsTextField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(value = value, onValueChange = onValueChange,
        label = { Text(label) }, singleLine = true,
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp))
}
