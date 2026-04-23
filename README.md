# KKM — Онлайн касса для Android (Казахстан, стандарт 2.0.3)

## Структура проекта

```
kkm-android/
├── app/
│   ├── build.gradle.kts              # Зависимости, buildConfig
│   ├── proguard-rules.pro            # Правила обфускации
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/kz/kkm/
│       │   ├── KkmApplication.kt             # Hilt + SQLCipher init
│       │   ├── data/
│       │   │   ├── local/
│       │   │   │   ├── KkmDatabase.kt        # Room + SQLCipher AES-256
│       │   │   │   ├── dao/Daos.kt           # ShiftDao, ReceiptDao, CatalogDao, EmployeeDao, TaxPeriodDao
│       │   │   │   └── entity/Entities.kt    # Все Room-сущности + TypeConverters
│       │   │   ├── remote/
│       │   │   │   └── ApiServices.kt        # OfdApiService + IsnaApiService (Retrofit)
│       │   │   ├── repository/
│       │   │   │   ├── Repositories.kt       # ShiftRepository + ReceiptRepository
│       │   │   │   └── SettingsRepository.kt # DataStore + PIN hash
│       │   │   └── sync/
│       │   │       └── OfdSyncWorker.kt      # WorkManager фоновая синхронизация с ОФД
│       │   ├── di/
│       │   │   └── Modules.kt               # Hilt DI (DB, Network, App)
│       │   ├── domain/
│       │   │   └── model/Models.kt          # Shift, Receipt, Employee, TaxPeriod, Reports
│       │   ├── fiscal/
│       │   │   └── FiscalModule.kt          # FiscalManager (ФП), TlvBuilder, QrCodeGenerator
│       │   ├── tax/
│       │   │   ├── TaxCalculator.kt         # ОПВ/ООСМС/ИПН/СО/ВОСМС/СН + 910.00
│       │   │   └── XmlFormBuilder.kt        # XML формы 910.00 по XSD КГД
│       │   └── ui/
│       │       ├── MainActivity.kt          # FragmentActivity + EdgeToEdge
│       │       ├── Navigation.kt            # Routes + KkmNavHost
│       │       ├── auth/
│       │       │   ├── AuthScreen.kt        # PIN + биометрия
│       │       │   └── AuthViewModel.kt
│       │       ├── main/
│       │       │   ├── MainCashScreen.kt    # Хост (Main→Payment→Done)
│       │       │   ├── MainScreen.kt        # Основной экран кассы
│       │       │   └── MainViewModel.kt     # Корзина, поиск, смены
│       │       ├── receipt/
│       │       │   ├── PaymentScreen.kt     # Оплата (наличные/карта/смешанная)
│       │       │   ├── ReceiptDoneScreen.kt # Готовый чек + QR
│       │       │   └── ReceiptDetailScreen.kt # Детали чека из журнала
│       │       ├── shift/
│       │       │   └── ShiftScreen.kt       # Открытие/закрытие смены, X/Z-отчёты
│       │       ├── returns/
│       │       │   └── ReturnsScreen.kt     # Возврат товара
│       │       ├── catalog/
│       │       │   └── CatalogScreen.kt     # Каталог + штрихкод + НКТ
│       │       ├── reports/
│       │       │   └── ReportScreens.kt     # Журнал операций
│       │       ├── tax910/
│       │       │   └── Tax910Screens.kt     # 4-шаговый мастер 910.00
│       │       ├── settings/
│       │       │   └── SettingsScreen.kt    # Настройки организации + ОФД
│       │       └── theme/
│       │           └── Theme.kt             # Material 3 цвета + типографика
│       └── res/
│           ├── values/strings.xml           # Русский язык
│           ├── values-kk/strings.xml        # Казахский язык
│           ├── values/themes.xml
│           └── xml/
│               ├── network_security_config.xml
│               ├── backup_rules.xml
│               └── data_extraction_rules.xml
├── gradle/
│   └── libs.versions.toml               # Version catalog
├── build.gradle.kts
└── settings.gradle.kts
```

## Быстрый старт

### 1. Требования
- Android Studio Hedgehog (2023.1.1) или новее
- JDK 17
- Android SDK 34 (compileSdk)
- Минимальный SDK: 24 (Android 7.0)

### 2. Открыть проект
```bash
# Клонировать или распаковать архив
cd kkm-android
# Открыть в Android Studio: File → Open → выбрать папку kkm-android
```

### 3. Конфигурация перед сборкой

#### 3.1 Настройки ОФД и ИСНА — `app/build.gradle.kts`
```kotlin
// В buildTypes.release:
buildConfigField("String", "OFD_BASE_URL", "\"https://ваш-офд.kz/api/v2/\"")
buildConfigField("String", "ISNA_BASE_URL", "\"https://is.kgd.gov.kz/api/v1/\"")
// Получить SHA-256 пины сертификатов:
// openssl s_client -connect ofd.kgd.gov.kz:443 | openssl x509 -pubkey -noout | \
//   openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64
buildConfigField("String", "OFD_PIN_SHA256", "\"sha256/HASH=\"")
buildConfigField("String", "ISNA_PIN_SHA256", "\"sha256/HASH=\"")
```

#### 3.2 Первый запуск в приложении
1. Создать PIN-код (минимум 4 цифры)
2. Перейти в **Настройки** и заполнить:
   - БИН / ИИН организации
   - Наименование и адрес
   - URL и токен ОФД
   - Регистрационный номер машины (РНМ) из КГД
3. Открыть смену

### 4. Сборка
```bash
# Debug APK
./gradlew assembleDebug

# Release APK (требует keystore)
./gradlew assembleRelease
```

---

## Ключевые технические решения

| Задача | Решение |
|--------|---------|
| Шифрование БД | SQLCipher AES-256, ключ в Android Keystore |
| Фискальный признак | HMAC-SHA256 (замените на ГОСТ НУЦ РК в prod) |
| Передача в ОФД | Retrofit + TLV-пакет (протокол 2.0.3) |
| Фоновая синхронизация | WorkManager, повтор при сбое |
| Декларация 910.00 | Авто-агрегация из чеков + XmlFormBuilder |
| Подписание XML | NCALayer SDK (облачная ЭЦП НУЦ РК) |
| Локализация | values/strings.xml (RU) + values-kk/strings.xml (KK) |
| Безопасность | Certificate Pinning, FLAG_SECURE, root-detection |

---

## Что нужно доработать для production

1. **ЭЦП НУЦ РК** — заменить HMAC в `FiscalManager.generateFiscalSign()` на ГОСТ через NCALayer Mobile SDK
2. **Сертификат-пиннинг** — вставить реальные SHA-256 хэши сертификатов ОФД и ИСНА в `build.gradle.kts`
3. **NKT интеграция** — добавить API-клиент Национального каталога товаров в `CatalogScreen`
4. **Регистрация в КГД** — пройти процедуру включения в реестр ККМ
5. **Токен ИСНА** — получить OAuth 2.0 client_credentials для API ИСНА
6. **Тестовая среда ОФД** — провести тестовую передачу 100 чеков
7. **Keystore для релиза** — создать и подключить Android keystore для подписи APK

---

## Файлы конфигурации для замены

| Файл | Что изменить |
|------|-------------|
| `app/build.gradle.kts` | OFD_BASE_URL, ISNA_BASE_URL, пины сертификатов |
| `fiscal/FiscalModule.kt` | `generateFiscalSign()` → NCALayer GOST |
| `data/remote/ApiServices.kt` | Уточнить endpoint'ы конкретного ОФД |
| `res/mipmap-*/` | Добавить иконку приложения |

---

*Соответствует: НК РК, Приказ МФ № 208 (2018), Приказ МФ № 808 (2019), Протокол ОФД 2.0.3*
