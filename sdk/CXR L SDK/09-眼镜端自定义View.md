# Android：眼镜端自定义 View

> 文档版本：1.0.4
> 功能开发 > Android

## 概述

在 CUSTOMVIEW 会话下，通过手机向眼镜下发**布局 JSON** 与**图标资源（Base64 PNG）**，在眼镜端渲染界面，并支持**打开 / 增量更新 / 关闭**。完成后即完成 CustomView 会话的会话构建，此后可调用音频、拍照能力。

> **CustomApp 会话不支持自定义 View API；两者不可混用。**

## 前置条件

- 已完成[连接与会话](/07-连接与会话.md)配置，CXRLink 配置为 CUSTOMVIEW 且 `connect(token)` 已调用。
- 已注册 `ICustomViewCbk`。
- 若布局含 ImageView，须先通过 `customViewSetIcons` 下发对应图标。

## 核心 API

| 方法 | 说明 |
| --- | --- |
| `setCXRCustomViewCbk(ICustomViewCbk)` | 注册自定义视图回调 |
| `customViewSetIcons(String)` | 传入图标列表 JSON 数组字符串 |
| `customViewOpen(String)` | 传入完整视图树 JSON，首次打开 |
| `customViewUpdate(String)` | 传入增量更新 JSON 数组 |
| `customViewClose()` | 关闭当前自定义视图 |
| `customViewIsOpen()` | 查询视图是否仍处于打开态 |

## 回调 ICustomViewCbk

| 回调 | 说明 |
| --- | --- |
| onCustomViewOpened() | 视图已在眼镜端打开；会话构建完成 |
| onCustomViewUpdated() | 增量更新已应用 |
| onCustomViewClosed() | 视图已关闭 |
| onCustomViewIconsSent() | 图标下发成功 |
| onCustomViewError(code, msg?) | 错误；应清除「已打开」状态 |

## 推荐调用顺序

```mermaid
flowchart LR
    linkReady[cxrl_and_bt_ready] --> icons[customViewSetIcons]
    icons --> open[customViewOpen]
    open --> update[customViewUpdate_optional]
    update --> close[customViewClose]
```

1. **链路就绪**：`onCXRLConnected(true)` 且 `onGlassBtConnected(true)`。
2. **调用 `customViewSetIcons` 下发图标** — 布局含 ImageView 时必需；须在链路就绪后调用。
3. **调用 `customViewOpen` 下发完整视图树** — 同样须在链路就绪后调用。
4. **收到 `onCustomViewOpened` 后**，可进入音频、拍照等子能力。
5. 需要局部刷新时调用 `customViewUpdate`（建议视图已打开后）。
6. 页面或会话结束时调用 `customViewClose`。

## 约束

- ImageView 的 `name` 必须与图标列表中对应项的 `name` 一致。
- 每个节点 `props.id` 唯一，增量更新通过 id 定位节点。
- JSON 字段名、单位（dp / sp）须与协议一致。
- 须在 `onCustomViewOpened` 之后调用音频、拍照。
- `customViewSetIcons` 与 `customViewOpen` 均须在 CXRL 连通且蓝牙就绪后调用。

---

## CustomView JSON Schema

### 1. 视图树结构（customViewOpen）

每个节点为 JSON 对象：

```json
{
  "type": "LinearLayout",
  "props": { "id": "root", "layout_width": "match_parent", "layout_height": "match_parent" },
  "children": []
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| type | string | 是 | 节点类型，见下表 |
| props | object | 是 | 节点属性对象 |
| children | array | 否 | 子节点数组，递归同结构；叶子节点可省略 |

支持的 type 值：

| type | 说明 |
| --- | --- |
| LinearLayout | 线性布局容器 |
| RelativeLayout | 相对布局容器 |
| TextView | 文本 |
| ImageView | 图片（引用已下发图标） |

### 2. 公共布局字段

以下字段在多种 type 的 props 中通用：

| 字段 | 类型 | 必填 | 取值 / 格式 | 说明 |
| --- | --- | --- | --- | --- |
| id | string | 是 | [a-zA-Z0-9_]+ | 节点唯一标识，用于 customViewUpdate |
| layout_width | string | 是 | match_parent \| wrap_content \| {N}dp | 宽度 |
| layout_height | string | 是 | 同上 | 高度 |
| marginStart | string | 否 | {N}dp | 起始外边距 |
| marginEnd | string | 否 | {N}dp | 结束外边距 |
| marginTop | string | 否 | {N}dp | 上外边距 |
| marginBottom | string | 否 | {N}dp | 下外边距 |
| paddingStart | string | 否 | {N}dp | 起始内边距 |
| paddingEnd | string | 否 | {N}dp | 结束内边距 |
| paddingTop | string | 否 | {N}dp | 上内边距 |
| paddingBottom | string | 否 | {N}dp | 下内边距 |
| backgroundColor | string | 否 | #RRGGBB 或 #AARRGGBB | 背景色；眼镜端按单绿色通道风格渲染 |

> **单位约定**：尺寸值须带 `dp` 或 `sp` 后缀；纯数字在部分客户端可自动补 dp，建议始终显式带单位。
>
> **颜色约定**：传入 `#RRGGBB` 或 `#AARRGGBB`；眼镜端会将色值映射为以绿色为主的显示效果，勿假设全彩还原。

### 3. LinearLayout props

| 字段 | 类型 | 必填 | 取值 | 说明 |
| --- | --- | --- | --- | --- |
| orientation | string | 是 | vertical \| horizontal | 排列方向 |
| gravity | string | 否 | center \| center_vertical \| center_horizontal \| top \| bottom \| start \| end | 子元素对齐 |
| layout_gravity | string | 否 | 同上 | 在父容器中的对齐 |

> LinearLayout 序列化时 `paddingStart` / `paddingEnd` 在 JSON 中对应键名 `paddingLeft` / `paddingRight`。

### 4. RelativeLayout props

除公共布局字段外，还支持相对定位约束：

| 字段 | 类型 | 必填 | 取值 | 说明 |
| --- | --- | --- | --- | --- |
| layout_toStartOf | string | 否 | 目标节点 id | 位于目标起始侧 |
| layout_toEndOf | string | 否 | 目标节点 id | 位于目标结束侧 |
| layout_above | string | 否 | 目标节点 id | 位于目标上方 |
| layout_below | string | 否 | 目标节点 id | 位于目标下方 |
| layout_alignStart | string | 否 | 目标节点 id | 与目标起始对齐 |
| layout_alignEnd | string | 否 | 目标节点 id | 与目标结束对齐 |
| layout_alignTop | string | 否 | 目标节点 id | 与目标顶部对齐 |
| layout_alignBottom | string | 否 | 目标节点 id | 与目标底部对齐 |
| layout_alignBaseLine | string | 否 | 目标节点 id | 与目标基线对齐 |
| layout_aliginParentStart | string | 否 | true \| false | 对齐父容器起始（协议键名保留此拼写） |
| layout_aliginParentEnd | string | 否 | true \| false | 对齐父容器结束 |
| layout_aliginParentTop | string | 否 | true \| false | 对齐父容器顶部 |
| layout_aliginParentBottom | string | 否 | true \| false | 对齐父容器底部 |
| layout_centerInParent | string | 否 | true \| false | 在父容器中居中 |
| layout_centerHorizontal | string | 否 | true \| false | 水平居中 |
| layout_centerVertical | string | 否 | true \| false | 垂直居中 |

> 相对约束中的目标值均为同树内其他节点的 `props.id`。

### 5. TextView props

| 字段 | 类型 | 必填 | 取值 | 说明 |
| --- | --- | --- | --- | --- |
| text | string | 是 | 任意文本 | 显示内容 |
| textColor | string | 否 | #RRGGBB 或 #AARRGGBB | 文字颜色 |
| textSize | string | 否 | {N}sp | 字号 |
| gravity | string | 否 | center \| center_vertical \| center_horizontal \| start \| end \| top \| bottom | 文本对齐 |
| textStyle | string | 否 | bold \| italic \| bold_italic | 字体样式 |

### 6. ImageView props

| 字段 | 类型 | 必填 | 取值 | 说明 |
| --- | --- | --- | --- | --- |
| name | string | 是 | 与图标列表 name 一致 | 引用 customViewSetIcons 下发的图标 |
| scaleType | string | 否 | center \| center_crop \| center_inside \| fit_center \| fit_end \| fit_start \| fit_xy \| matrix | 缩放模式，默认 center |

### 7. 图标资源（customViewSetIcons）

传入 JSON 数组字符串：

```json
[
  { "name": "icon1", "data": "<Base64 编码的 PNG>" },
  { "name": "icon2", "data": "<Base64 编码的 PNG>" }
]
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| name | string | 是 | 图标标识，与 ImageView.props.name 对应 |
| data | string | 是 | PNG 图片的 Base64 字符串（建议 Base64.NO_WRAP） |

### 8. 增量更新（customViewUpdate）

传入 JSON 数组字符串，每项描述一个节点的变更：

```json
[
  {
    "action": "update",
    "id": "textView",
    "props": {
      "text": "Hello Rokid 1"
    }
  },
  {
    "action": "update",
    "id": "imageView",
    "props": {
      "name": "icon2"
    }
  }
]
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| action | string | 是 | 固定为 "update" |
| id | string | 是 | 目标节点 props.id |
| props | object | 是 | 仅包含需要变更的字段键值对 |

> 单次调用可携带多条更新；切换 ImageView 图标时，新 name 对应的图标须已通过 `customViewSetIcons` 下发。

### 9. 完整示例

**customViewOpen 示例：**

```json
{
  "type": "LinearLayout",
  "props": {
    "id": "root",
    "layout_width": "match_parent",
    "layout_height": "match_parent",
    "marginTop": "160dp",
    "marginBottom": "80dp",
    "backgroundColor": "#FF000000",
    "orientation": "vertical",
    "gravity": "center_horizontal"
  },
  "children": [
    {
      "type": "TextView",
      "props": {
        "id": "textView",
        "layout_width": "wrap_content",
        "layout_height": "wrap_content",
        "text": "Hello World",
        "textColor": "#00FF00",
        "textSize": "16sp",
        "gravity": "center",
        "textStyle": "bold",
        "paddingStart": "16dp",
        "paddingEnd": "16dp"
      }
    },
    {
      "type": "ImageView",
      "props": {
        "id": "imageView",
        "layout_width": "120dp",
        "layout_height": "120dp",
        "name": "icon1",
        "scaleType": "center"
      }
    }
  ]
}
```

**customViewUpdate 示例：**

```json
[
  {
    "action": "update",
    "id": "textView",
    "props": { "text": "Hello Rokid 1" }
  },
  {
    "action": "update",
    "id": "imageView",
    "props": { "name": "icon2" }
  }
]
```

## 下一步

会话构建完成后，可继续集成[音频](/10-音频.md)与[拍照](/11-拍照.md)。CustomView 会话不支持自定义指令。

## 附录：对照工程

`RenewCXRLSample` 中 `SessionHubViewModel` 演示打开/更新/关闭流程，`dataBean/selfView/` 目录提供 JSON 构建辅助类。
