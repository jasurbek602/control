# Final build check

- GitHub Actions workflow: `.github/workflows/android-build.yml`
- Android module: `control/android-child`
- Build task: `:app:assembleDebug`
- Java: 17
- Gradle: the project's Gradle wrapper (`gradle-wrapper.properties`)
- APK artifact: `family-guard-debug-apk`

## Important

This package has been statically checked in this environment, but it has **not** been installed on a physical Samsung Galaxy S22 here. The GitHub runner can verify the Android build; a physical-device test is still required for Samsung-specific runtime behavior.

The app intentionally does not request runtime permissions automatically on first launch. Screen capture, location, notifications, Usage Access and Accessibility are user-triggered.
