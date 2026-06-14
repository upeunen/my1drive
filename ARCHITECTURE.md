# Архитектура My1Drive

## Слои

```
┌──────────────────────────────────────────────────────────────────┐
│                        UI (Compose)                              │
│  GalleryScreen → GalleryScreenContent → PhotosGridTab            │
│                    ↕                    GooglePhotosGridItem      │
│               GalleryViewModel ←─── SettingsTab                  │
│                    ↕                    BottomNavigationBar       │
│              GooglePhotosTopBar         FullscreenPreview         │
│              GalleryScreenActionBar     InfoDialog               │
│              OtgGuideDialog                                      │
└──────────────────────┬───────────────────────────────────────────┘
│
▼
┌──────────────────────────────────────────────────────────────────┐
│                    ViewModel / Логика                             │
│  GalleryViewModel (AndroidViewModel):                            │
│    ┌─ Состояния: mediaItems, selectedIds, archiveState, ...      │
│    ├─ Архивация: startArchiving(), performArchiving(), ...       │
│    ├─ Восстановление: requestRestore(), startRestoring(), ...    │
│    ├─ Удаление: deleteSelected(), deleteArchivedRecord()         │
│    ├─ Синхронизация: silentSyncArchive()                         │
│    └─ Кэш: onPreviewCached(), clearPreviewCache()                │
│                                                                  │
│  Utils:                                                          │
│    ├─ OtgArchiveUtil — SHA-256, копирование, восстановление      │
│    ├─ PreviewCacheManager — LRU кэш превью                       │
│    └─ OtgThumbnailFetcher — Coil fetcher для OTG файлов          │
└──────────────────────┬───────────────────────────────────────────┘
│
▼
┌──────────────────────────────────────────────────────────────────┐
│                    Repository / Data                              │
│  MediaRepository (interface) ← MediaRepositoryImpl               │
│    └─ Оборачивает DAO, добавляет логику                          │
│                                                                  │
│  Room Database:                                                  │
│    AppDatabase → MediaDao → MediaEntity                          │
│      (таблица media_archive, ключ = SHA-256)                     │
│                                                                  │
│  Domain модели:                                                  │
│    MediaItem, MediaStatus (ON_DEVICE | ARCHIVED_OTG)             │
└──────────────────────────────────────────────────────────────────┘
```

## Файлы и их ответственность

| Файл | Строк | Что делает |
|---|---|---|
| **GalleryViewModel.kt** | ~370 | Центральная логика: архивация, восстановление, синхронизация, управление состоянием UI |
| **OtgArchiveUtil.kt** | ~200 | SHA-256 хеширование, копирование файлов на OTG (SAF), восстановление через MediaStore |
| **MediaRepositoryImpl.kt** | ~100 | Трансформация Entity → Item, методы refresh/insert/delete |
| **PreviewCacheManager.kt** | ~150 | LRU кэш превью (макс 500 МБ), миграция, очистка |
| **OtgThumbnailFetcher.kt** | ~120 | Coil fetcher: загрузка превью с OTG через SAF, сохранение в кэш |
| **MediaItem.kt** | ~30 | data class: id, displayName, uri, mimeType, size, status, ... |
| **MediaEntity.kt** | ~20 | Room @Entity: таблица media_archive, SHA-256 = PrimaryKey |
| **MediaDao.kt** | ~30 | Room @Dao: CRUD, Flow, запросы для LRU |
| **MediaStatus.kt** | 2 | enum: ON_DEVICE, ARCHIVED_OTG |
| **AppDatabase.kt** | ~30 | Room @Database, version=2, MIGRATION_1_2 |
| **GalleryScreen.kt** | ~80 | Scaffold, FAB с ActionBar, ImageLoader, LaunchedEffect |
| **GalleryScreenContent.kt** | ~100 | Компоновка: TopBar, баннеры, PhotosRoute/ArchiveRoute/SettingsTab |
| **GooglePhotosTopBar.kt** | ~80 | Верхняя панель: статус OTG, кнопка подключения |
| **GalleryScreenActionBar.kt** | ~50 | FAB: Archive / Restore / Delete |
| **PhotosGridTab.kt** | ~70 | LazyVerticalGrid с DateCategoryHeader + GooglePhotosGridItem |
| **GooglePhotosGridItem.kt** | ~150 | Карточка файла: превью, выделение, длинное нажатие |
| **SettingsTab.kt** | ~80 | Настройки: выбор OTG, кэш (размер/очистка), инфо о приложении |
| **FullscreenPreview.kt** | ~100 | Полноэкранный просмотр с зумом |
| **InfoDialog.kt** | ~60 | Диалог с информацией о файле (имя, размер, дата) |
| **MainActivity.kt** | ~150 | Activity, SAF-пикеры, BroadcastReceiver, ViewModel |

## Поток данных

```
MediaStore (галерея) → [Архивация] → OTG (флешка)
↓                                        ↓
SHA-256 ← OtgArchiveUtil              DocumentFile (SAF)
↓                                        ↓
MediaEntity ← MediaDao ← Room DB      Превью → PreviewCacheManager
↓                                        ↓
MediaItem ← MediaRepositoryImpl       OtgThumbnailFetcher → Coil
↓                                        ↓
StateFlow ← GalleryViewModel          UI (Compose)
↓
groupedMediaItems → GalleryItem (Header/Media)
↓
PhotosGridTab → GooglePhotosGridItem
```

## Ключевые связи

- **SHA-256** = ID = PrimaryKey в Room — связывает файл на OTG с записью в БД
- **otgUri** (в MediaEntity) — SAF URI файла на флешке, по нему идёт чтение/восстановление
- **MediaStatus** — фильтрует список: ON_DEVICE = в галерее, ARCHIVED_OTG = на флешке
- **originalRelativePath** — путь в MediaStore (DCIM/Camera и т.д.), для восстановления в исходную папку
- **PreviewCacheManager** управляет папкой `filesDir/my1drive_previews/`, а **OtgThumbnailFetcher** наполняет её

## Правила (кратко)

1. **Одна флешка — один архив.** Нет списка архивов, нет ArchiveChipsBar.
2. **Превью lazy.** Coil генерирует когда пользователь видит файл. Не при архивации.
3. **Только Room.** Никаких JSON руками. SharedPreferences — только URI и лимит кэша.
4. **SHA-256 = ID.** Первичный ключ. Никаких random ID.
5. **Восстановление в исходную папку.** Нет настроек режима.
6. **BroadcastReceiver — только статус.** Без вибро и звуков.
7. **Единый список файлов.** Один Flow. Фильтрация по MediaStatus на UI.
8. **Файлы до 400 строк.** Больше — выносить часть.
