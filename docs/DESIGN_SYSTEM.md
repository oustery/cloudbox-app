# CloudBox — дизайн-система

Обновлено: октябрь 2026. Бренд: **CloudBox Rose**.

Дизайн-система описывает единый визуальный язык приложения: палитру, типографику,
радиусы, отступы, движение и бренд-блок (иконка + логотип). Всё построено поверх
Material 3; динамические цвета Material You остаются включёнными по умолчанию и
подменяют статическую палитру на палитру с обоев пользователя.

## 1. Бренд-акценты

| Токен | Значение | Назначение |
|---|---|---|
| `BrandAccent` | `#FF8FA8` | Главный акцент (плеер, выделения) |
| `BrandAccentStrong` | `#E85D84` | Акцент при наложении, нажатые состояния |
| `BrandGradientStart` | `#FF9CB4` | Градиентные круги/блоки (начало) |
| `BrandGradientEnd` | `#E0527C` | Градиентные круги/блоки (конец) |
| `FavoriteGold` | `#F2BF60` | Золотой: избранное, награды |

Градиент бренда — линейный 45° (левый верх → правый низ). Он же используется
в лаунчер-иконке (`#FF9DB3 → #D6436C`) и в кружке логотипа шапки.

## 2. Палитра (статические схемы)

Цвета объявлены в `ui/theme/Color.kt`, собраны в схемы в `ui/theme/Theme.kt`.

### Тёмная тема (тёплый графит с розовым подтоном)

| Роль | Значение |
|---|---|
| background / surface | `#171114` |
| surfaceContainerLow / Container | `#1F1619` / `#231A1D` |
| surfaceContainerHigh / Highest | `#2E2427` / `#392E31` |
| onSurface | `#ECE0E3` |
| onSurfaceVariant | `#D5C3C9` |
| outline / outlineVariant | `#9E8C93` / `#514349` |
| primary / onPrimary | `#FFB1C5` / `#5E1129` |
| primaryContainer / on | `#7C2942` / `#FFD9DE` |
| secondary (контейнеры) | `#E3BDC6` (`#5B3F47` / `#FFD9E0`) |
| tertiary (золото) | `#F2BF60` (`#5B4316` / `#FFDF9E`) |
| error / onError | `#FFB4AB` / `#690005` |

### Светлая тема (тёплый фарфор)

| Роль | Значение |
|---|---|
| background / surface | `#FFF8F7` |
| surfaceContainerLow / Container | `#FFF0F1` / `#FAEAEC` |
| surfaceContainerHigh / Highest | `#F4E4E6` / `#EFDFE1` |
| onSurface | `#22191B` |
| onSurfaceVariant | `#524347` |
| outline / outlineVariant | `#847378` / `#D7C2C6` |
| primary / onPrimary | `#8E3A52` / `#FFFFFF` |
| primaryContainer / on | `#FFD9DE` / `#3C0718` |
| secondary (контейнеры) | `#745560` (`#F7D9E0` / `#2B151C`) |
| tertiary (золото) | `#7A5900` (`#FFDEA6` / `#261A00`) |
| error / onError | `#BA1A1A` / `#FFFFFF` |

### Статусные цвета (загрузки)

| Токен | Значение | Смысл |
|---|---|---|
| `StatusSuccess` | `#34D399` | Завершено |
| `StatusWarning` | `#FBBF24` | Пауза/ожидание |
| `StatusError` | `#F87171` | Ошибка |
| `StatusRunning` | `#EF6E96` | Скачивается |

### Функциональные цвета файлов

Кодировка типов файлов (`getFileIconColor`) остаётся разноцветной сознательно —
это ориентир в списке, а не бренд: папка `#FFB300`, фото `#00D2F7`, видео `#EF4444`,
аудио `#8B5CF6`, архив `#F97316`, документ `#3B82F6`. Скримы поверх миниатюр —
чёрные (`#DC000000`, `#CC000000`, `#80000000`).

## 3. Типографика (`ui/theme/Type.kt`)

| Стиль | Размер/интерлиньяж | Вес | Использование |
|---|---|---|---|
| `displaySmall` | 34/42 | Bold | Гигантские заголовки (настройки и т.п.) |
| `headlineSmall` | 24/32 | SemiBold | Экранные заголовки |
| `titleLarge` | 22/28 | SemiBold | Заголовки шапок, секций |
| `titleMedium` | 16/24 | SemiBold | Заголовки карточек |
| `titleSmall` | 14/20 | Medium | Подзаголовки карточек |
| `bodyLarge` | 16/24 | Normal | Основной текст |
| `bodyMedium` | 14/20 | Normal | Вторичный текст |
| `bodySmall` | 12/16 | Normal | Подписи, метаданные |
| `labelLarge` | 14/20 | Medium | Кнопки |
| `labelMedium` | 12/16 | Medium | Чипы, бейджи |
| `labelSmall` | 11/16 | Medium | Мелкие подписи (субтитр шапки) |

## 4. Форма и отступы (`ui/theme/UiTokens.kt`)

- Отступы `UiSpacing`: 2 / 4 / 6 / 8 / 12 / 16 / 24 / 32 dp.
- Радиусы `UiRadius`: small 12, medium 16, large 20, sheet 28, pill 999 dp.
- Общие формы M3 (`Shapes`): 8 / 12 / 16 / 24 / 32 dp.
- Движение `UiMotionTokens`: 150 мс (короткое), 250 мс (стандарт),
  350 мс (акцентное, кривая `CubicBezier(0.2, 0, 0, 1)`).

## 5. Бренд-блок

Иконка лаунчера — **«облако в коробке»**: белое облако, вырезанное в скруглённой
коробке, на розовом градиенте.

- `drawable/ic_launcher_background.xml` — градиент + мягкий световой блик сверху слева.
- `drawable/ic_launcher_foreground.xml` — белый знак в safe zone (Ø66 адаптивной сетки).
- `monochrome` — тот же знак (тонируется системой на Android 13+).
- `drawable/ic_brand_mark.xml` — плотная версия знака (сетка 48) для шапки приложения.
- Legacy PNG (`mipmap-*`): знак отмасштабирован ×1.32 относительно adaptive-слоя.

Правила: знак всегда белый на розовом градиенте; не размещать знак на пёстром фоне
без подложки; монохромная версия системно тонируется — красить её вручную нельзя.

## 6. Правила применения

1. **Никаких хардкод-брендовых цветов в UI.** Только `MaterialTheme.colorScheme.*`
   или токены из `ui/theme`. Исключения: статусные цвета загрузок, кодировка типов
   файлов, скримы поверх фото/видео.
2. Material You включён по умолчанию — статическая схема применяется на Android < 12
   и при выключенных динамических цветах. Оба режима обязаны выглядеть канонично.
3. Розовый — единственный бренд-акцент. Синий Mail.ru из интерфейса выведен.
4. Золотой (`tertiary`) — только семантика «избранное/награда», не для кнопок.
5. Плеер сохраняет фиксированный `PlayerAccent = BrandAccent`: контраст поверх видео
   в обеих темах проверен.

## 7. Material 3 Expressive

Приложение использует полный канон Material 3 Expressive (Jetpack Compose
`material3:1.5.0-beta02` — первая линия, где Google собрал весь канон
компонентов; theme/motion-часть канона стабильна с 1.4.0).

### 7.1 Движение

- `MaterialExpressiveTheme(motionScheme = MotionScheme.expressive())` —
  пружинная физика для всех тематических компонентов (FAB-меню, чипы,
  диалоги, SplitButton).
- Кастомные анимации обязаны брать спецификации из
  `MaterialTheme.motionScheme` (`fastSpatialSpec`, `defaultSpatialSpec`, ...),
  а не изобретать свои tween-кривые. Tween остаётся только для fade-фаз.

### 7.2 Формы

- Канонический corner-scale в `CloudBoxShapes`:
  extraSmall 4 / small 8 / medium 12 / large 16 / extraLarge 28.
- Морфинг форм — через `MaterialShapes` + `Morph` (graphics-shapes):
  обёртки в `ui/components/expressive/Expressive.kt`
  (`MorphPolygonShape`, `MorphingIconButton`). Каноничный паттерн:
  круг → 12-стороннее печенье при нажатии; морф заменяет ripple.

### 7.3 Канонические компоненты и их места

| Компонент | Где используется |
| --- | --- |
| `LoadingIndicator` (морфинг фигур) | состояния загрузки `ContentStatePanel` |
| `LinearWavyProgressIndicator` | прогресс загрузок в `DownloadsScreen` |
| `CircularWavyProgressIndicator` | буферизация плеера, баннер обновления |
| `HorizontalFloatingToolbar` | панель действий режима выделения в проводнике |
| `SplitButtonLayout` | «Скачать» + меню (выбрать всё / снять) в тулбаре |
| `MorphingIconButton` (кастом) | шапка проводника, чек-контрол карточек |

### 7.4 Правила

1. Не заменять канонические компоненты самодельными, если они есть в
   библиотеке; самодельные — только надстройки над `MaterialShapes`/`Morph`.
2. Новый wavy-прогресс не задаёт `height` — высоту рисует сам компонент.
3. Экспериментальные API (`LoadingIndicator`, `MaterialShapes`, `Morph.toPath`)
   используются только с явным `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`
   на самом узком скоупе.
4. Поисковое поле — 28dp, фильтр-чипы — pill: закреплённый expressive-вид.
