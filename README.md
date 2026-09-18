<div align="center">

  <img src="app-icon.png" alt="GlassesReader App Icon" width="120" />

  # GlassesReader

  **一款将手机屏幕文字实时同步到 Rokid 智能眼镜的 Android 应用。无需开发线，安卓手机安装即可使用**

  [![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-blue.svg)](https://kotlinlang.org/)
  [![Android](https://img.shields.io/badge/Android-12%2B-green.svg)](https://www.android.com/)
  [![Compose](https://img.shields.io/badge/Jetpack%20Compose-1.5.1-orange.svg)](https://developer.android.com/jetpack/compose)
  [![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

  <img src="info.png" alt="功能示意图" width="600" />

</div>

## 📖 项目简介

项目主页 👉 [glassesreader.conclean.top](https://glassesreader.conclean.top/)


GlassesReader 是一款基于 Kotlin 与 Jetpack Compose 构建的 Android 工具应用，通过无障碍服务抓取前台应用的屏幕文字，并通过蓝牙实时推送到 Rokid 智能眼镜显示。应用采用 Material Design 3 设计规范，提供简洁直观的用户界面。

### ✨ 核心特性

- 🔍 **智能文本采集**：监听屏幕内容变化，自动解析可见文字（优先适配微信读书，同时兼容其他阅读场景）
- 👓 **实时同步显示**：将采集的文本实时推送到 Rokid 眼镜端自定义页面显示
- 🔄 **自动重连**：应用启动时自动尝试重连已配对的设备，连接参数持久化保存
- 🎛️ **双控制入口**：
  - 主页圆形 FAB 按钮：主要服务控制入口
  - 悬浮窗开关按钮：可拖曳的浮窗按钮，与主页按钮状态同步
- ⚙️ **丰富的设置选项**：亮度调节、字体大小、文本处理选项等

## 🚀 快速开始

### 下载安装

1. 前往 [Releases](https://github.com/conclean/GlassesReader/releases) 页面下载最新版本的 APK
2. 在 Android 设备上安装 APK（需要允许"安装未知来源应用"）

### 首次使用

1. **权限授权**：首次启动应用会停留在"权限引导"页，依次完成：
   - ✅ 悬浮窗权限授权
   - ✅ 无障碍服务启用（在系统设置中找到 `ScreenTextService` 并启用）
   - ✅ 通知权限授权（Android 13+）

2. **设备连接**（CXR-L）：
   - 手机安装 **Rokid AI App（≥ 1.9.0）**，并在官方 App 内先完成眼镜配对/连接
   - 打开本应用「设备连接」页，点击「授权并连接」
   - 在官方 App 授权页同意后，等待本应用链路就绪
   - 授权 token 会本地保存，下次启动可自动尝试重连

3. **启动服务**：
   - 当权限与连接全部就绪时，点击主页右下角圆形 FAB 按钮启动读屏服务
   - 服务启动后，手机悬浮窗和眼镜端页面将同步显示识别文本

### 日常使用

- **控制服务**：
  - 主页圆形 FAB 按钮：主要控制入口，点击可开启/暂停读屏服务
  - 悬浮窗开关：可拖曳的浮窗按钮，与主页按钮状态同步（**默认开启**；可在「应用设置」中关闭）

- **调整显示**：
  - 在"显示设置"页调整眼镜端亮度（0-15）、字体大小（12-48 sp）
  - 配置文本处理选项（删除空行、删除换行符、删除首行/尾行）

## 🛠️ 技术栈

- **开发语言**：Kotlin
- **UI 框架**：Jetpack Compose + Material Design 3
- **架构模式**：MVVM（Model-View-ViewModel）
- **最低 SDK**：API 31 (Android 12+)（v2 / CXR-L；1.x 曾为 API 29）
- **目标 SDK**：API 35 (Android 15)

### 主要依赖

- [Rokid CXR-L SDK](./sdk/CXR%20L%20SDK/)（v2 开发中）：`com.rokid.cxr:client-l:1.0.4`，经官方 App 鉴权建链
- [Rokid CXR-M SDK](https://developer.rokid.com/)（1.x / `main` 冻结）：`com.rokid.cxr:client-m`，本 App 直连蓝牙
- [EasyFloat](https://github.com/princekin-f/EasyFloat)：悬浮窗管理
- Jetpack Compose：声明式 UI 框架
- Retrofit + OkHttp：网络请求（用于未来功能扩展）

## 📁 项目结构

```
app/src/main/java/com/app/glassesreader/
├── MainActivity.kt                    # 主 Activity，负责权限管理、服务控制、设备连接协调
├── accessibility/
│   ├── ScreenTextPublisher.kt        # 文本发布器，管理文本流
│   └── service/
│       └── ScreenTextService.kt      # 无障碍服务，抓取屏幕文字
├── sdk/
│   ├── CxrAuthManager.kt             # CXR-L 官方 App 鉴权与 token
│   ├── CxrConnectionManager.kt       # CXRLink 会话连接与链路就绪
│   └── CxrCustomViewManager.kt       # 眼镜端自定义页面管理（打开、更新、关闭）
├── service/
│   └── overlay/
│       └── TextOverlayService.kt     # 前台服务，管理悬浮窗和文本订阅
├── update/
│   ├── GitHubReleaseApi.kt          # GitHub Release API 接口
│   └── UpdateChecker.kt              # 更新检查器，检查应用版本更新
├── utils/
│   └── AccessibilityUtils.kt        # 无障碍服务工具类
└── ui/
    ├── components/                    # UI 组件库
    │   ├── CommonComponents.kt       # 通用组件（ServiceFab、StatusListItem 等）
    │   ├── Dialogs.kt                # 对话框组件（自动重连失败、更新提示等）
    │   ├── DisplayControls.kt        # 显示控制组件（亮度、字体大小、文本处理选项）
    │   └── FloatingToggle.kt         # 悬浮窗开关组件
    ├── model/                        # 数据模型
    │   └── MainUiModel.kt            # 主界面 UI 状态模型（MainTab、MainUiState）
    ├── screens/                      # 页面组件
    │   ├── MainScreen.kt             # 主屏幕组件
    │   ├── SettingsScreen.kt         # 设置页（权限、连接、应用设置）
    │   └── DeviceScanActivity.kt     # 设备连接页（官方 App 鉴权 + 建链）
    └── theme/                        # Material Design 3 主题配置
        ├── Color.kt                  # 颜色定义
        ├── Theme.kt                  # 主题配置
        └── Type.kt                   # 字体样式定义
```

## 🔧 开发说明

### 环境要求

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 8 或更高版本
- Android SDK API 31-35（v2 / CXR-L）

### 构建步骤

1. 克隆仓库：
```bash
git clone https://github.com/conclean/GlassesReader.git
cd GlassesReader
```

2. 使用 Android Studio 打开项目

3. 同步 Gradle 依赖

4. 连接 Android 设备或启动模拟器（需要 Android 12+）

5. 运行项目

### 权限说明

#### 系统权限
- `SYSTEM_ALERT_WINDOW`：创建悬浮窗，用于显示开关按钮
- `FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_DATA_SYNC`：Android 14+ 前台服务所需
- `POST_NOTIFICATIONS`（Android 13+）：显示前台服务常驻通知
- `INTERNET`：CXR-L 与官方 App / 服务通信

#### 系统服务
- **无障碍服务** `ScreenTextService`：监听屏幕内容变化事件并整理文本
- **前台服务** `TextOverlayService`：通过 EasyFloat 创建开关浮窗并维持采集服务

## 📝 功能模块

### 1. 权限引导（SETUP）
- 检查并引导用户授权悬浮窗权限
- 检查并引导用户启用无障碍服务
- 检查并引导用户授权通知权限（Android 13+）

### 2. 设备连接（CONNECT）
- 检测 Rokid AI App / Hi Rokid 是否已安装
- 引导用户在官方 App 完成授权，获取 token
- 建立 CXR-L CUSTOMVIEW 会话并显示链路状态
- **自动重连**：应用启动时若有本地 token 则自动尝试建链

### 3. 显示设置（DISPLAY）
- **亮度调节**：0-15 档位，实时同步到眼镜端
- **字体大小**：12-48 sp，可调节眼镜端显示字体
- **文本处理选项**：
  - 删除空行
  - 删除换行符
  - 删除首行/尾行（可设置删除行数）

### 4. 应用设置（SETTINGS）
- **悬浮窗开关按钮**：控制手机端悬浮窗的显示/隐藏
  - 当权限或连接未完成时，开关禁用并提示
  - 当所有条件满足时，开关启用，用户可手动开启悬浮窗
  - 悬浮窗隐藏时，仍可通过主页 FAB 按钮控制服务

## ⚠️ 已知限制与注意事项

### 连接限制（CXR-L）
- **依赖官方 App**：须安装 Rokid AI App（≥ 1.9.0）或 Hi Rokid，并完成本应用授权后才能建链。
- **眼镜先在官方 App 配对**：连接本应用前，请确保眼镜已在官方应用内完成配对/连接。
- **自动重连失败**：若本地 token 失效或链路未就绪，应用会提示前往「设备连接」页重新授权连接。

### 文本采集限制
- 文本采集依赖目标应用的无障碍节点，若页面采用 Canvas 自绘等方案可能无法完整捕获
- 眼镜端自定义页面依赖 CXR-L 链路，如连接断开将退回占位文本

### 使用建议
- 安装并保持 Rokid AI App 可用，先在官方 App 内连上眼镜
- 在本应用「设备连接」页完成授权并等待链路就绪
- 建议在连接成功后，先测试文本采集功能是否正常

## 🔄 SDK 升级说明（CXR-M → CXR-L）

### 版本与分支策略

| 项 | 说明 |
| --- | --- |
| **1.x（冻结）** | `main` 停留在 **v1.2.2**（CXR-M 直连）。**暂不维护**新功能；GitHub Release 仍可供下载 |
| **2.x（开发中）** | 分支 **`v2/cxr-l`**：升级 CXR-L、后续 2.x 功能均在此开发 |
| **当前包版本** | `versionName` = `2.0.0-dev`，`versionCode` = `20`（仅开发/内测；正式首发拟为 `2.0.0`） |
| **versionCode** | 自 20 起每次对外 APK +1；正式 `2.0.0` 及之后按语义化版本递增 `versionName` |

稳定后计划将 `v2/cxr-l` 合回 `main` 并打 tag `v2.0.0`；此后默认只维护 2.x。

当前 **v2 分支运行时已基于 CXR-L**（官方 App 鉴权 + `CXRLink`）；旧 BLE 扫描直连路径已移除。

### 核心结论

| 维度 | 说明 |
| --- | --- |
| **业务能力** | **不变**：手机无障碍抓取文本 → CustomView 投到眼镜显示 |
| **连接方式** | **大变**：须安装并协同 **Rokid AI App（≥ 1.9.0）** 或 Hi Rokid，经鉴权拿 token 后 `CXRLink.connect(token)` 建链；不再走本 App 的 `initBluetooth` / `connectBluetooth` 独占直连 |
| **用户侧变化** | 旧版强调「先断开官方 App」；新版改为「官方 App 是建链前置依赖」 |

### 对升级工作的影响（摘要）

1. **必改**：鉴权流程、`CxrConnectionManager`、依赖坐标（`client-m` → `client-l`）、权限引导（检测官方 App）
2. **可复用**：无障碍采集、悬浮窗/FAB、文本处理、CustomView JSON 布局思路（API 更名：`openCustomView` → `customViewOpen` 等）
3. **已定**：v2 分支 `minSdk = 31`；AR 截图 / 录屏已从本应用移除，改由官方 App 负责
4. **S0 状态**：依赖已切到 `client-l:1.0.4`；连接 / CustomView / 鉴权已落地

完整对照表、API 映射、状态机与改造清单见：

👉 **[CXR-M → CXR-L 迁移差异说明](./sdk/CXR-M-to-CXR-L-迁移差异.md)**

## 📚 技术文档

### 迁移与总览
- [CXR-M → CXR-L 迁移差异说明](./sdk/CXR-M-to-CXR-L-迁移差异.md)（升级必读）
- [CXR-L SDK 文档目录](./sdk/CXR%20L%20SDK/README.md)（v1.0.4）

### 旧版 CXR-M（当前集成）
- [设备连接](./sdk/doc/设备连接.md)
- [控制与监听设备状态](./sdk/doc/控制与监听设备状态.md)
- [数据操作](./sdk/doc/数据操作.md)
- [自定义页面场景](./sdk/doc/自定义页面场景.md)

### 新版 CXR-L（升级目标）
- [简介](./sdk/CXR%20L%20SDK/01-简介.md)
- [快速开始](./sdk/CXR%20L%20SDK/02-快速开始.md)
- [鉴权](./sdk/CXR%20L%20SDK/06-鉴权.md)
- [连接与会话](./sdk/CXR%20L%20SDK/07-连接与会话.md)
- [设备控制](./sdk/CXR%20L%20SDK/08-设备控制.md)
- [眼镜端自定义 View](./sdk/CXR%20L%20SDK/09-眼镜端自定义View.md)

## 🙏 致谢

- [Rokid](https://www.rokid.com/) - 提供智能眼镜 SDK 支持
- [EasyFloat](https://github.com/princekin-f/EasyFloat) - 悬浮窗管理库

## 📮 反馈与支持

如果你在使用过程中遇到问题或有功能建议，欢迎：
- 提交 [Issue](https://github.com/conclean/GlassesReader/issues)
- 发送邮件反馈

---

<div align="center">

**如果这个项目对你有帮助，请给个 ⭐ Star 支持一下！**

Made with ❤️ by [conclean]

</div>
