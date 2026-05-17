# Обновление proxu из upstream v2rayNG

## Текущая ситуация

- **Fork:** https://github.com/ch32v003-cloud/proxu
- **Upstream:** https://github.com/2dust/v2rayNG
- **Текущая обновлённая база:** upstream `master` / v2rayNG `2.1.7`
- **Локальный package:** `com.proxu.app`
- **Android-проект:** `proxu/`

## Рекомендуемый процесс обновления

Из-за полного rebrand (`V2rayNG` -> `proxu`, `com.v2ray.ang` -> `com.proxu.app`) прямой merge upstream в `master` может дать много rename/package конфликтов. Безопаснее обновляться в отдельной рабочей ветке от свежего upstream и заново накладывать proxu-адаптацию.

### 1. Получить свежий upstream

```bash
git fetch upstream --prune --tags
```

Если fetch падает на TLS, можно повторить через HTTP/1.1:

```bash
git -c http.version=HTTP/1.1 fetch upstream --prune --tags
```

### 2. Создать рабочую ветку от upstream

```bash
git switch -c proxu-<version>-rebrand upstream/master
```

Пример:

```bash
git switch -c proxu-2.1.7-rebrand upstream/master
```

### 3. Наложить rebrand

Минимально проверить и адаптировать:

- папка проекта: `V2rayNG/` -> `proxu/`
- `rootProject.name = "proxu"`
- `namespace = "com.proxu.app"`
- `applicationId = "com.proxu.app"`
- Kotlin/Java package: `com.v2ray.ang` -> `com.proxu.app`
- application class: `AngApplication` -> `ProxuApplication`
- daemon/process names: `RunSoLibV2RayDaemon` -> `RunProxuDaemon`
- URL scheme: `v2rayng://` -> `proxu://`
- APK output prefix: `v2rayNG_` -> `proxu_`
- CI paths: `V2rayNG` -> `proxu`
- app label/resources/docs that should show `proxu`

### 4. Обновить core/native компоненты

- `AndroidLibXrayLite` должен соответствовать upstream-релизу v2rayNG.
- Для v2rayNG `2.1.7` используется `AndroidLibXrayLite v26.5.9` / Xray-core `v26.5.9`.
- `proxu/app/libs/libv2ray.aar` можно скачать из релиза AndroidLibXrayLite или через CI.
- `libhev-socks5-tunnel.so` нужно пересобрать под package `com/proxu/app/service`.

```bash
export NDK_HOME=/path/to/android-sdk/ndk/<version>
./compile-hevtun.sh
```

После сборки убедиться, что `.so` попали в APK для всех ABI.

### 5. Проверить важные файлы

```bash
git grep -n -E 'com\.v2ray\.ang|v2rayng://|RunSoLibV2RayDaemon|AngApplication|V2rayNG' -- . ':!AndroidLibXrayLite' ':!hev-socks5-tunnel'
```

Допустимы только намеренные upstream-документальные ссылки, например README с описанием исходного имени или ссылки на upstream releases.

### 6. Собрать и протестировать

```bash
cd proxu
./gradlew testPlaystoreDebugUnitTest
./gradlew assembleDebug
```

Дополнительно проверить APK:

```bash
/home/lvs/Android/Sdk/build-tools/36.0.0/aapt dump badging app/build/outputs/apk/playstore/debug/proxu_2.1.7_arm64-v8a.apk
unzip -l app/build/outputs/apk/playstore/debug/proxu_2.1.7_arm64-v8a.apk | grep -E 'lib/arm64-v8a/(libgojni|libhev-socks5-tunnel)\.so'
```

### 7. Перед коммитом

```bash
git diff --check
git status -sb
```

Не добавлять случайные локальные/untracked файлы без явного решения.

## Почему не прямой merge

Прямой merge старого `master` с `upstream/master` может ломаться на:

- `rename/rename` конфликтах после upstream-переносов DTO/package;
- `removed in local` из-за переименования `V2rayNG/` в `proxu/`;
- повторных конфликтах в `AndroidManifest.xml`, `build.gradle.kts`, resources и package imports.

Для крупных upstream обновлений проще и безопаснее брать чистый upstream как базу и заново применять небольшой, проверяемый слой rebrand/Vision/custom native packaging.

## Что проверять вручную на устройстве

- запуск приложения;
- запуск VPN;
- наличие и загрузку `libhev-socks5-tunnel.so`;
- импорт/редактирование профилей;
- proxy-chain;
- dynamic local SOCKS port;
- local SOCKS username/password;
- IPv6 options;
- Browser Dialer;
- Vision dark glass UI.

## Важно

- Не пушить без явного решения.
- Не коммитить секреты, keystore или `.env`.
- Не коммитить случайные design docs/untracked файлы без согласия.
- Release APK требует signing config/keystore; debug APK подходит только для локальной проверки.
