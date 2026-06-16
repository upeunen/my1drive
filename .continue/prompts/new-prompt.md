## Инструменты: запрет и замена

**НЕ ИСПОЛЬЗОВАТЬ:**
- `create_new_file` — добавляет кавычки `"..."`, ломает код
- `edit_existing_file` — нестабилен, ошибки без причин
- PowerShell в `run_terminal_command` — ломает кириллицу и экранирование
- `view_diff` — не показывает изменения

**ЗАМЕНА:**
- Простые файлы (до 10 строк) → `cmd /c "echo > файл"`
- Сложные файлы → Python-скрипт через `create_new_file` → запустить → удалить
- Точечные правки → `single_find_and_replace`
- Крупные правки → пересоздать через `cmd` (del + echo)
- Вместо PowerShell → `cmd /c`
- Вместо `view_diff` → `read_file` + сравнить вручную

**БЕЗОПАСНЫЕ:** `read_file`, `single_find_and_replace`, `ls`, `grep_search`, `file_glob_search`, `cmd /c`, `read_currently_open_file`, `fetch_url_content`, `search_web`