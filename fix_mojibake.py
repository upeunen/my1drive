import re

with open('app/src/main/java/by/w6/my1drive/ui/GalleryScreenContent.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Remove duplicated mapStepToText and ProgressPanel at the end
# It starts at "fun mapStepToText(step: String): String {" around line 1284
idx = content.find("fun mapStepToText(step: String): String {")
if idx != -1:
    content = content[:idx]

# Replace specific strings with stringResource
content = content.replace('title = "РђСЂС…РёРІР°С†РёСЏ"', 'title = stringResource(R.string.title_archiving)')
content = content.replace('title = "Р’РѕСЃСЃС‚Р°РЅРѕРІР»РµРЅРёРµ"', 'title = stringResource(R.string.title_restoring)')
content = content.replace('title = "РЎРёРЅС…СЂРѕРЅРёР·Р°С†РёСЏ Р°СЂС…РёРІР°"', 'title = stringResource(R.string.title_syncing)')

content = content.replace('" (+${archiveState.pendingQueueSize} РІ РѕС‡РµСЂРµРґРё)"', 'stringResource(R.string.status_in_queue, archiveState.pendingQueueSize)')
content = content.replace('"Р’С‹С‡РёСЃР»РµРЅРёРµ С…СЌС€РµР№..."', 'stringResource(R.string.status_computing_hashes)')
content = content.replace('"РџРѕРёСЃРє С„Р°Р№Р»РѕРІ..."', 'stringResource(R.string.status_searching_files)')

# Add imports if not exist
if "import by.w6.my1drive.ui.components.mapStepToText" not in content:
    content = content.replace("import by.w6.my1drive.ui.components.ProgressPanel", "import by.w6.my1drive.ui.components.ProgressPanel\nimport by.w6.my1drive.ui.components.mapStepToText")

with open('app/src/main/java/by/w6/my1drive/ui/GalleryScreenContent.kt', 'w', encoding='utf-8') as f:
    f.write(content)
