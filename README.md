# Code Agent Mobile

Personal Android coding agent for GitHub repositories.

## What it does
- Uses an OpenAI-compatible AI API such as DeepSeek or Qwen.
- Reads repository files through the GitHub API.
- Creates a working branch before edits.
- Creates/updates/deletes files on that branch.
- Creates a **draft pull request** back to the selected base branch.
- Stores API credentials using Android Keystore-backed encryption.
- No Termux or terminal is required on the phone.

## Build the APK from your phone
1. Open this repository on GitHub.
2. Open **Actions** and select **Build APK**.
3. Tap **Run workflow** (or wait for the build triggered by a push).
4. Open the completed workflow run.
5. Under **Artifacts**, download `CodeAgentMobile-debug`.
6. Extract the ZIP and install `app-debug.apk` on Android.

The APK is a debug build intended for personal use.

## GitHub token
Use a fine-grained GitHub token scoped only to the repositories you want the agent to edit. It needs repository Contents read/write and Pull requests read/write access.

## AI
For DeepSeek, use the OpenAI-compatible base URL shown by DeepSeek's current API documentation and your selected model. Qwen-compatible endpoints can also be entered manually.

## Important V1 limitation
The agent edits text files through GitHub APIs. It does not execute arbitrary shell commands on the Android device. CI/test execution and richer diff approval are planned for later versions.
