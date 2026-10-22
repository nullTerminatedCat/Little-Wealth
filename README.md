# 厘财

记在这台手机上的人民币账本。没有账号，不联网。

四个页面：

- **总览**：本月结余、存钱目标进度、资产配置偏离
- **记账**：按月记录收入和支出，精确到分
- **目标**：存钱目标，手动记入存入或取出
- **配置**：自己的资产目标比例和实际金额，偏离达到 5 个百分点会标出

金额以人民币分存在本地 Room 数据库 `licai.db`。应用不申请网络权限。

## 用 Android Studio 打开

1. 安装 Android Studio（带 Android SDK 35 与 JDK 17）。
2. 选择 **File → Open**，打开本仓库根目录（含 `settings.gradle.kts` 的那一层），等 Gradle Sync 完成。
3. 若提示找不到 SDK，在本机 `local.properties` 写一行（此文件已忽略，不要提交）：

   ```
   sdk.dir=/你的/Android/Sdk
   ```

   也可以设置环境变量 `ANDROID_HOME` 指向同一目录。
4. 运行配置选 `app`，装到模拟器或真机（Android 8.0 / API 26 及以上）。

## 命令行 assembleDebug

在仓库根目录：

```bash
export ANDROID_HOME=/你的/Android/Sdk
./gradlew :app:assembleDebug
```

成功后调试包在：

```text
app/build/outputs/apk/debug/app-debug.apk
```

仓库里同时放了一份同样的调试包：`dist/licai-debug.apk`。它用调试证书签名，只用于安装验证，不要拿去上架。

单元测试：

```bash
./gradlew :app:testDebugUnitTest
```
