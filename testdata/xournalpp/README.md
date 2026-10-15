# Xournal++ test files

These files are copied unchanged from Xournal++ v1.3.8 (commit
`938bdb8de4d32f38f48dd7f6544886722157a848`), © the Xournal++ contributors,
licensed GPL-2.0-or-later.

| Here | In Xournal++ |
|---|---|
| everything except `development/` | `test/files/` (same relative paths) |
| `development/` | `development/documentation/` |

Left out: `big-test.xoj` and `packaged_xopp/big-test.xopp` (6 MB each, covered
by `testdata/physics.xopp`), `load/test.xoi` (empty), and the palette,
metadata and image fixtures, which are not Xournal++ document files.

The expectations Libreta's tests check for these files come from Xournal++'s
`test/unit_tests/control/LoadHandlerTest.cpp`. `docs/xopp-format.md`
describes what each file exercises.
