# Proxu

<p align="center">
  <img src="proxu/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="120" alt="Proxu Logo">
</p>

<p align="center">
  <a href="https://developer.android.com/about/versions/lollipop"><img src="https://img.shields.io/badge/API-24%2B-yellow.svg?style=flat" alt="API"></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.3.10-blue.svg" alt="Kotlin"></a>
  <a href="https://github.com/ch32v003-cloud/proxu/commits/master"><img src="https://img.shields.io/github/commit-activity/m/ch32v003-cloud/proxu" alt="GitHub commit activity"></a>
  <a href="https://github.com/ch32v003-cloud/proxu/releases"><img src="https://img.shields.io/github/downloads/ch32v003-cloud/proxu/latest/total?logo=github" alt="GitHub Releases"></a>
</p>

**Proxu** — приватный Android-клиент VPN на базе [v2rayNG](https://github.com/2dust/v2rayNG), интегрированный с облачным сервисом [proxu.pro](https://proxu.pro). Поддерживает Xray core и v2fly core.

## Возможности

### Основные
- **Google Авторизация** — вход через Google Sign-In, автоматическая регистрация новых пользователей с балансом 50₽
- **Синхронизация VPN-профилей** — автоматический импорт профилей из proxu.pro при логине
- **Баланс и оплата** — отображение баланса в toolbar, пополнение через ЮKassa (карта, СБП)
- **Автоматическое создание VPN** — создание нового VPN-профиля через API proxu.pro с выбором сервера и inbound
- **История транзакций** — просмотр истории операций (пополнения, списания)
- **Светлая и тёмная темы** — Material3 DayNight с адаптивными цветами
- **Easter Egg** — 10 кликов по "Account" в меню открывают расширенные функции

### Технические
- Поддержка VLESS, VMess, Trojan, Shadowsocks, Hysteria2
- Реальное время соединения (real ping)
- Тестирование всех конфигураций
- Импорт/экспорт конфигураций
- Работа в фоне с уведомлением
- Поддержка F-Droid (скрытый Google Sign-In если нет Play Services)

## Скриншоты

*(добавить скриншоты экрана входа, главного экрана, пополнения баланса, истории транзакций)*

## API Интеграция

Приложение взаимодействует с сервером `proxu.pro`:

| Действие | Метод | Endpoint |
|----------|-------|----------|
| Авторизация Google | POST | `/api/public/auth/google/api` |
| Профиль пользователя | GET | `/api/user/profile` |
| Список VPN-серверов | GET | `/api/user/vpn-servers` |
| Inbounds сервера | GET | `/api/user/vpn-inbounds/{id}` |
| Создание VPN | POST | `/api/user/proxies` (type: "vpn") |
| Список прокси | GET | `/api/user/proxies` |
| История транзакций | GET | `/api/user/transactions` |
| Создание платежа | POST | `/api/user/payments/create` |
| Статус платежа | GET | `/api/user/payments/{id}/status` |

## Архитектура

```
Proxu/
├── proxu/app/src/main/java/com/proxu/app/
│   ├── auth/           # Авторизация и API
│   │   ├── ProxuAuthApiService.kt      # Google auth
│   │   ├── ProxuApiService.kt          # API proxu.pro
│   │   ├── ProxuProfileSync.kt         # Синхронизация профилей
│   │   ├── ProxuLoginActivity.kt       # Экран входа Google
│   │   └── ProxuWebLoginActivity.kt    # WebView вход
│   ├── ui/             # UI экраны
│   │   ├── MainActivity.kt             # Главный экран
│   │   ├── TransactionHistoryActivity.kt # История транзакций
│   │   └── PaymentBottomSheetDialog.kt  # Пополнение баланса
│   └── handler/        # Управление данными
│       └── MmkvManager.kt            # Локальное хранилище
└── AndroidLibXrayLite/  # v2ray/Xray core (Go)
```

## Сборка

### Требования
- Android Studio Arctic Fox или новее
- JDK 17
- Android SDK API 24+
- Kotlin 2.3.10

### Сборка APK

```bash
cd proxu
./gradlew assemblePlaystoreDebug   # Debug версия
./gradlew assemblePlaystoreRelease  # Release версия
```

APK файлы будут в `proxu/app/build/outputs/apk/`.

### Сборка с обновленным core

Если нужно обновить v2ray/Xray core:

```bash
cd AndroidLibXrayLite
make  # или gradlew build
```

## Разработка

### Geoip и Geosite
- Файлы `geoip.dat` и `geosite.dat` находятся в `Android/data/com.proxu.app/files/assets`
- Автоматическая загрузка из [Loyalsoldier/v2ray-rules-dat](https://github.com/Loyalsentinel/v2ray-rules-dat)
- Возможен ручной импорт сторонних dat-файлов

### Обновление из upstream (v2rayNG)

```bash
# Получить изменения из оригинального репозитория
git fetch upstream

# Просмотреть новые коммиты
git log --oneline HEAD..upstream/master

# Применить конкретный коммит
git cherry-pick <commit-hash>
```

Подробнее в [UPSTREAM.md](UPSTREAM.md).

### Запуск на эмуляторе

Для WSA (Windows Subsystem for Android) нужно выдать разрешение VPN:
```bash
adb shell appops set com.proxu.app ACTIVATE_VPN allow
```

## Конфигурация

### Shop ID ЮKassa
Shop ID и API-ключ настраиваются в `ProxuPaymentBottomSheetDialog.kt` (для production заменить на реальные).

### Google Web Client ID
Настраивается в `res/values/strings.xml`:
```xml
<string name="google_web_client_id">YOUR_CLIENT_ID.apps.googleusercontent.com</string>
```

### API Base URL
По умолчанию: `https://proxu.pro`. Меняется в `ProxuApiService.kt` и `ProxuAuthApiService.kt`.

## Easter Egg / Скрытое меню

Для обычных пользователей интерфейс упрощён:
- **Расширенное меню** (добавление конфигов, пинг всех серверов, настройки маршрутизации) скрыто
- **Кнопка "Создать профиль"** скрыта
- **Кнопка "Пополнить баланс"** всегда видна при логине

Чтобы открыть расширенное меню: нажмите **10 раз** на пункт **"Account"** в боковом меню. Повторные 10 кликов скрывают меню обратно.

## Лицензия

[GPLv3](LICENSE)

## Благодарности

- Оригинальный проект: [v2rayNG](https://github.com/2dust/v2rayNG) by [2dust](https://github.com/2dust)
- Xray core: [XTLS/Xray-core](https://github.com/XTLS/Xray-core)
- v2fly core: [v2fly/v2ray-core](https://github.com/v2fly/v2ray-core)
- Loyalsoldier rules: [v2ray-rules-dat](https://github.com/Loyalsoldier/v2ray-rules-dat)