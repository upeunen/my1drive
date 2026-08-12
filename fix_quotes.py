import re, os
files=['d:/My1drive/android/app/src/main/res/values/strings.xml', 'd:/My1drive/android/app/src/main/res/values-ru/strings.xml', 'd:/My1drive/android/app/src/main/res/values-es/strings.xml']
for f in files:
    with open(f, 'r', encoding='utf-8') as file:
        content = file.read()
    content = re.sub(r"(?<!\\)'", r"\'", content)
    with open(f, 'w', encoding='utf-8') as file:
        file.write(content)
