#!/usr/bin/env bash
# Manual review (not part of CI): renders every page of the sample documents with
# Inkpad (Robolectric, JVM) and with Xournal++, and writes side-by-side and diff images
# plus a similarity table to core/../android/render/build/render-crosscheck/.
# Xournal++ is pointed at Inkpad's bundled DejaVu fonts so text can be compared.
# Needs xournalpp, fc-match, python3 with Pillow, and a graphical session for xournalpp.
set -euo pipefail

cd "$(dirname "$0")/.."
repo="$PWD"
out="$repo/android/render/build/render-crosscheck"
for tool in xournalpp fc-match python3; do
    command -v "$tool" >/dev/null || { echo "missing tool: $tool" >&2; exit 2; }
done
python3 -c 'import PIL' 2>/dev/null || { echo "missing python module: Pillow" >&2; exit 2; }

rm -rf "$out"
mkdir -p "$out/xournalpp" "$out/review" "$out/fontcache"

./gradlew -q :android:render:testDebugUnitTest --tests 'pm.asaf.inkpad.android.render.RenderExportTest' --rerun \
    -Pinkpad.renderExportDir="$out/inkpad"

# Xournal++ resolves font names through fontconfig: make Sans/Serif/Monospace mean DejaVu.
cat >"$out/fonts.conf" <<CONF
<?xml version="1.0"?>
<!DOCTYPE fontconfig SYSTEM "fonts.dtd">
<fontconfig>
  <include ignore_missing="yes">/etc/fonts/fonts.conf</include>
  <dir>$repo/android/render/src/main/res/font</dir>
  <cachedir>$out/fontcache</cachedir>
  <match target="pattern"><test name="family" qual="any"><string>Sans</string></test>
    <edit name="family" mode="prepend" binding="strong"><string>DejaVu Sans</string></edit></match>
  <match target="pattern"><test name="family" qual="any"><string>sans-serif</string></test>
    <edit name="family" mode="prepend" binding="strong"><string>DejaVu Sans</string></edit></match>
  <match target="pattern"><test name="family" qual="any"><string>Serif</string></test>
    <edit name="family" mode="prepend" binding="strong"><string>DejaVu Serif</string></edit></match>
  <match target="pattern"><test name="family" qual="any"><string>serif</string></test>
    <edit name="family" mode="prepend" binding="strong"><string>DejaVu Serif</string></edit></match>
  <match target="pattern"><test name="family" qual="any"><string>Monospace</string></test>
    <edit name="family" mode="prepend" binding="strong"><string>DejaVu Sans Mono</string></edit></match>
  <match target="pattern"><test name="family" qual="any"><string>monospace</string></test>
    <edit name="family" mode="prepend" binding="strong"><string>DejaVu Sans Mono</string></edit></match>
</fontconfig>
CONF
export FONTCONFIG_FILE="$out/fonts.conf"
echo "Xournal++ fonts: Sans -> $(fc-match -f '%{family}' Sans), Serif -> $(fc-match -f '%{family}' Serif)"

while IFS=$'\t' read -r doc pages pdf_pages; do
    mkdir -p "$out/xournalpp/$doc"
    # Export next to a copy of the document so sidecar files (backgrounds) resolve.
    timeout 120 xournalpp --create-img="$out/xournalpp/$doc/page.png" --export-png-dpi=108 "$repo/testdata/$doc" \
        >"$out/xournalpp/$doc/export.log" 2>&1 || echo "Xournal++ could not export $doc" >&2
    # Single-page documents are exported without a page number.
    if [ -f "$out/xournalpp/$doc/page.png" ]; then mv "$out/xournalpp/$doc/page.png" "$out/xournalpp/$doc/page-1.png"; fi
done <"$out/inkpad/manifest.tsv"

python3 -I - "$out" <<'PY'
import sys, os
from PIL import Image, ImageChops
out = sys.argv[1]
rows = []
for line in open(f"{out}/inkpad/manifest.tsv", encoding="utf-8"):
    doc, pages, pdf = line.rstrip("\n").split("\t")
    pdf_pages = {int(p) for p in pdf.split(",") if p}
    for n in range(1, int(pages) + 1):
        ours, theirs = f"{out}/inkpad/{doc}/page-{n}.png", f"{out}/xournalpp/{doc}/page-{n}.png"
        name = f"{doc} p{n}"
        if n in pdf_pages:
            rows.append((name, None, None, "PDF background: review on a device")); continue
        if not os.path.exists(theirs):
            rows.append((name, None, None, "no Xournal++ export")); continue
        a, b = Image.open(theirs).convert("RGB"), Image.open(ours).convert("RGB")
        # Sizes can differ by a pixel (rounding of fractional page sizes): compare the common area.
        common = (0, 0, min(a.width, b.width), min(a.height, b.height))
        a, b = a.crop(common), b.crop(common)
        diff = ImageChops.difference(a, b)
        px = list(diff.get_flattened_data()) if hasattr(diff, "get_flattened_data") else list(diff.getdata())
        mean = sum(sum(p) for p in px) / (3 * len(px))
        far = 100 * sum(1 for p in px if max(p) > 32) / len(px)
        side = Image.new("RGB", (a.width * 3, a.height), "white")
        side.paste(a, (0, 0)); side.paste(b, (a.width, 0))
        side.paste(diff.point(lambda v: 255 - min(255, v * 4)), (2 * a.width, 0))
        side.save(f"{out}/review/{doc.replace('/', '__')}-p{n}.png")
        rows.append((name, mean, far, ""))
print(f"{'page':60s} {'mean':>7s} {'>32':>7s}")
for name, mean, far, note in rows:
    if mean is None: print(f"{name:60s} {'-':>7s} {'-':>7s}  {note}")
    else: print(f"{name:60s} {mean:7.2f} {far:6.2f}%")
print(f"\nSide-by-side images (Xournal++ | Inkpad | difference): {out}/review/")
PY
