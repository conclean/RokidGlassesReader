# CXR-M → CXR-L SDK 迁移差异说明

> 对比范围：
> - **旧版**：`sdk/doc/`（CXR-M，项目当前依赖 `com.rokid.cxr:client-m:1.0.1-…`）
> - **新版**：`sdk/CXR L SDK/`（CXR-L v1.0.4，`com.rokid.cxr:client-l:1.0.4`）
>
> 整理日期：2026-08-21  
> 面向：GlassesReader 升级准备（核心业务仍为「手机无障碍文本 → 眼镜 CustomView」）

---

## 1. 一句话结论

| 维度 | 结论 |
| --- | --- |
| 业务能力 | **不变**：仍可通过 CustomView 把手机侧文本投到眼镜显示 |
| 连接模型 | **大变**：从「本 App 独占直连蓝牙」改为「必须经 Rokid 官方 App 鉴权 + 协同建链」 |
| 集成成本 | **高**：连接层、鉴权、状态机、API 入口类几乎全部重写；CustomView JSON 协议大体可复用 |

---

## 2. SDK 定位对比

| 项 | CXR-M（旧） | CXR-L（新） |
| --- | --- | --- |
| Maven 坐标 | `com.rokid.cxr:client-m` | `com.rokid.cxr:client-l:1.0.4` |
| 入口类 | `CxrApi.getInstance()` 单例 | `CXRLink` 实例（进程内建议全局复用） |
| 运行前提 | 本 App 扫蓝牙并直连眼镜 | 手机须安装 **Rokid AI App ≥ 1.9.0**（大陆）或 **Hi Rokid**（海外） |
| 文档目录 | `sdk/doc/` | `sdk/CXR L SDK/` |
| 示例工程 | CXR-M Sample | `RenewCXRLSample` + 眼镜端 `CXRSWithCXRLSample` |
| 建议 minSdk | 项目现为 29 | 文档要求 **minSdk 31**（Android 12+） |
| 配套眼镜端 SDK | 无（CustomView 由手机直接下发） | CustomView 仍无需眼镜端 SDK；CustomApp 需 **CXR-S**（`cxr-service-bridge`） |

---

## 3. 连接模型：最大差异（升级必看）

### 3.1 旧版（CXR-M）：本 App 直连、与官方 App 互斥

典型流程（与当前 `CxrConnectionManager` 一致）：

1. 本 App BLE 扫描（UUID `00009100-…`）
2. `CxrApi.initBluetooth(context, device, callback)` → 拿到 `socketUuid` / `macAddress`
3. `CxrApi.connectBluetooth(context, uuid, mac, callback)` → `onConnected`
4. 之后直接调用 CustomView / 亮度等 API

**用户侧约束（旧 README 已写明）**：

- 眼镜同一时间只能被一个 App 占用
- 连接前需断开官方 App 配对，或让眼镜进入配对模式

### 3.2 新版（CXR-L）：必须先连官方 App，再拿 token 建会话

典型流程：

1. 检测并引导安装 **Rokid AI App / Hi Rokid**
2. `AuthorizationHelper.requestAuthorization(...)` → OAuth 式授权 → 得到 **token**
3. 创建 `CXRLink`，`configCXRSession(CUSTOMVIEW | CUSTOMAPP)`
4. `link.connect(token)`
5. 等待**链路就绪**：`onCXRLConnected(true)` **且** `onGlassBtConnected(true)`
6. 再做**会话构建**：`customViewOpen` → `onCustomViewOpened`（或 CustomApp 的 `appStart`）

```
旧：本 App ──蓝牙──▶ 眼镜
新：本 App ──鉴权/会话──▶ Rokid AI App ──协同──▶ 眼镜
```

### 3.3 对 GlassesReader 的直接影响

| 旧体验 | 新体验（预期） |
| --- | --- |
| 「设备连接」页扫描选设备 | 先检测官方 App → 授权拿 token → `connect(token)` |
| 强调「断开官方 App 再连本 App」 | **反过来**：官方 App 是建链前置依赖，不能跳过 |
| 持久化 `socketUuid` + `mac` 做自动重连 | 改为持久化/刷新 **token**，并管理 `CXRLink` 生命周期 |
| `BluetoothHelper` 扫描仍可能有辅助价值 | 主路径不再依赖本 App 的 `initBluetooth` / `connectBluetooth` |

> 用户理解正确：**新 SDK 需要连接/协同官方应用才能与眼镜通信**。  
> 业务不变：**无障碍抓取文本 → CustomView 更新眼镜文字** 这一条链路仍然成立。

---

## 4. 能力对照（GlassesReader 相关）

### 4.1 核心能力：CustomView（文本投屏）— 能力保留，API 更名

| 能力 | CXR-M | CXR-L |
| --- | --- | --- |
| 打开 | `openCustomView(json)` | `customViewOpen(json)` |
| 更新 | `updateCustomView(jsonArray)` | `customViewUpdate(jsonArray)` |
| 关闭 | `closeCustomView()` | `customViewClose()` |
| 图标 | `sendCustomViewIcons(List<IconInfo>)` | `customViewSetIcons(String)`（JSON 数组字符串） |
| 是否打开 | （项目侧自行维护） | `customViewIsOpen()` |
| 回调 | `CustomViewListener`（`onOpened` / `onUpdated`…） | `ICustomViewCbk`（`onCustomViewOpened` / `onCustomViewUpdated`…） |
| 返回值 | `ValueUtil.CxrStatus` | 文档以回调为主；`connect` 等返回 Boolean |

**JSON 协议**：布局树（`LinearLayout` / `RelativeLayout` / `TextView` / `ImageView`）、增量 `action: update` + `id` + `props` 与旧版高度一致，现有布局构建逻辑可迁移，重点改调用入口与打开时机（须等链路就绪）。

### 4.2 设备控制：亮度 — 能力保留，入口与返回值变化

| 项 | CXR-M | CXR-L |
| --- | --- | --- |
| 设置亮度 | `CxrApi.setGlassBrightness` → `CxrStatus` | `CXRLink.setGlassBrightness` → `Boolean` |
| 设置音量 | `setGlassVolume` + `VolumeUpdateListener` | `setGlassVolume`，无独立 listener 文档 |
| 查询 | `getGlassInfo` / 亮度更新 listener | `getGlassDeviceInfo()` → `onGlassDeviceInfo(GlassInfo)` |
| 前置条件 | 蓝牙已连接 | **链路就绪**即可，**不要求** CustomView 已打开 |

### 4.3 音频 / 拍照 — 模型变化（本项目次要）

| 项 | CXR-M | CXR-L |
| --- | --- | --- |
| 拍照 | 蓝牙 + 常配合 Wi-Fi P2P / 文件同步 | 会话构建完成后走链路回调（JPEG 字节流） |
| 录音/音频 | `openAudioRecord` 等 | `startAudioStream` / PCM 流 |
| Wi-Fi P2P | 文档专章（`initWifiP2P` / 媒体同步） | **新文档无对等 Wi-Fi 专章** |
| AI 场景 | `AI 场景操作.md`（ASR/TTS 等） | **新文档未收录同名能力** |

GlassesReader 若继续做「眼镜拍照 + Wi-Fi 同步媒体」，需在 CXR-L 实测确认替代路径，不能假设旧 Wi-Fi API 仍存在。

### 4.4 新版新增、旧版无或弱的能力

| 能力 | 说明 | 与 GlassesReader 关系 |
| --- | --- | --- |
| 鉴权 `AuthorizationHelper` | 官方 App 授权拿 token | **升级必做** |
| 会话类型 CUSTOMVIEW / CUSTOMAPP | 建链前必须配置 | 投屏选 **CUSTOMVIEW** |
| CustomApp + CXR-S | 远程安装/启动眼镜端 APK、Caps 双向指令 | 当前投屏场景**不需要** |
| 按键与系统广播 | 镜腿/触控板事件上报 | 可选增强 |
| iOS SDK | `RGCxrClient` | 本仓库 Android 无关 |

---

## 5. 状态机对比（升级后心智模型）

### 旧版

```
扫描设备 → initBluetooth → connectBluetooth(onConnected)
  → openCustomView → updateCustomView（循环）
```

### 新版

```
检测官方 App → 鉴权拿 token
  → CXRLink + configCXRSession(CUSTOMVIEW) → connect(token)
  → 链路就绪（CXRL + GlassBt）
  → customViewOpen → onCustomViewOpened（会话构建完成）
  → customViewUpdate（循环投屏文本）
```

关键概念（新文档强调）：

| 概念 | 判定 |
| --- | --- |
| 链路就绪 | `onCXRLConnected(true)` ∧ `onGlassBtConnected(true)` |
| 会话构建完成（CustomView） | `onCustomViewOpened` |
| 可调亮度/音量 | 链路就绪即可 |
| 可拍照/音频 | 链路就绪 **且** 会话构建完成 |

---

## 6. 项目侧改造清单（按模块分步）

> **原则**：一次只改一个模块；每步可编译、可验证。  
> **AR 截图/录屏**：**暂不迁移、不删除**——若本 App 可与官方 App 同开，官方侧截图或可覆盖需求；等真机验证后再决定去留。

### 6.1 建议改造顺序

| 步骤 | 模块 | 目标 | 关键文件 | 优先级 |
| --- | --- | --- | --- | --- |
| **S0** | Gradle / 依赖 | `client-m` → `client-l:1.0.4`；minSdk 29→31 | `app/build.gradle.kts`、`settings.gradle.kts` | ✅ 已完成（依赖可解析；旧 API 待后续模块改，暂不能完整编译） |
| **S1** | 鉴权 | 检测 Rokid AI App / Hi Rokid；授权拿 token | **新建** `sdk/` 鉴权封装；权限/设置引导 UI | ✅ `CxrAuthManager` + 连接页授权 |
| **S2** | 连接与会话 | 用 `CXRLink` + `connect(token)` 替代直连蓝牙；链路就绪门控 | `CxrConnectionManager.kt`；`BluetoothHelper.kt`（降级）；`DeviceScanActivity.kt`；`MainActivity` 自动重连 | ✅ CXRLink CUSTOMVIEW；扫描页改为鉴权连接 |
| **S3** | CustomView 投屏 | `customViewOpen/Update/Close`；等链路就绪再建会话 | `CxrCustomViewManager.kt`；`TextOverlayService` / 显示设置调用方 | ✅ 已切 CXR-L API |
| **S4** | 亮度等设备控制 | `CXRLink.setGlassBrightness` 等 | `MainActivity`、`TextPresetManager`、显示控制组件 | ✅ 亮度已切；重启眼镜暂缓 |
| **S5** | 文案 / 引导 UX | 「断开官方 App」→「安装并授权官方 App」 | `SettingsScreen`、连接页、README/已知限制 | 🔄 设置页文案已改，其余可继续打磨 |
| **S6** | 权限清单裁剪 | 按新主路径补 INTERNET/鉴权相关；蓝牙扫描权限可后置裁剪 | `AndroidManifest.xml`、运行时权限 | 建议 |
| **—** | AR 截图 / 录屏 / Wi‑Fi P2P | **先不动、不删**；入口可先藏或保留但标明未迁 | 见 §6.3 | ✅ 入口 stub；原文归档 `legacy/ArCxrMMainActivity.snippet.txt` |

### 6.2 各模块对照（现状 → CXR-L）

| 模块 | 现状（CXR-M） | 升级方向（CXR-L） |
| --- | --- | --- |
| Gradle | `com.rokid.cxr:client-m:…` | ✅ 已改为 `com.rokid.cxr:client-l:1.0.4`；**minSdk = 31** |
| 鉴权 | 无 | `AuthorizationHelper` 拿 token；官方 App ≥ 1.9.0 |
| `CxrConnectionManager` | `initBluetooth` / `connectBluetooth` / UUID·MAC 持久化 | 鉴权 + `CXRLink.connect(token)` + `onCXRLConnected`∧`onGlassBtConnected` |
| `BluetoothHelper` | 主连接入口扫描 | 主路径不再依赖；可保留代码但退出主流程 |
| `CxrCustomViewManager` | `CxrApi.open/update/closeCustomView` | `CXRLink.customViewOpen/Update/Close`；链路就绪后再 open |
| 亮度 | `CxrApi.setGlassBrightness` | `CXRLink.setGlassBrightness`；链路就绪即可 |
| 权限引导 | 悬浮窗 / 无障碍 / 蓝牙 / 通知 | **新增**检测官方 App + 授权 |
| 文案 | 「须断开官方 App」 | 「须安装并保持官方 App，完成本 App 授权」 |

**本阶段明确不动（非 SDK 或可复用）**：

- 无障碍文本采集（`ScreenTextService` 等）
- 悬浮窗 / FAB 服务控制
- 文本处理选项、字号、预设等业务逻辑（仅改底层 SDK 调用）
- CustomView 布局 JSON 组装思路

### 6.3 AR 截图 / 录屏（暂缓，保留代码）

**决策（2026-08-21）**：升级 CXR-L 主路径时 **不迁移、不删除** AR 相关实现；待验证「与官方 App 并存时官方能否截当前画面」后再定。

涉及范围（仅作索引，改造期跳过）：

| 层级 | 主要位置 | 旧 API / 行为 |
| --- | --- | --- |
| 编排 | `MainActivity` 中 Wi‑Fi P2P、拍照、录屏、媒体同步 | `initWifiP2P` / `takeGlassPhoto` / `controlScene(VIDEO_RECORD)` / sync |
| 悬浮窗 | `TextOverlayService` AR 倒计时、录屏快门 | 与 CustomView 有交叉，改 S3 时保留方法签名即可 |
| 周边 | `recording/*`、叠图/导出工具、AR 广播 | 本地处理为主 |

> 注意：换成 `client-l` 后，这些旧 API 可能**编译失败**。处理方式优先：**用 `#` 注释 / 独立门面暂时 stub / 或把 AR 入口从 UI 藏起且相关调用包在未引用模块**——仍以「不删业务代码」为原则，具体在 S0 依赖切换时再定最小编译策略。

---

## 7. 用户体验变化摘要（写进产品说明用）

升级到 CXR-L 后，用户使用路径预期变为：

1. 安装 GlassesReader **以及** Rokid AI App（≥ 1.9.0）
2. 眼镜与手机按官方 App 要求完成配对/连接
3. 在 GlassesReader 内完成授权（跳转官方 App 拿 token）
4. 本 App 建立 CUSTOMVIEW 会话并打开自定义页面
5. 开启无障碍读屏 → 文字实时出现在眼镜上

与旧版相比：**不再要求「先踢掉官方 App」**；官方 App 成为通信链路的一部分。

---

## 8. 文档索引

| 内容 | 旧文档 | 新文档 |
| --- | --- | --- |
| 连接 | [设备连接.md](./doc/设备连接.md) | [07-连接与会话.md](./CXR%20L%20SDK/07-连接与会话.md)、[06-鉴权.md](./CXR%20L%20SDK/06-鉴权.md) |
| CustomView | [自定义页面场景.md](./doc/自定义页面场景.md) | [09-眼镜端自定义View.md](./CXR%20L%20SDK/09-眼镜端自定义View.md) |
| 设备控制 | [控制与监听设备状态.md](./doc/控制与监听设备状态.md) | [08-设备控制.md](./CXR%20L%20SDK/08-设备控制.md) |
| 总览 / 快速开始 | — | [01-简介.md](./CXR%20L%20SDK/01-简介.md)、[02-快速开始.md](./CXR%20L%20SDK/02-快速开始.md) |
| 新版目录 | — | [CXR L SDK/README.md](./CXR%20L%20SDK/README.md) |

---

## 9. 风险与待验证项

1. **minSdk 31**：当前工程为 29，升级依赖后需实测或提高最低系统版本。  
2. **token 有效期与自动重连**：旧版靠 UUID/MAC；新版自动重连策略需按官方 Sample 验证。  
3. **与官方 App 版本耦合**：client-l:1.0.4 要求 Rokid AI App ≥ 1.9.0。  
4. **拍照 / Wi-Fi / 媒体同步 / 录屏**：旧能力在新文档中缺失或形态改变，升级主路径（文本投屏）可先不迁这些功能。  
5. **独占连接语义消失后的冲突**：多 App 同时用眼镜时的实际表现需真机验证。
