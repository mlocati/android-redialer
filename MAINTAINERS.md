# Maintainer's Guide: How to Publish a New Version

Follow these steps to release a new version of the Redialer app on GitHub.

## 1. Automatic Versioning (Recommended)
You can use the provided scripts to automate the version bump, commit, and tagging process.

### On Linux or macOS (Bash):
```bash
./set-version 1.3.0
```

### On Windows (PowerShell):
```pwsh
.\set-version.ps1 "1.3.0"
```

These scripts will:
1.  **Increment** `versionCode` in `app/build.gradle.kts` by 1.
2.  **Update** `versionName` to the version provided (e.g., `1.3.0`).
3.  **Commit** the changes with the message "Version 1.3.0".
4.  **Create** a git tag named `v1.3.0`.

## 2. Push to GitHub
After running the script, push the commit and the tag to the remote repository in one command:

```bash
git push origin main --tags
```

## 3. Automated Process
Once the tag is pushed, the `Continuous Integration` workflow will:
1. **Verify**: Check that the tag name matches the `versionName` in `app/build.gradle.kts`.
2. **Build**: Compile both Debug and Release versions of the APK.
3. **Release**: Create a new GitHub Release and attach:
   - `app-debug.apk`
   - `app-release.apk` (Signed with debug key)

## 4. Manual Process (Alternative)
If you prefer to do it manually:
1. Open `app/build.gradle.kts`.
2. Increment `versionCode` and update `versionName`.
3. Commit: `git commit -am "Version 1.3.0"`.
4. Tag: `git tag v1.3.0`.
5. Push: `git push origin main --tags`.

## 5. Verify the Release
Go to the **Releases** section of your GitHub repository to ensure everything looks correct and the APKs are attached.
