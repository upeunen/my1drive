---
globs: app/src/main/java/by/w6/my1drive/ui/OtgConnectionManager.kt,app/src/main/java/by/w6/my1drive/ui/ArchiveSyncHelper.kt
description: Применять при изменениях в OtgConnectionManager, ArchiveSyncHelper
  или Архивации данных с OTG
---

# My1Drive — Правила разработки

## OtgConnectionManager (обнаружение и синхронизация)

### Обнаружение OTG-флешки
`isAnyOtgDrivePresent()` использует два подхода:
1. `volume.isRemovable && state == MEDIA_MOUNTED`
2. Любой не-primary том с `MEDIA_MOUNTED` и UUID != "private"

### Синхронизация при старте
- На старте поллинга всегда сбрасывать `initialSyncAttempted = false`
- В цикле: синхронизация запускается если `newStatus == KNOWN_DRIVE_CONNECTED` И (`isTransition` ИЛИ `!initialSyncAttempted`)
- После запуска синхронизации выставлять `initialSyncAttempted = true`
- Сбрасывать `initialSyncAttempted` в `onOtgUriSelected()`, `onEject()`, `createNewArchive()`

### Приветственный диалог
- `dismissFirstLaunchDialog()` **НЕ** сбрасывает `firstLaunchHandled` — чтобы диалог не висел при каждом поллинге
- `firstLaunchHandled` сбрасывается только в `createNewArchive()` (при создании нового архива)

## ArchiveSyncHelper (тихая и ручная синхронизация)

### Silent sync и Manual sync
Обе синхронизации работают по единому принципу:
1. **Сканируют** реальные файлы на флешке (кроме `.my1drive_uuid` и `.my1drive_db.json`)
2. **Для каждого файла**: SHA-256, displayName, mimeType, size, dateModified
3. **Перезаписывают** JSON на флешке актуальным списком
4. **Очищают** Room и вставляют актуальные записи

**Важно:** JSON и Room всегда отражают реальное состояние файлов на флешке. Никаких "пустых заглушек" для отсутствующих файлов.

### Обработка ошибок
- Пустой `catch (_: Exception) { }` запрещён. Всегда логировать через `Log.w(...)`.
- Исключения при хэшировании отдельных файлов (`calculateSha256`) — `continue` с пропуском.
- Ошибки уровня синхронизации (не удалось прочитать директорию) — логировать, сбросить `isSilentSyncing`.

## ArchiveMetadataStore (JSON на флешке)
- Файл `.my1drive_db.json` — источник истины.
- Каждая мутация архива (add, delete, restore) должна:
  1. Сначала изменить JSON на OTG-накопителе
  2. Потом обновить Room на устройстве
- При синхронизации JSON **полностью перезаписывается** актуальными данными с флешки

Со мной общайся на русском языке, все комментарии только на русском языке
не используй в синтаксисе PowerShell && 
после удачного debug при моем одобрении вноси измения в ARCHITECTURE.md если в процессе стали очевидны расхождения