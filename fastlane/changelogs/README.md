# Store Changelogs

This directory contains short, plain-text release notes curated for Google Play Store and App Store Connect release submissions. Google Play enforces a strict **500-character limit** per locale (`GOOGLE_PLAY_MAX_CHANGELOG_CHARS = 500`).

## Rules

1. **Strict 500-Character Limit:** Every `.txt` file in this directory (including translations) must be `<= 500` Unicode code points after stripping leading and trailing whitespace. Target 300 to 420 characters in English so French translations have comfortable headroom under 500 characters:
   ```bash
   python3 -c 'import pathlib,sys; [print(len(pathlib.Path(p).read_text("utf-8").strip()), p) for p in sys.argv[1:]]' fastlane/changelogs/*.txt
   ```
2. **Plain Text Only:** Use plain `- ` bullet points without `#` headings, `**bold**` markers, or backticks.
3. **Naming Convention:**
   - Production releases: `<version>.txt` (e.g., `7.0.0.txt`) and French `<version>.fr.txt` (`7.0.0.fr.txt`).
   - Optional pre-release notes: `dev.txt` and `dev.fr.txt`.
