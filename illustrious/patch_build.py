from pathlib import Path
root=Path("source")
p=root/"pubspec.yaml"
s=p.read_text().replace("dependencies:\n", "dependencies:\n  crypto: ^3.0.6\n  flutter_localizations:\n    sdk: flutter\n",1)
s=s.replace("version: 1.0.0+1", "version: 0.1.4+5")
s=s.replace("  uses-material-design: true", "  uses-material-design: true\n  assets:\n    - DEV_NOTES.md")
p.write_text(s)
p=root/"android/app/src/main/AndroidManifest.xml"
s=p.read_text()
import re
s=re.sub(r'    <uses-permission[^>]+/>', '', s)
s=s.replace('<application', '<uses-permission android:name="android.permission.INTERNET" />\n    <application',1)
s=s.replace('android:label="Local Diffusion"','android:label="Local SDXL 0.1.4"')
s=s.replace('android:requestLegacyExternalStorage="true"','')
p.write_text(s)

# Separate installation protects the original app and its private model files.
p=root/"android/app/build.gradle.kts"
s=p.read_text().replace('applicationId = "com.example.local_diffusion"', 'applicationId = "com.localsdxl.mobile"')
p.write_text(s)
