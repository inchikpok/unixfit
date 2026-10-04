from pathlib import Path
import xml.etree.ElementTree as ET
files = list(Path("app/build/intermediates").rglob("AndroidManifest.xml"))
assert files, "Merged manifests not found"
for path in files:
    try:
        tree = ET.parse(path)
    except ET.ParseError:
        continue
    for item in tree.iter("uses-permission"):
        assert item.get("{http://schemas.android.com/apk/res/android}name") != "android.permission.INTERNET", path
print("OK: no INTERNET permission")
