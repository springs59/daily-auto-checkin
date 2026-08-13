# 开发者指南

## 前置条件

- JDK 17
- Android SDK Platform 34 与 Build Tools 34

## 构建

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/tmp/android-sdk ./gradlew :app:assembleDebug :app:assembleRelease --no-daemon
```

输出 APK 位于 `app/build/outputs/apk/debug/` 与 `app/build/outputs/apk/release/`。

## 捕获文件导入维护

新增格式时，将源格式转换为 `CapturedRequest`，再复用 `CaptureImporter.classify` 和 `CaptureImporter.buildDraft`。导入流程仅应处理用户通过系统文件选择器明确选择的本地文件。

HTTPS 抓包导入以已解密的 HAR 或 cURL 为主。原始加密 PCAP/PCAPNG 应显示导出说明。
