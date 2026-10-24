# CloudBox

<p align="center">
  <img src="app/src/main/res/drawable/ic_launcher_foreground.xml" width="112" height="112" alt="CloudBox" />
</p>

<p align="center">
  <strong>Нативный Android-клиент для публичных ссылок Облака Mail.ru и Яндекс.Диска:<br/>
  просмотр медиа, навигация по папкам и фоновая загрузка файлов без браузера.</strong>
</p>

<p align="center">
  <a href="https://github.com/oustery/cloudbox-app/actions/workflows/ci.yml">
    <img src="https://github.com/oustery/cloudbox-app/actions/workflows/ci.yml/badge.svg" alt="CI" />
  </a>
  <img src="https://img.shields.io/badge/platform-Android_7.0%2B_(API_24%2B)-3DDC84?logo=android&logoColor=white" alt="Platform" />
  <img src="https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Jetpack%20Compose-M3_Expressive-4285F4?logo=jetpackcompose&logoColor=white" alt="Compose" />
  <img src="https://img.shields.io/badge/architecture-MVVM%20%2F%20Clean-blue" alt="Architecture" />
  <img src="https://img.shields.io/badge/license-MIT-green" alt="License" />
</p>

---

## О проекте

CloudBox создан вокруг одной проблемы: веб-интерфейсы облачных сервисов неудобны для массового
просмотра и скачивания контента по публичным ссылкам. Приложение работает с API сервисов
напрямую — без регистрации и логина: пользователь вставляет публичную ссылку и получает нативную
навигацию по вложенным папкам, галерею с превью, потоковый видеоплеер и полноценный менеджер
загрузок с паузой и возобновлением.

Ключевые свойства проекта:

- **Мультипровайдерность** — единый интерфейс для Облака Mail.ru и Яндекс.Диска; провайдер
  распознаётся из самой ссылки и маршрутизируется в соответствующий репозиторий.
- **Оффлайн-дружелюбная библиотека** — посещённые ссылки сохраняются локально (Room) вместе
  с категориями, избранным и счётчиками содержимого.
- **Роуллинг-дистрибуция** — CI/CD собирает подписанные APK и публикует rolling-релиз,
  приложение умеет обновляться прямо из себя.

## Возможности

### Библиотека ссылок

- Вставка ссылки из буфера одним тапом, приём ссылок через системное меню «Поделиться».
- Автоматическое сохранение открытых папок: название, категория, количество файлов, обложка.
- Сводка на главной: сколько ссылок сохранено, сколько файлов в них лежит, сколько в избранном.
- Фильтры с живыми счётчиками, поиск, сортировка (по дате, имени, объёму) — состояние
  запоминается между сессиями.
- Свайп-жесты: вправо — избранное, влево — удаление с отменой (undo-снекбар).
- Объединённый пул «Все файлы»: содержимое всех сохранённых ссылок в одном списке
  с указанием источника у каждого элемента.

### Проводник

- Навигация по подпапкам с хлебными крошками, адаптивная сетка или список.
- Превью фото и видео с шиммер-загрузкой; режим «экономия трафика» отключает превью.
- Мгновенный поиск по имени, фильтры по типам (медиа, фото, видео, аудио, документы, архивы),
  сортировка — всё сохраняется как состояние рабочего пространства.
- Мультивыбор с пакетной загрузкой: вся папка или только отмеченные элементы.

### Просмотр медиа

- Фото: зум до 5x, панорамирование, двойной тап, листание соседних файлов папки.
- Видео: Media3/ExoPlayer с потоковым воспроизведением без предварительной загрузки,
  LRU-кэш 256 МБ для мгновенных повторных просмотров, докачка ±10 сек двойным тапом,
  скорость 0.5–2x, пауза/цикл/мьют, продолжение с места остановки.
- Аудио и документы открываются системными обработчиками.

### Загрузки

- Фоновый foreground-сервис с агрегированным уведомлением: прогресс, скорость, действия
  «Пауза» и «Отмена» прямо из шторки.
- Возобновление через HTTP Range, до 3 попыток на файл, параллелизм ограничен четырьмя задачами.
- Файлы сохраняются в `Downloads/CloudBox` и сразу видны в галерее и файловом менеджере.

### Настройки и обновления

- Тема (системная/светлая/тёмная), динамические цвета Material You, стартовый экран,
  виброотклик, экономия трафика, плотность сетки проводника.
- Статистика кэша (превью в памяти/на диске, медиа-кэш плеера) и его очистка.
- Встроенный обновлятор: проверка rolling-релиза, скачивание и установка APK в два тапа.

## Поддерживаемые провайдеры

| Провайдер | Распознаваемые ссылки | Механизм доступа |
|---|---|---|
| Облако Mail.ru | `cloud.mail.ru/public/<id1>/<id2>`, `cloud.mail.ru/public/…/подпапка`, короткий ключ `id1/id2` | Dispatcher API (weblink_get / weblink_view / thumbnails) + Folder API v2, HTML-фолбэк |
| Яндекс.Диск | `disk.yandex.ru/d/<id>`, `disk.yandex.ru/i/<id>`, `yadi.sk/d/<id>`, публичные пути к файлам | Public API `cloud-api.yandex.net` (`public/resources`, `public/resources/download`), OAuth не требуется |

Провайдер определяется автоматически при разборе ссылки ([`LinkProvider`](app/src/main/java/com/example/data/api/LinkProvider.kt));
смешанная библиотека и общий пул поддерживаются «из коробки» — бейдж на карточке показывает источник.

## Архитектура

```
┌──────────────────────────────────────────────────────────────────┐
│                          UI (Jetpack Compose, M3)                │
│  HomeScreen   FolderExplorerScreen   MediaViewer   DownloadsScreen│
│  SettingsScreen            + общие компоненты (cards, chips…)     │
└──────────────────────────────┬───────────────────────────────────┘
                               │  StateFlow / UiMessenger (снекбары)
┌──────────────────────────────▼───────────────────────────────────┐
│                          ViewModels                              │
│  CloudLinkViewModel (библиотека, проводник, пул, плеер)           │
│  DownloadViewModel (очередь загрузок)                             │
│  AppSettingsViewModel (тема, поведение — DataStore)               │
└──────────────┬────────────────────────────────┬──────────────────┘
               │                                │
┌──────────────▼──────────────┐  ┌──────────────▼─────────────────┐
│        Repository layer     │  │      DownloadRepository        │
│  CloudMailRuRepository ─────┼──┤  (foreground-сервис, Range,    │
│   └─ provider routing:      │  │   ретраи, параллелизм ≤ 4)     │
│      MAILRU → Mail.ru API   │  └──────────────┬─────────────────┘
│      YANDEX → YandexDiskApi │                 │
└──────┬──────────────┬───────┘                 │
       │              │                         │
┌──────▼──────┐ ┌─────▼───────────┐   ┌─────────▼──────────┐
│ Mail.ru API │ │ Yandex Disk API │   │  Room (SavedLink,  │
│ dispatcher/ │ │ cloud-api.      │   │  DownloadTask) +   │
│ folder/v2/  │ │ yandex.net      │   │  DataStore (prefs) │
│ thumbnails  │ │ public/resource │   └────────────────────┘
└─────────────┘ └─────────────────┘
```

- **`data/api`** — Retrofit-сервисы обоих провайдеров, разбор и нормализация ссылок
  (`CloudUrlParser`, `LinkProvider`, `YandexUrlParser`).
- **`data/repository`** — доступ к контенту: листинг папок, превью, прямые ссылки на файлы,
  стриминг. Внешний код не знает, с каким облаком работает.
- **`data/local`** — Room: сохранённые ссылки и задачи загрузок; **`data/preferences`** —
  DataStore: тема, поведение, состояние проводника.
- **`ui/core`** — общий скелет UX: снекбар-шина `UiMessenger`, тактильный отклик `Haptics`,
  диалоги подтверждения.

### Как это работает на примере Mail.ru

1. Из ссылки выделяется weblink-ключ (`KVy7/S1hRRPVUE`).
2. `GET /api/v2/dispatcher` возвращает актуальные пулы хостов для скачивания, стриминга
   и генерации превью.
3. `GET /api/v2/folder?weblink=…` отдаёт дерево папки с типами и размерами.
4. Нестандартные ссылки обрабатываются HTML-фолбэком (мета-теги страницы).

Для Яндекс.Диска всё проще: `public/resources?public_key=…` возвращает и дерево, и превью,
а `public/resources/download` — подписанную прямую ссылку на файл.

## Структура проекта

```
app/src/main/java/com/example/
├── MainActivity.kt            # навигация, тема, глобальные снекбары
├── data/
│   ├── api/                   # Retrofit, разбор ссылок, LinkProvider
│   ├── local/                 # Room: entities, DAO
│   ├── model/                 # CloudItem, сортировки
│   ├── preferences/           # DataStore: AppSettings, UiPrefs
│   ├── repository/            # Mail.ru + Yandex репозитории, загрузки
│   └── update/                # AppUpdateManager (rolling-релиз)
├── service/                   # DownloadService (foreground)
└── ui/
    ├── components/            # карточки, чипы, баннеры, скелетоны
    ├── core/                  # feedback (снекбары, хаптика), диалоги
    ├── icons/                 # кастомные иконки поверх Lucide
    ├── screens/               # главная, проводник, плеер, загрузки, настройки
    ├── theme/                 # дизайн-система CloudBox Rose, токены
    └── viewmodel/             # CloudLink, Download
```

## Технологии

| Слой | Технология |
|---|---|
| Язык | Kotlin 2.2, корутины, Flow |
| UI | Jetpack Compose, Material 3 Expressive, Lucide-иконки, шрифт Inter |
| Сеть | Retrofit 2.12, OkHttp 4.10, Moshi (codegen) |
| БД и настройки | Room 2.7 (KSP), DataStore Preferences |
| Медиа | Coil 2.7 (превью), Media3 ExoPlayer 1.8.1 (стриминг + SimpleCache) |
| Фоновая работа | Foreground Service, DownloadManager (обновления APK) |
| Тесты | JUnit 4, Robolectric, Compose UI тесты |
| CI/CD | GitHub Actions: тесты + lint + сборка, rolling-релиз с подписью |

## Сборка

Требования: JDK 17+, Android SDK 37 (используется Gradle wrapper 9.x).

```bash
git clone https://github.com/oustery/cloudbox-app.git
cd cloudbox-app

./gradlew :app:testDebugUnitTest   # юнит- и Robolectric-тесты
./gradlew :app:lintDebug           # Android lint
./gradlew :app:assembleDebug       # debug-APK
./gradlew :app:assembleRelease -PsplitApks   # релизные APK (нужна подпись)
```

Релизная сборка делит APK по ABI (`arm64-v8a`, `armeabi-v7a`, `universal`) и подписывается
ключом из репозиторных секретов: `KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_ALIAS`,
`KEY_PASSWORD`. Без секретов rolling-сборка подписывается эфемерным ключом (обновления
«поверх» не работают), а версионируемые релизы намеренно падают — ключ не подменяется тихо.

## CI/CD

- **`ci.yml`** — на каждый PR и push в `main`: проверка wrapper, юнит-тесты, lint,
  debug-сборка; артефакт живёт до конца прогона. Права — read-only.
- **`release.yml`** — после зелёного CI в `main` публикует rolling-релиз `latest`
  (три APK + SHA-256 контрольные суммы, стабильный пермалинк); тег `v*` запускает
  версионируемый релиз.

Версионированный релиз:

```bash
git tag v1.2.3
git push origin v1.2.3
```

Подробнее о разработке и выпуске — в [CONTRIBUTING.md](CONTRIBUTING.md).

## Документация

- [docs/DESIGN_SYSTEM.md](docs/DESIGN_SYSTEM.md) — дизайн-система: палитра, типографика, токены.
- [docs/UX_IMPROVEMENT_PLAN.md](docs/UX_IMPROVEMENT_PLAN.md) — UX-аудит и дорожная карта.
- [plan.md](plan.md) — архитектурный бэклог.

## Лицензия

Распространяется под лицензией MIT.
