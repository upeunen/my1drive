# Архитектура My1Drive (LLM-Friendly)

Документ описывает структуру проекта, потоки данных и ключевые правила разработки для быстрого входа моделей и агентов в контекст проекта.

---

## 📌 Общий концепт
Приложение предназначено для резервного копирования (архивации) локальных фото/видео из [MediaStore](https://developer.android.com/reference/android/provider/MediaStore) на OTG-накопитель (флешку) через Storage Access Framework (SAF) и их последующего восстановления.

* **Идентификация по SHA-256:** Каждый файл хэшируется при архивации. Хэш служит уникальным `PrimaryKey` (`id`/`hash`) в Room.
* **Единый список медиа:** Интерфейс склеивает локальные файлы (`ON_DEVICE`) и архивные копии (`ARCHIVED_OTG`) в единый поток.
* **Ленивые превью (Lazy caching):** Превью файлов на OTG генерируются Coil'ом лениво только при просмотре и сохраняются во внутреннем LRU-кэше приложения.
* **Смена OTG-накопителей (Поддержка мультиархивности):** Приложение поддерживает работу с несколькими внешними накопителями одновременно. Каждый архив имеет свой уникальный UUID (хранится в файле `.my1drive_uuid` / `.my1drive_uuid.txt` на флешке) и имя. База данных больше не затирается при смене флешки, а файлы с разных дисков хранятся изолированно.
* **Оффлайн-режим:** В настройках доступен переключатель «Работа с несколькими архивами (дисками)». При его включении файлы с отключенных накопителей отображаются в общей галерее в виде полупрозрачных оффлайн-элементов с иконкой `UsbOff`. При отключенном тумблере показываются только файлы текущего (подключенного) диска.
* **Идентификация архивов:** В свойствах файла (`InfoDialog`) выводится понятное название архива, на котором физически находится оригинал, и его текущий статус подключения.

---

## 🛠️ Стек технологий
* **UI:** Jetpack Compose, Coil (с кастомным Fetcher).
* **БД:** Room Database.
* **Асинхронность:** Coroutines, Flow / StateFlow.
* **Работа с файлами:** DocumentFile (SAF) для OTG, ContentResolver для MediaStore.

---

## 📁 Структура проекта и ключевые компоненты

Корневой пакет исходного кода: `android/app/src/main/java/by/w6/my1drive/`

### 1. Domain (Модели и логический слой)
* [MediaItem](file:///d:/My1drive/android/app/src/main/java/by/w6/my1drive/domain/model/MediaItem.kt) — Представление медиафайла для UI. Содержит статус: `ON_DEVICE` или `ARCHIVED_OTG`.
* [MediaStatus](file:///d:/My1drive/android/app/src/main/java/by/w6/my1drive/domain/model/MediaStatus.kt) — Enum со статусами: `ON_DEVICE`, `ARCHIVED_OTG`.
* [MediaRepository](file:///d:/My1drive/android/app/src/main/java/by/w6/my1drive/domain/repository/MediaRepository.kt) — Интерфейс для получения списка медиа и управления архивом.

### 2. Data (База данных и Репозиторий)
* [AppDatabase](file:///d:/My1drive/android/app/src/main/java/by/w6/my1drive/data/local/AppDatabase.kt) — Room БД (обновлена до версии 6, содержит миграции и таблицу зарегистрированных архивов).
* [ArchiveEntity](file:///d:/My1drive/android/app/src/main/java/by/w6/my1drive/data/local/ArchiveEntity.kt) — [NEW] Описание таблицы `archives` для хранения зарегистрированных в системе OTG-накопителей.
* [ArchiveDao](file:///d:/My1drive/android/app/src/main/java/by/w6/my1drive/data/local/ArchiveDao.kt) — [NEW] Операции для добавления и изменения названий архивов.
* [MediaEntity](file:///d:/My1drive/android/app/src/main/java/by/w6/my1drive/data/local/MediaEntity.kt) — Описание таблицы `media_archive`. Первичный ключ `id` = SHA-256 строка. Добавлено поле `archiveUuid` для связки с конкретной флешкой.
* [MediaDao](file:///d:/My1drive/android/app/src/main/java/by/w6/my1drive/data/local/MediaDao.kt) — DAO запросы, включая автоматическую миграцию legacy-файлов (`migrateLegacyArchiveUuid`).
* [MediaRepositoryImpl](file:///d:/My1drive/android/app/src/main/java/by/w6/my1drive/data/repository/MediaRepositoryImpl.kt) — Объединяет локальные и архивные файлы, фильтрует по тумблеру отображения оффлайн-архивов и сопоставляет имена дисков.

### 3. Utils (Файловый I/O и Кэширование)
* [OtgArchiveUtil](file:///d:/My1drive/android/app/src/main/java/by/w6/my1drive/utils/OtgArchiveUtil.kt) — Логика архивации/восстановления. Выполняет SHA-256 хэширование, копирование потоков данных, восстановление в MediaStore. Обрабатывает исключения (пропуск недоступных/облачных файлов через `CopyVerifyResult.Skipped`).
* [PreviewCacheManager](file:///d:/My1drive/android/app/src/main/java/by/w6/my1drive/utils/PreviewCacheManager.kt) — Менеджер LRU-кэша в `filesDir/my1drive_previews/` (расширение `.my1d` для скрытия превью от галерей). Лимит кэша по умолчанию 500 МБ.
* [OtgThumbnailFetcher](file:///d:/My1drive/android/app/src/main/java/by/w6/my1drive/utils/OtgThumbnailFetcher.kt) — Кастомный fetcher для Coil. Позволяет загружать превью на лету с флешки (если подключена) и сохранять их в кэш.

### 4. UI (Компоненты и логика представления)
* [MainActivity](file:///d:/My1drive/android/app/src/main/java/by/w6/my1drive/MainActivity.kt) — Activity приложения. Запрашивает SAF-разрешения для OTG, READ_MEDIA разрешения (включая частичный доступ на Android 14+), слушает Intent-ы подключения/отключения флешек.
* [GalleryViewModel](file:///d:/My1drive/android/app/src/main/java/by/w6/my1drive/ui/GalleryViewModel.kt) — Управление состояниями (выбранные файлы, прогресс бэкапа/восстановления/удаления), вызовы фоновых корутин.
* [ArchiveSyncHelper](file:///d:/My1drive/android/app/src/main/java/by/w6/my1drive/ui/ArchiveSyncHelper.kt) — Helper для проверки доступности архивных файлов на OTG (тихая синхронизация).
* Экран галереи:
  * [GalleryScreen](file:///d:/My1drive/android/app/src/main/java/by/w6/my1drive/ui/GalleryScreen.kt) — Главная обертка экрана.
  * [GalleryScreenContent](file:///d:/My1drive/android/app/src/main/java/by/w6/my1drive/ui/GalleryScreenContent.kt) — Навигация по вкладкам.
  * [PhotosGridTab](file:///d:/My1drive/android/app/src/main/java/by/w6/my1drive/ui/PhotosGridTab.kt) — Сетка фото с разбивкой по датам (`DateCategoryHeader`).
  * [GooglePhotosGridItem](file:///d:/My1drive/android/app/src/main/java/by/w6/my1drive/ui/GooglePhotosGridItem.kt) — Интерактивная карточка фото (поддержка выделения, отображение статусов).
    * [SettingsTab](file:///d:/My1drive/android/app/src/main/java/by/w6/my1drive/ui/SettingsTab.kt) — Настройки приложения: выбор пути к OTG, лимит кэша, **кнопка удаления текущего архива** (очистка БД и кэша).

---

## 🔄 Основные потоки данных (Data Flows)

### 📤 1. Архивация (Бэкап)
```mermaid
graph TD
    A[Пользователь нажимает Archive] --> B[GalleryViewModel.startArchiving]
    B --> C[OtgArchiveUtil.copyAndVerifyItem]
    C --> D[Хэширование SHA-256]
    D --> E[Копирование на флешку по SAF]
    E --> F[Повторное хэширование для верификации]
    F --> G{Совпадают?}
    G -- Да --> H[Запись в Room DB с SHA-256 в качестве ID]
    H --> I[Удаление исходного файла через MediaStore.createDeleteRequest]
    G -- Нет / Ошибка --> J[Файл пропускается, пишется в отчет об ошибке]
```

### 📥 2. Восстановление (Restore)
```mermaid
graph TD
    A[Пользователь нажимает Restore] --> B[GalleryViewModel.startRestoring]
    B --> C[OtgArchiveUtil.restoreItem]
    C --> D[Создание файла в MediaStore по originalRelativePath]
    D --> E[Копирование данных с флешки из otgUri]
    E --> F[Проверка хэша SHA-256 восстановленного файла]
    F --> G{Хэш валиден?}
    G -- Да --> H[Удаление записи из Room DB]
    H --> I[Удаление локального превью из кэша]
    G -- Нет / Ошибка --> J[Удаление битого файла из MediaStore, откат]
```

### 🖼️ 3. Ленивое превью OTG-файлов
```mermaid
graph TD
    A[Coil запрашивает изображение для ARCHIVED_OTG] --> B[OtgThumbnailFetcher.fetch]
    B --> C{Есть в LRU-кэше?}
    C -- Да --> D[Возвращаем из PreviewCacheManager]
    C -- Нет --> E{Флешка подключена?}
    E -- Да --> F[Читаем файл с флешки -> Генерируем превью]
    F --> G[Сжимаем в WebP -> Сохраняем в кэш с расширением .my1d]
    G --> H[Возвращаем Bitmap в Coil]
    E -- Нет --> I[Копирование невозможно -> Coil отображает ошибку]
```

### 4. Смена OTG-накопителя (переключение архива)
```mermaid
graph TD
    A[Подключена флешка] --> B[Чтение .my1drive_uuid / .my1drive_uuid.txt]
    B --> C{UUID найден?}
    C -- Нет --> D[Показ диалога ввода имени флешки ArchiveNamingDialog]
    D --> E[Генерация UUID, запись на флешку и в таблицу archives]
    C -- Да --> F[Проверка UUID в таблице archives]
    F --> G{UUID есть в БД?}
    G -- Нет --> H[Добавление флешки в БД]
    G -- Да --> I[Обновление даты последнего подключения]
    E --> J[Установка флешки как активной и запуск изолированной синхронизации]
    I --> J
    J --> K[Обновление UI без удаления файлов других флешек]
```

### 5. Ручное удаление архива
```mermaid
graph TD
    A[Пользователь нажимает «Удалить архив» в SettingsTab] --> B[GalleryViewModel.deleteArchive]
    B --> C[MediaDao.clearAll() — удаление всех записей]
    C --> D[PreviewCacheManager.clearAll() — удаление всех превью]
    D --> E[Обновление UI (архив пуст)]
```

---

## 🔒 Лимиты бесплатной версии (Archive Size Limits)

В приложении действует жесткий неотключаемый лимит в **128 МБ** на общий объем архивированных файлов.

### Внутренний свич (IS_LIMIT_ACTIVE)

Управление лимитом осуществляется **одной константой**:

**Файл:** `app/src/main/java/by/w6/my1drive/ui/GalleryViewModel.kt`  
**Строка 14:** `private const val IS_LIMIT_ACTIVE = true`

- `true` — лимит включён (поведение бесплатной версии)
- `false` — лимит отключён (поведение PRO-версии)

Это **внутренний, недоступный пользователю** свич. Изменяется только в исходном коде.  
На что влияет:
1. Блокировка архивации при превышении 128 МБ (`startArchiving` строка 181, `archiveSingleItem` строка 197)
2. Отображение прогресс-бара и предупреждений в `SettingsTab` (`ui/SettingsTab.kt`)

### Размер лимита

**Файл:** `app/src/main/java/by/w6/my1drive/ui/GalleryViewModel.kt`  
**Строка 15:** `private const val ARCHIVE_SIZE_LIMIT = 128L * 1024 * 1024 // 128 MB`

Единая константа для всех проверок.

### Как считается текущий объём архива

Вызов `updatePhysicalArchiveSize()` через `calculatePhysicalArchiveSize()` получает сумму `size` из таблицы `media_archive` в Room (`SELECT COALESCE(SUM(size), 0) FROM media_archive`).  
Вызов происходит:
1. При первом подключении диска (переход статуса в `KNOWN_DRIVE_CONNECTED`).
2. При явном изменении папки архива (`setOtgDirectory()`).
3. По завершении операций архивации, фоновой или ручной синхронизации (через колбэк `onOperationComplete` в [ArchiveSyncHelper](file:///d:/My1drive/android/app/src/main/java/by/w6/my1drive/ui/ArchiveSyncHelper.kt)).
4. По завершении операций восстановления (`startRestoring()`) или удаления файлов из архива (`deleteArchivedItems()`).
5. При отключении OTG — сбрасывается в 0.

### Проверка лимита перед операцией

Перед началом любой операции архивации (`startArchiving` или `archiveSingleItem`) приложение вычисляет:  
`текущий_размер_архива + размер_выбираемых_файлов`.  
Если сумма превышает `ARCHIVE_SIZE_LIMIT`, операция блокируется и выводится диалог.

---

## 🚨 Ключевые правила разработки (Strict Constraints)

1. **SHA-256 в качестве Primary Key:** Всегда используйте SHA-256 хэш файла как уникальный идентификатор в Room ([MediaEntity](file:///d:/My1drive/android/app/src/main/java/by/w6/my1drive/data/local/MediaEntity.kt)). Никаких случайных UUID.
2. **Изоляция архивов:** Приложение поддерживает переключение между флешками. Данные разных флешек сосуществуют в локальной БД Room и разделяются по `archiveUuid`. При фоновой синхронизации Room-записи другого диска **никогда не затираются**.
3. **Безопасность файловых операций (Android 14+ / Android 16):**
   - Для Android 14+ обязателен запрос `READ_MEDIA_VISUAL_USER_SELECTED`. Если доступ частичный, выводится предупреждение.
   - Любое чтение из галереи может бросить `SecurityException` (особенно на Android 16 для "облачных" файлов Google Фото). Ошибки должны изолироваться на уровне отдельных файлов (помечаться как `Skipped`), не ломая всю операцию архивации.
4. **Ленивая генерация превью:** Превью файлов на флешке **никогда** не генерируются при архивации. Только при непосредственном отображении в сетке галереи через [OtgThumbnailFetcher](file:///d:/My1drive/android/app/src/main/java/by/w6/my1drive/utils/OtgThumbnailFetcher.kt).
5. **Потокобезопасность:** Все тяжелые расчеты (SHA-256, I/O потоки, сжатие картинок) должны запускаться исключительно на пуле потоков `Dispatchers.IO`.
6. **Размер файлов:** Не допускайте разрастания кодовой базы в одном файле. Если класс UI/ViewModel превышает 400 строк, выносите логику в хелперы (например, [ArchiveSyncHelper](file:///d:/My1drive/android/app/src/main/java/by/w6/my1drive/ui/ArchiveSyncHelper.kt)).

---

## 📜 История версий архитектуры (Architecture Version Log)

### Версия v1.1.0
* **Изменения**: Реализована поддержка работы с несколькими архивами (флешками) одновременно без затирания БД при смене накопителя.
* **Архитектурные детали**:
  - В БД Room добавлена таблица `archives` и поле `archiveUuid` в таблицу `media_archive` (миграция 5->6).
  - На каждом диске создается идентификационный файл `.my1drive_uuid` (или `.my1drive_uuid.txt` из-за особенностей SAF).
  - Добавлен интерфейс диалога именования флешки `ArchiveNamingDialog` и переключатель оффлайн-файлов в настройках.
  - Вся логика синхронизации `ArchiveSyncHelper` изолирована по UUID текущего диска.

### Версия v1.0.1
* **Изменения**: Исправлен сброс авторизации SAF и ошибки доступа (`SecurityException`) при монтировании архива на съемном накопителе (OTG).
* **Архитектурные детали**:
  - Введён новый класс [OtgFolderResolver](file:///d:/My1drive/android/app/src/main/java/by/w6/my1drive/utils/OtgFolderResolver.kt) для динамического получения архивной папки `Arhiv-<DeviceName>` на базе Tree URI родительского тома, на который выданы постоянные права.
  - В [MainActivity](file:///d:/My1drive/android/app/src/main/java/by/w6/my1drive/MainActivity.kt) и Preferences сохраняется строго корневой Tree URI флешки (а не URI созданной папки), что гарантирует валидность persisted permissions.
  - Компоненты файлового I/O ([ArchiveMetadataStore](file:///d:/My1drive/android/app/src/main/java/by/w6/my1drive/utils/ArchiveMetadataStore.kt), [ArchiveSyncHelper](file:///d:/My1drive/android/app/src/main/java/by/w6/my1drive/ui/ArchiveSyncHelper.kt), [OtgArchiveUtil](file:///d:/My1drive/android/app/src/main/java/by/w6/my1drive/utils/OtgArchiveUtil.kt)) теперь резолвят путь архива через `OtgFolderResolver`.
