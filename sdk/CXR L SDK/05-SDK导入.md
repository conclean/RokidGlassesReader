# Android：SDK 导入

> 文档版本：1.0.4
> 功能开发 > Android

## 概述

在手机 Android 工程中引入 CXR-L 客户端库（client-l），配置 Maven 仓库与最低系统版本，使应用能够调用 CXRLink 及配套 API。

## 前置条件

- Android Studio，Gradle Kotlin DSL 或 Groovy 均可。
- 设备或模拟器满足 minSdk 31（Android 12+）。
- 网络可访问 Rokid Maven 公库。
- 手机已安装 Rokid AI App ≥ 1.9.0（大陆）或 Hi Rokid（海外），用于鉴权。

## 仓库配置

在工程根目录 `settings.gradle.kts` 的 `dependencyResolutionManagement.repositories` 中加入：

```kotlin
maven { url = uri("https://maven.rokid.com/repository/maven-public/") }
```

同时保留 `google()`、`mavenCentral()` 等常用仓库。

## 依赖声明

在 `app/build.gradle.kts` 的 `dependencies` 中：

```kotlin
implementation("com.rokid.cxr:client-l:1.0.4")
```

## Application 与全局连接

CXRLink 实例在会话生命周期内应全局复用。推荐在 Application 中持有单例引用，供鉴权后的会话页与子能力页共享：

```kotlin
class MyApplication : Application() {
    var sharedLink: CXRLink? = null

    fun resetSession() {
        sharedLink = null
    }
}
```

在 `AndroidManifest.xml` 注册：

```xml
<application android:name=".MyApplication" ...>
```

> 注意：namespace / applicationId 为你自有应用包名，与 CUSTOMAPP 会话配置的眼镜端目标包名无关。

## 权限与清单（必需）

RenewCXRLSample 与 CXR-L 集成须声明以下权限；按能力裁剪时，至少保留 INTERNET，CustomApp 安装路径涉及公共存储时须补齐存储相关权限。

| 权限 | 必需性 | 用途 |
| --- | --- | --- |
| INTERNET | 必需 | SDK 网络通信 |
| MANAGE_MEDIA | 推荐 | 媒体/文件访问（Sample 已声明） |
| MANAGE_EXTERNAL_STORAGE | CustomApp 读公共目录 APK 时 必需 | Android 11+ 访问公共存储中的 cxrL.apk |
| READ_EXTERNAL_STORAGE（maxSdkVersion=32） | Android 12 及以下读外部存储时 必需 | 读取公共目录 APK |

### CustomApp：安装 APK 前的运行时权限

从公共路径（如 `/sdcard/DCIM/Rokid/cxrL.apk`）读取 APK 并调用 `appUploadAndInstall` 前，须：

- Android 11+（API 30+）：引导用户授予「所有文件访问」（`Environment.isExternalStorageManager()`）。
- Android 6–12：请求 `READ_EXTERNAL_STORAGE` 运行时权限。
- 应用专属目录（如 `getExternalFilesDir("DCIM/Rokid")`）通常无需上述权限，推荐优先使用。

调用 `appUploadAndInstall` 前应确认目标文件 `exists()`、`canRead()`，且公共路径已满足存储权限（Sample 使用 ApkInstallAccess 校验）。

若需分享本地文件（如音频 WAV），可配置 FileProvider：

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

## 同步与验证

1. 执行 Gradle Sync，确认无依赖解析错误。
2. 任意源码文件中可成功 `import com.rokid.cxr.link.CXRLink`。

## 下一步

完成导入后，继续[鉴权获取 token](/05-鉴权.md)，再进入[连接与会话](/06-连接与会话.md)。

## 附录：对照工程

官方示例工程 `RenewCXRLSample`（com.rokid.renewcxrlsample）可参考 `settings.gradle.kts`、`app/build.gradle.kts` 与 `CXRLApplication.kt`。示例工程内 SDK 版本可能与文档发布版本不同，集成时以本文 1.0.4 为准。

---

# 眼镜端：CXR-S SDK 导入

在眼镜端 Android 工程中引入 CXR-S 客户端库（Maven 构件 `cxr-service-bridge`），使应用能够调用 CXRServiceBridge 及 Caps API。手机侧使用上文 client-l；**勿在手机 App 中引入 cxr-service-bridge**。

## 依赖声明

在眼镜端 `app/build.gradle.kts` 的 `dependencies` 中：

```kotlin
implementation("com.rokid.cxr:cxr-service-bridge:1.0-20260417.063502-103")
```

> 版本说明：文档以 CXRSWithCXRLSample 快照坐标为例；正式发布版本请以 Sample 工程 build.gradle.kts 与发布说明为准。

## AndroidManifest

CXRSWithCXRLSample 清单较为精简：

- 声明入口 MainActivity（`android:exported="true"`）。
- 无 CXR 相关额外权限；镜腿/触控板系统广播在运行时通过 `registerReceiver` 注册。

```xml
<application ...>
    <activity
        android:name=".activities.main.MainActivity"
        android:exported="true"
        ...>
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
    </activity>
</application>
```

## 初始化时机

CXRServiceBridge 应在 CustomApp 被手机 `appStart` 拉起后尽早完成：

- `setStatusListener(StatusListener)` — 监听连接状态。
- `subscribe(clientKey, MsgCallback)` — 订阅手机下发的自定义指令通道。

详见[自定义指令](/12-自定义指令.md)专章。Sample 在 MainViewModel.init 中完成上述两步。

## 约束

- `applicationId` 须与手机端 CUSTOMAPP 会话配置的 packageName 一致。
- SDK 版本须与手机端 client-l 及眼镜系统版本兼容，升级时两端同步验证。

## 附录：对照工程

`CXRSWithCXRLSample`：`app/build.gradle.kts`（minSdk = 31、依赖坐标）、`app/src/main/AndroidManifest.xml`。
