<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# QUẢN LÝ LỚP BẰNG MÃ QR - PP HẬU

Hệ thống quản lý lớp học và điểm danh chuyên cần học sinh THPT thông minh bằng mã QR mã hóa HMAC-SHA256, hỗ trợ hoạt động ngoại tuyến (Offline-First), phân tích chuyên cần bằng Trợ lý AI và xuất báo cáo Excel/in thẻ QR PDF hàng loạt.

View your app in AI Studio: https://ai.studio/apps/692b7a60-7192-46a3-a067-3d1ae47a7fcb

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device
7. If you have already published your app in AI Studio, please [request upload key reset](https://support.google.com/googleplay/android-developer/answer/9842756#zippy=%2Crequest-an-upload-key-reset) in Google Play Console.
