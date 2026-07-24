import re

strings = {
    'Неизвестный носитель': ('title_unknown_drive', 'Неизвестный носитель'),
    'Подключен неизвестный носитель. Создать новый архив, или если вернётся старый — сможете его синхронизировать': ('desc_unknown_drive', 'Подключен неизвестный носитель. Создать новый архив, или если вернётся старый — сможете его синхронизировать'),
    'Закрыть': ('action_close', 'Закрыть'),
    'Поделиться эскизами?': ('dialog_share_thumbnails_title', 'Поделиться эскизами?'),
    'я понимаю, больше не нужно предупреждать': ('dialog_do_not_warn', 'я понимаю, больше не нужно предупреждать'),
    'Ошибка': ('dialog_error_title', 'Ошибка'),
    'Выберите диапазон дат': ('dialog_select_date_range', 'Выберите диапазон дат'),
    'Некоторые из выбранных файлов находятся на отключенных накопителях. Будут отправлены их эскизы низкого разрешения.': ('dialog_share_thumbnails_desc', 'Некоторые из выбранных файлов находятся на отключенных накопителях. Будут отправлены их эскизы низкого разрешения.'),
    'Поделиться': ('action_share', 'Поделиться'),
    'Выбрать': ('action_select', 'Выбрать'),
    'Сменить': ('action_change', 'Сменить'),
    'Подключите нужный OTG накопитель': ('toast_connect_otg', 'Подключите нужный OTG накопитель'),
    'Отмена': ('action_cancel', 'Отмена'),
    'Все': ('tab_all', 'Все'),
    'Сменить папку архива?': ('dialog_change_folder_title', 'Сменить папку архива?'),
    'С датой': ('tab_with_date', 'С датой'),
    'ОК': ('action_ok', 'ОК'),
    'Ошибка архивирования': ('dialog_archive_error_title', 'Ошибка архивирования'),
    'Скопировать лог': ('action_copy_log', 'Скопировать лог'),
    'Ошибка восстановления': ('dialog_restore_error_title', 'Ошибка восстановления'),
    'Смена папки архива может нарушить текущую синхронизацию. Вы уверены, что хотите продолжить?': ('dialog_change_folder_desc', 'Смена папки архива может нарушить текущую синхронизацию. Вы уверены, что хотите продолжить?'),
    'ВЫДЕЛИТЬ': ('action_select_all', 'ВЫДЕЛИТЬ'),
    'В папке': ('tab_in_folder', 'В папке'),
    'Снять выделение': ('action_deselect', 'Снять выделение'),
    'Скопировано в буфер обмена': ('toast_copied_to_clipboard', 'Скопировано в буфер обмена'),
    'Диапазон': ('tab_date_range', 'Диапазон'),
    'В папке': ('tab_in_folder', 'В папке')
}

with open('app/src/main/res/values/strings.xml', 'r', encoding='utf-8') as f:
    xml = f.read()

for ru_text, (name, val) in strings.items():
    if f'<string name="{name}">' not in xml:
        xml = xml.replace('</resources>', f'    <string name="{name}">{val}</string>\n</resources>')

with open('app/src/main/res/values/strings.xml', 'w', encoding='utf-8') as f:
    f.write(xml)

with open('app/src/main/java/by/w6/my1drive/ui/GalleryScreenContent.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# For toast, replace context context -> context.getString(R.string...)
content = content.replace('"Скопировано в буфер обмена"', 'context.getString(R.string.toast_copied_to_clipboard)')
content = content.replace('"Подключите нужный OTG накопитель"', 'context.getString(R.string.toast_connect_otg)')

# For Text("..."), replace with stringResource
for ru_text, (name, val) in strings.items():
    if ru_text in ["Скопировано в буфер обмена", "Подключите нужный OTG накопитель", "ВЫДЕЛИТЬ"]:
        continue
    content = content.replace(f'"{ru_text}"', f'stringResource(R.string.{name})')

# Handle ВЫДЕЛИТЬ separately due to encoding quirks earlier if there are any
content = re.sub(r'\"ВЫДЕЛ.ТЬ\"', 'stringResource(R.string.action_select_all)', content)

# Also fix the duplicate missing chips logic by just letting Compose string resources be there
with open('app/src/main/java/by/w6/my1drive/ui/GalleryScreenContent.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("Strings replaced")
