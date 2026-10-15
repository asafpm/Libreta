#!/usr/bin/env bash
# Manual check (not part of CI): PDFs exported by Libreta on a device (PdfExportDeviceTest)
# must look like Xournal++'s own exports of the same notes, keep the pages' sizes, and keep the
# annotated PDF's text. Run the device test first:
#   ./gradlew :android:render:connectedDebugAndroidTest \
#       -Pandroid.testInstrumentationRunnerArguments.class=pm.asaf.libreta.android.render.PdfExportDeviceTest
# Needs xournalpp, poppler-utils (pdftoppm, pdfinfo, pdftotext), python3 with Pillow, and a
# graphical session for xournalpp. Writes side-by-side images to android/render/build/pdf-export-check/.
set -euo pipefail

cd "$(dirname "$0")/.."
repo="$PWD"
exported=$(find "$repo/android/render/build/outputs/connected_android_test_additional_output" -name 'export-*.pdf' -printf '%h\n' | sort -u | head -1)
[ -n "$exported" ] || { echo "no exported PDFs: run PdfExportDeviceTest first" >&2; exit 2; }
out="$repo/android/render/build/pdf-export-check"
rm -rf "$out"
mkdir -p "$out/xournalpp" "$out/pages" "$out/review" "$out/fontcache"
for tool in xournalpp pdftoppm pdfinfo pdftotext python3; do
    command -v "$tool" >/dev/null || { echo "missing tool: $tool" >&2; exit 2; }
done

# Xournal++ draws text with the fonts Libreta bundles, as in render-crosscheck.sh.
cat >"$out/fonts.conf" <<CONF
<?xml version="1.0"?>
<!DOCTYPE fontconfig SYSTEM "fonts.dtd">
<fontconfig>
  <include ignore_missing="yes">/etc/fonts/fonts.conf</include>
  <dir>$repo/android/render/src/main/res/font</dir>
  <cachedir>$out/fontcache</cachedir>
  <match target="pattern"><test name="family" qual="any"><string>Sans</string></test>
    <edit name="family" mode="prepend" binding="strong"><string>DejaVu Sans</string></edit></match>
  <match target="pattern"><test name="family" qual="any"><string>Serif</string></test>
    <edit name="family" mode="prepend" binding="strong"><string>DejaVu Serif</string></edit></match>
</fontconfig>
CONF
export FONTCONFIG_FILE="$out/fonts.conf"

status=0
for ours in "$exported"/export-*.pdf; do
    name=$(basename "$ours" .pdf); name=${name#export-}
    theirs="$out/xournalpp/$name.pdf"
    timeout 120 xournalpp --create-pdf="$theirs" "$repo/testdata/$name.xopp" >"$theirs.log" 2>&1 || true
    if [ ! -s "$theirs" ]; then echo "skip $name: Xournal++ could not export it"; continue; fi
    # Page sizes, in points.
    sizes() { pdfinfo -f 1 -l 999 "$1" | awk '/^Page.*size/ {print $4, $6}'; }
    if ! diff <(sizes "$theirs" | awk '{printf "%.0f %.0f\n", $1, $2}') <(sizes "$ours" | awk '{printf "%.0f %.0f\n", $1, $2}') >/dev/null; then
        echo "FAIL $name: page sizes differ"; status=1
    fi
    # The text of the PDF it annotates is still text.
    pdftotext "$theirs" "$out/xournalpp/$name.txt"
    pdftotext "$ours" "$out/pages/$name.txt"
    if [ -s "$out/xournalpp/$name.txt" ] && ! diff -q <(tr -d '[:space:]' <"$out/xournalpp/$name.txt") <(tr -d '[:space:]' <"$out/pages/$name.txt") >/dev/null; then
        echo "note $name: the text differs from Xournal++'s export (see $out/pages/$name.txt)"
    fi
    pdftoppm -r 100 -png "$theirs" "$out/pages/$name-theirs"
    pdftoppm -r 100 -png "$ours" "$out/pages/$name-ours"
done

python3 -I - "$out" <<'PY'
import sys, glob, os, re
from PIL import Image, ImageChops
out = sys.argv[1]
print(f"{'page':40s} {'mean':>7s} {'>32':>7s}")
for theirs in sorted(glob.glob(f"{out}/pages/*-theirs-*.png")):
    ours = theirs.replace("-theirs-", "-ours-")
    name = re.sub(r"-theirs-0*(\d+)\.png$", r" p\1", os.path.basename(theirs))
    if not os.path.exists(ours):
        print(f"{name:40s} missing in Libreta's export"); continue
    a, b = Image.open(theirs).convert("RGB"), Image.open(ours).convert("RGB")
    common = (0, 0, min(a.width, b.width), min(a.height, b.height))
    a, b = a.crop(common), b.crop(common)
    diff = ImageChops.difference(a, b)
    px = list(diff.get_flattened_data()) if hasattr(diff, "get_flattened_data") else list(diff.getdata())
    mean = sum(sum(p) for p in px) / (3 * len(px))
    far = 100 * sum(1 for p in px if max(p) > 32) / len(px)
    side = Image.new("RGB", (a.width * 3, a.height), "white")
    side.paste(a, (0, 0)); side.paste(b, (a.width, 0))
    side.paste(diff.point(lambda v: 255 - min(255, v * 4)), (2 * a.width, 0))
    side.save(f"{out}/review/{name.replace(' ', '-')}.png")
    print(f"{name:40s} {mean:7.2f} {far:6.2f}%")
print(f"\nSide-by-side images (Xournal++ | Libreta | difference): {out}/review/")
PY
exit $status
