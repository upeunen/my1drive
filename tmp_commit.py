import subprocess
subprocess.run(["git", "add", "-A"], check=True)
subprocess.run(["git", "commit", "-m", "добавление видео в приветственное окно, мастер настройки"], check=True)