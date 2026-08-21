# Rokid AR Platform — CXR-L SDK 文档（v1.0.4）

> 来源：https://custom.rokid.com/prod/rokid_web/84feb39f8ef141b0ad0326f902ab881f/pc/cn/3b63d21420e645e3affca478b39e4a13.html
> 提取日期：2026-08-18
> 原文版本：1.0.4（页面导航显示 relatedVersion 1.0.6）

本目录为 Rokid AR Platform 开发者文档「CXR-L SDK」的 Markdown 版，按原站章节拆分为独立文件。

## 文档目录

### 总览

| 序号 | 文件 | 内容 |
| --- | --- | --- |
| 01 | [01-简介.md](01-简介.md) | CXR-L SDK 简介：定位、核心能力、能力前置关系、能力可用矩阵、示例工程、CXR-S SDK 简介 |
| 02 | [02-快速开始.md](02-快速开始.md) | 快速开始：环境前提、获取 Sample、最小验证路径（Android/iOS）、CustomApp 联调路径 |
| 03 | [03-开发流程与状态机.md](03-开发流程与状态机.md) | 端到端开发流程、鉴权/链路/能力状态机、跨平台一致性 |
| 04 | [04-术语与缩写.md](04-术语与缩写.md) | 平台产品、鉴权连接、会话链路、能力缩写、Caps、CustomView JSON 术语 |

### 功能开发（Android）

| 序号 | 文件 | 内容 |
| --- | --- | --- |
| 05 | [05-SDK导入.md](05-SDK导入.md) | Android：SDK 导入（client-l）+ 眼镜端 CXR-S SDK 导入 |
| 06 | [06-鉴权.md](06-鉴权.md) | Android：鉴权（AuthorizationHelper、GlassPermission、AuthResult） |
| 07 | [07-连接与会话.md](07-连接与会话.md) | Android：连接与会话（CXRLink、会话配置、ICXRLinkCbk、链路就绪） |
| 08 | [08-设备控制.md](08-设备控制.md) | Android：设备控制（亮度/音量 0…15、GlassInfo） |
| 09 | [09-眼镜端自定义View.md](09-眼镜端自定义View.md) | Android：眼镜端自定义 View（含完整 JSON Schema） |
| 10 | [10-眼镜端自定义应用.md](10-眼镜端自定义应用.md) | Android：眼镜端自定义应用（安装/启动/停止/卸载） |
| 11 | [11-音频.md](11-音频.md) | Android：音频（PCM 16kHz 单声道 16bit） |
| 12 | [12-拍照.md](12-拍照.md) | Android：拍照（JPEG 字节流） |
| 13 | [13-自定义指令.md](13-自定义指令.md) | Android：自定义指令（Caps 双向通信、CXRServiceBridge） |
| 14 | [14-眼镜端按键与系统广播.md](14-眼镜端按键与系统广播.md) | Android：眼镜端按键与系统广播（KeyType action 表） |

## 阅读建议

- 从 [01-简介](01-简介.md) 了解能力边界与前置关系，尤其注意「会话构建」与「链路就绪」两个关键概念。
- 按 [02-快速开始](02-快速开始.md) 走通最小验证路径。
- 集成顺序建议：SDK 导入 → 鉴权 → 连接与会话 → 按需选读功能专章。

## 关键概念速查

| 概念 | 判定条件 |
| --- | --- |
| 链路就绪 | `onCXRLConnected(true)` 且 `onGlassBtConnected(true)` |
| 会话构建完成（CustomView） | `onCustomViewOpened` |
| 会话构建完成（CustomApp） | `onOpenAppResult(true)` 或 `onGlassAppResume(true)` |
| 音频 / 拍照 | 链路就绪 + 会话构建完成 |
| 自定义指令 | 仅 CUSTOMAPP 会话 + 眼镜端应用已打开 |
| 设备控制 | 仅需链路就绪，无需会话构建 |

## 提取说明

- 原文中的 Mermaid 流程图 / 状态图 / 时序图已转换为代码块保留。
- 每页底部的「上一篇 / 下一篇」导航、「去论坛反馈」等页面框架内容已去除。
- 原「自定义指令」页面在网页中存在重复渲染，已去重合并。
