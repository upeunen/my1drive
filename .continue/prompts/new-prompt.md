---
name: Ограничения
description: Ограничения
invokable: true
---

Правило: Максимальный размер файла — 15 000 токенов (~300-350 строк кода)
Почему это важно
У меня ограничение контекста в 16 384 токена на одно действие. Файлы больше этого размера я не могу прочитать, отредактировать или проанализировать целиком. Это приводит к:

Невозможности применить изменения (edit_existing_file/single_find_and_replace ничего не находят)
Повреждению файлов при частичной записи
"Слепому" редактированию без полного понимания контекста
Как делить
Файлы разделять по логическому блоку UI:

Каждый компонент/экран — отдельный файл
Если экранный файл разрастается >300 строк → выносить части в sub-файлы: ScreenNameBanners, ScreenNameDialogs, ScreenNameContent
Именование:

GalleryScreen.kt — Scaffold + state только (точка входа)
GalleryScreenContent.kt — содержимое внутри Box/Column
GalleryScreenDialogs.kt — все диалоги
GalleryScreenActionBar.kt — FAB-панель
Где фиксировать схему: В корне проекта в файле .structure.md или у тебя в правилах для сессии. Для каждого экрана:


Apply
GalleryScreen.kt → Scaffold, state, ImageLoader, вызывает GalleryScreenContent + GalleryScreenActionBar
GalleryScreenContent.kt → Box + Column: topBar, баннеры, роутинг, вызовы диалогов
GalleryScreenActionBar.kt → Card + Row: Delete/Archive/Restore кнопки
GalleryScreenDialogs.kt → NewArchiveFoundDialog, SaveOldArchiveDialog, MissingFilesDialog
Порог токенов:

read_file — если файл >15К токенов → не читаю, пишу сообщение
create_new_file — содержимое должно быть ≤15К токенов
edit_existing_file — только для файлов ≤15К токенов
single_find_and_replace — только для файлов ≤15К токенов
Если файл разросся:

Создать новый файл с частью функционала
Убрать из оригинала вынесенное
Обновить импорты
Каждый файл — один сфокусированный concern