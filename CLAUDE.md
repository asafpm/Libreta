# CLAUDE.md
- Read PLAN.md before starting. Current phase: 3.
- Build/test: ./gradlew check   Lint: ./gradlew ktlintCheck detekt   CI: scripts/ci.sh
- Screenshots: record with ./gradlew :android:render:testDebugUnitTest -Pinkpad.recordScreenshots=true,
  then review the PNGs (scripts/render-crosscheck.sh compares with Xournal++) before committing.
- Kotlin style: immutable data classes, sealed types, no !! operators.
- Update "Current phase" here when a phase is done.