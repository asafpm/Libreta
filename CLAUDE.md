# CLAUDE.md
- Read PLAN.md before starting. Current phase: 9.
- Build/test: ./gradlew check   Lint: ./gradlew ktlintCheck detekt   CI: scripts/ci.sh
- Screenshots: record with ./gradlew :android:render:testDebugUnitTest -Plibreta.recordScreenshots=true,
  then review the PNGs (scripts/render-crosscheck.sh compares with Xournal++) before committing.
- Device tests: ./gradlew connectedDebugAndroidTest (tablet unlocked); speed checks on the release-like
  build: ./gradlew :android:app:connectedBenchmarkAndroidTest -Plibreta.testBuildType=benchmark
- Kotlin style: immutable data classes, sealed types, no !! operators.
- Update "Current phase" here when a phase is done.