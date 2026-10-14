#!/usr/bin/env bash
# Manual check (not part of CI): Xournal++ must render every sample document rewritten
# by Inkpad exactly like the original. Needs xournalpp, pdftoppm (poppler) and compare
# (ImageMagick), and a graphical session for xournalpp.
set -euo pipefail

cd "$(dirname "$0")/.."
out="$PWD/core/format/build/roundtrip"
for tool in xournalpp pdftoppm compare; do
    command -v "$tool" >/dev/null || { echo "missing tool: $tool" >&2; exit 2; }
done
echo "Using $(xournalpp --version 2>/dev/null | head -1)"

./gradlew -q :core:format:jvmTest --tests 'pm.asaf.inkpad.core.format.RoundTripExportTest' --rerun \
    -Pinkpad.roundtripDir="$out"

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
passed=0
failed=0
skipped=0

export_pdf() { # document, output pdf
    timeout 120 xournalpp --create-pdf="$2" "$1" >"$2.log" 2>&1 && [ -s "$2" ]
}

while IFS= read -r doc; do
    [ -n "$doc" ] || continue
    if ! export_pdf "$out/original/$doc" "$work/original.pdf"; then
        # Nothing to compare against, e.g. zip packages whose attached PDF Xournal++'s
        # exporter cannot open.
        echo "skip $doc: Xournal++ cannot export the original ($(tail -1 "$work/original.pdf.log"))"
        skipped=$((skipped + 1))
        continue
    fi
    ok=true
    if ! export_pdf "$out/rewritten/$doc" "$work/rewritten.pdf"; then
        echo "FAIL $doc: Xournal++ cannot export the rewritten document ($(tail -1 "$work/rewritten.pdf.log"))" >&2
        ok=false
    else
        rm -f "$work"/page-*.png
        pdftoppm -r 50 -png "$work/original.pdf" "$work/page-original"
        pdftoppm -r 50 -png "$work/rewritten.pdf" "$work/page-rewritten"
        originals=("$work"/page-original-*.png)
        rewrittens=("$work"/page-rewritten-*.png)
        if [ "${#originals[@]}" -ne "${#rewrittens[@]}" ]; then
            echo "FAIL $doc: ${#originals[@]} pages originally, ${#rewrittens[@]} after rewriting" >&2
            ok=false
        else
            for i in "${!originals[@]}"; do
                # Prints the number of differing pixels ("0" or, in newer versions, "0 (0)").
                diff=$(compare -metric AE "${originals[$i]}" "${rewrittens[$i]}" null: 2>&1 || true)
                if [ "${diff%% *}" != "0" ]; then
                    echo "FAIL $doc: page $((i + 1)) differs ($diff pixels)" >&2
                    ok=false
                fi
            done
        fi
    fi
    if $ok; then
        passed=$((passed + 1))
        echo "ok   $doc"
    else
        failed=$((failed + 1))
    fi
done <"$out/documents.txt"

echo "$passed passed, $failed failed, $skipped skipped"
[ "$failed" -eq 0 ]
