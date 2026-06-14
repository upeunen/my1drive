\# My1Drive — Правила



1\. Минимум логических файлов. 5 штук: GalleryViewModel, OtgArchiveUtil, MediaRepositoryImpl, PreviewCacheManager, OtgThumbnailFetcher. Без OtgManager, ArchiveManager, ArchiveStore.



2\. Одна флешка — один архив. Нет ArchiveChipsBar и списка архивов.



3\. Превью lazy. Coil генерирует когда пользователь видит файл. Не при архивации.



4\. Только Room. Никаких JSON руками. SharedPreferences — только URI и лимит кэша.



5\. SHA-256 = ID. Первичный ключ. Никаких random ID.



6\. Восстановление в исходную папку. Нет настроек режима.



7\. BroadcastReceiver — только статус. Без вибро и звуков.



8\. Единый список файлов. Один Flow. Фильтрация по MediaStatus на UI.



9\. Файлы до 400 строк. Больше — выносить часть.



10\. Никаких кракозябр, мусорных комментариев, закомментированного кода.

