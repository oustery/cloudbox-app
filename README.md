# 📦 CloudBox — Cloud Mail.ru Client & Downloader for Android

<p align="center">
  <img src="app/src/main/res/drawable/ic_launcher_foreground.xml" width="128" height="128" alt="CloudBox Logo" />
</p>

<p align="center">
  <strong>Современный Android-клиент для просмотра медиафайлов, навигации по папкам и быстрой загрузки контента с публичных ссылок Облака Mail.ru.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android_8.0+_(API_26+)-3DDC84?style=flat&logo=android" alt="Android Platform" />
  <img src="https://img.shields.io/badge/Language-Kotlin_2.2-7F52FF?style=flat&logo=kotlin" alt="Kotlin" />
  <img src="https://img.shields.io/badge/UI-Jetpack_Compose_M3-4285F4?style=flat&logo=jetpackcompose" alt="Compose M3" />
  <img src="https://img.shields.io/badge/Architecture-Clean_%2F_MVVM-blue?style=flat" alt="Architecture" />
  <img src="https://img.shields.io/badge/CI%2FCD-GitHub_Actions-2088FF?style=flat&logo=githubactions" alt="CI/CD" />
  <img src="https://img.shields.io/badge/License-MIT-green?style=flat" alt="License" />
</p>

---

## 🚀 О проекте

**CloudBox** решает проблему неудобного просмотра и скачивания контента с общедоступных ссылок сервиса Облако Mail.ru (`https://cloud.mail.ru/public/...`). 

Вместо медленного веб-интерфейса в браузере с ограничениями, приложение обращается напрямую к сетевым диспетчерам сервиса, предоставляет нативную навигацию по вложенным папкам, встроенный зумируемый просмотрщик фото, потоковый видеоплеер и полноценный фоновый менеджер загрузок с отображением скорости в реальном времени.

---

## ✨ Ключевые возможности

### 🔗 Умная работа с ссылками
- Автоматическое распознавание любых вариаций публичных ссылок:
  - `https://cloud.mail.ru/public/KVy7/S1hRRPVUE`
  - `cloud.mail.ru/public/KVy7/S1hRRPVUE/subfolder`
  - Короткие идентификаторы `KVy7/S1hRRPVUE`
- Быстрая вставка из буфера обмена в один клик.
- Поддержка системного меню **«Поделиться» (Share)**: открывайте ссылки прямо из Telegram, WhatsApp, браузера или заметок.
- Автоматическое сохранение посещенных папок в локальную библиотеку (Room Database) с избранным, категориями и поиском.

### 📁 Проводник файлов (Folder Explorer)
- Навигация по подпапкам с интерактивной цепочкой «хлебных крошек» (Breadcrumbs).
- Два режима отображения:
  - **Адаптивная сетка (Grid)** с крупными карточками превью для фото и видео.
  - **Детальный список (List)** с размерами, датами изменения и метаданными.
- Мгновенный поиск файлов по имени и фильтрация по типам (Фото, Видео, Документы, Архивы).
- Режим пакетного выбора (**Batch Selection**) с загрузкой всей папки или выбранных элементов в одно касание.

### 🖼️ Встроенная медиагалерея и плеер
- **Полноэкранный просмотрщик фото**:
  - Высокое разрешение превью (до 1024px и исходники).
  - Поддержка жестов масштабирования **Pinch-to-Zoom** (до 5x), панорамирование (Pan) и двойной тап для быстрого зума.
  - Кнопка мгновенного сброса масштаба.
  - Листание соседних медиафайлов в текущей папке.
- **Встроенный потоковый видеоплеер**:
  - Прямое воспроизведение видеопотока через диспетчер серверов без необходимости предварительного скачивания.
  - Управление воспроизведением, перемотка, таймкоды и автоповтор.

### ⚡ Менеджер загрузок
- Фоновая загрузка с контролем потока и расчетом скорости (МБ/с, КБ/с).
- Сохранение файлов напрямую в системную папку `Downloads/CloudBox` с немедленным появлением в галерее и файловом менеджере устройства.
- Очередь загрузок: пауза, возобновление (HTTP Range), отмена и повтор при сбое связи.
- Быстрое открытие скачанных файлов через системный `FileProvider`.

---

## 🔬 Архитектура и реверс-инжиниринг API

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          Jetpack Compose M3 UI                              │
│   ┌────────────────────┐ ┌────────────────────────┐ ┌────────────────────┐  │
│   │     HomeScreen     │ │  FolderExplorerScreen  │ │ MediaViewerScreen  │  │
│   └─────────┬──────────┘ └───────────┬────────────┘ └─────────┬──────────┘  │
│             │                        │                        │             │
│   ┌─────────▼────────────────────────▼────────────────────────▼──────────┐  │
│   │                 DownloadsScreen & FileItemCard                       │  │
│   └──────────────────────────────────┬───────────────────────────────────┘  │
└──────────────────────────────────────┼──────────────────────────────────────┘
                                       │
┌──────────────────────────────────────▼──────────────────────────────────────┐
│                            ViewModels Layer                                 │
│   ┌────────────────────────────────────┐ ┌────────────────────────────────┐ │
│   │         CloudLinkViewModel         │ │        DownloadViewModel       │ │
│   └─────────────────┬──────────────────┘ └────────────────┬───────────────┘ │
└─────────────────────┼─────────────────────────────────────┼─────────────────┘
                      │                                     │
┌─────────────────────▼─────────────────────────────────────▼─────────────────┐
│                            Repository Layer                                 │
│   ┌────────────────────────────────────┐ ┌────────────────────────────────┐ │
│   │       CloudMailRuRepository        │ │       DownloadRepository       │ │
│   └─────────────────┬──────────────────┘ └────────────────┬───────────────┘ │
└─────────────────────┼─────────────────────────────────────┼─────────────────┘
                      │                                     │
        ┌─────────────┴─────────────┐         ┌─────────────┴─────────────┐
        │                           │         │                           │
┌───────▼──────────────┐   ┌────────▼───────┐ │ ┌───────────────────────┐ │
│ CloudMailRuApi (V2)  │   │ Dispatcher     │ │ │ OkHttp Stream Engine  │ │
│ - /api/v2/folder     │   │ & Shard Router │ │ │ Range-request / Media │ │
└──────────────────────┘   └────────────────┘ │ └───────────────────────┘ │
                                              │                           │
┌─────────────────────────────────────────────┴───────────────────────────┴───┐
│                    Room Database (Local Persistence)                        │
│   - SavedLinkDao (избранные папки, категории, превью)                       │
│   - DownloadDao (очередь, прогресс, статусы, пути к файлам)                 │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Протокол взаимодействия с Cloud Mail.ru

1. **Извлечение Weblink-ключа**:
   Из ссылки `https://cloud.mail.ru/public/KVy7/S1hRRPVUE` выделяется ключ `KVy7/S1hRRPVUE`.
2. **Маршрутизация серверов (`Dispatcher API`)**:
   - Запрос: `GET https://cloud.mail.ru/api/v2/dispatcher`
   - Разрешает актуальные пулы хостов:
     - `weblink_get` → базовый адрес скачивания файлов (`https://clocloXX.cloud.mail.ru/weblink/get/`).
     - `weblink_thumbnails` → генератор превью любого разрешения (`https://thumb.cloud.mail.ru/weblink/thumb/{size}/`).
     - `weblink_view` → стриминг видео и аудио.
3. **Листинг папки (`Folder API`)**:
   - Запрос: `GET https://cloud.mail.ru/api/v2/folder?weblink={key}&offset=0&limit=1000&api=2`
   - Возвращает JSON-дерево со списком файлов (`kind: "file"`), папок (`kind: "folder"`), размерами и типами контента.
4. **Fallback-механизм**:
   - При нестандартных ссылках или сбоях API активируется HTML-скрапер, извлекающий состояние объекта из мета-тегов и клиентских скриптов страницы.

---

## 🛠️ Стек технологий

| Компонент | Технология |
|---|---|
| **Язык** | Kotlin 2.2 |
| **UI Framework** | Jetpack Compose + Material Design 3 |
| **Иконки** | Material Icons Extended (Adaptive Vector + Themed Icons) |
| **Загрузка изображений** | Coil Compose 2.7 |
| **Сетевой слой** | Retrofit 2.12 + OkHttp 4.10 + Moshi Kotlin Codegen |
| **Локальная БД** | Room 2.7.0 (KSP, Flow, Coroutines) |
| **Многопоточность** | Kotlin Coroutines (`StateFlow`, `SupervisorJob`, `Dispatchers.IO`) |
| **Файлы & Шаринг** | AndroidX Core `FileProvider`, `MediaStore` |
| **Тестирование** | JUnit 4, Robolectric 4.16, Compose UI Testing |

---

## 🏗️ Сборка и запуск проекта

### Требования
- Android Studio Ladybug / Meerkat или IntelliJ IDEA
- JDK 17
- Android SDK 36.1 (minimum supported Android version: API 24)

### Сборка из командной строки

```bash
# Clone the repository
git clone https://github.com/oustery/cloudbox-app.git
cd cloudbox-app

# Run unit and Robolectric tests
./gradlew :app:testDebugUnitTest

# Run Android lint
./gradlew :app:lintDebug

# Build the debug APK
./gradlew :app:assembleDebug

# Build split release APKs (requires signing configuration)
./gradlew :app:assembleRelease -PsplitApks
```

Сгенерированные релизные файлы будут доступны в:
```
app/build/outputs/apk/release/
├── app-arm64-v8a-release.apk     # Для 64-битных устройств
├── app-armeabi-v7a-release.apk   # Для 32-битных устройств
└── app-universal-release.apk     # Универсальный APK
```

---

## CI/CD and releases (GitHub Actions)

The repository owns two separate workflows:

- `.github/workflows/ci.yml` runs on pull requests and pushes to `main`. It validates the checked-in Gradle wrapper, runs unit/Robolectric tests and Android lint, builds a debug APK, and uploads that APK as a short-lived Actions artifact. It has read-only repository permissions.
- `.github/workflows/release.yml` publishes a rolling `latest` prerelease only after a successful CI push to `main`. A `v*` tag starts a separate release run that validates wrapper, tests, lint, and debug build before packaging. Both paths build arm64-v8a, armeabi-v7a, and universal release APKs and verify SHA-256 checksums.

A successful `main` build updates the rolling `latest` prerelease. To make a versioned release, push a semantic version tag after merging the release-ready commit:

```bash
git tag v1.2.3
git push origin v1.2.3
```

Versioned releases require all four repository Actions secrets: `KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`. Encode the keystore as a single-line value (Linux: `base64 -w0 release.jks`) and keep a secure backup. Never commit signing keys or passwords. The Gradle release configuration uses the supplied alias and version metadata.

If no signing secrets exist, the rolling `latest` preview build uses an ephemeral key so delivery remains available. APKs signed by that temporary key are not suitable for in-place updates; configure a persistent signing key before broad distribution. Versioned releases intentionally fail instead of silently using a rotating key.

See [CONTRIBUTING.md](CONTRIBUTING.md) for developer setup and release instructions.

---

## 📋 План развития

Ознакомьтесь с подробным планом улучшений и архитектурным бэклогом в документе [plan.md](plan.md).

---

## 📄 Лицензия

Распространяется под лицензией MIT. Подробности в файле `LICENSE`.
