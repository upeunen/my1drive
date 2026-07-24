import re

with open('app/src/main/java/by/w6/my1drive/ui/GalleryScreenContent.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# Find any string containing non-ascii characters
matches = re.findall(r'\"([^\"]*[^\x00-\x7F][^\"]*)\"', text)
for m in set(matches):
    try:
        fixed = m.encode('windows-1251').decode('utf-8')
        print(repr(m), '->', repr(fixed))
    except Exception as e:
        print(repr(m), 'Error:', e)
