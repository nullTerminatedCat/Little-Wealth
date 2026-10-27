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

## 噪声粗测（独立模块 `:noise`）

和账本无关的小工具，包名 `com.lincx.noise`，界面为中文。它用麦克风估算 **A 计权**参考声级：大数字是快档（能量平滑时间常数约 0.125 秒），另外给出本次峰值和等效声级 Leq。A 计权是按 IEC 61672 曲线做的数字近似。

这不是计量器具。手机麦克风没有实验室校准，0 dBFS 只是固定当成 90 的满幅参考。可以在自认为安静的房间里测满约 2 秒，把这段等效声级平移到 30 dB(A)；房间如果并不是这个声级，所有读数会一起偏。相对起伏比绝对值更值得看。不要用于执法、职业卫生、听力诊断或验收。

应用不申请网络权限。测量只在页面位于前台时进行，离开页面会放开麦克风。采集会优先请求未处理的麦克风，并尝试关闭自动增益、降噪和回声消除，便于在小米 15 这类机型上少受系统加工影响；系统若不提供该通路，读数仍会受影响。

```bash
export ANDROID_HOME=/你的/Android/Sdk
./gradlew :noise:assembleDebug
```

成功后调试包在：

```text
noise/build/outputs/apk/debug/noise-debug.apk
```

装到 Android 8.0（API 26）及以上即可，已按 Android 15（含小米 15）的目标版本编译。首次打开要点允许麦克风；若系统不再弹出权限框，到系统设置里为本应用打开麦克风。小米手机路径一般为：设置 → 应用设置 → 噪声粗测 → 权限。

单元测试：

```bash
./gradlew :app:testDebugUnitTest :noise:testDebugUnitTest
```
