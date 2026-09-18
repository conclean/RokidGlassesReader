package com.app.glassesreader.utils

/**
 * 连接引导短文案（不申请蓝牙权限，仅提示）。
 */
object ConnectionHints {
    /** 请打开官方 App */
    const val OPEN_OFFICIAL_APP = "请先打开官方应用"

    /** 官方 App 与眼镜需蓝牙 */
    const val BLUETOOTH_FOR_OFFICIAL = "请确认手机蓝牙已开"

    /** 连接失败时的短提示（Toast / 状态行） */
    const val ON_CONNECT_FAILED = "请打开官方应用，并确认蓝牙已开后再试"

    /** 自动重连失败弹窗正文 */
    const val AUTO_RECONNECT_FAILED_DIALOG =
        "请打开官方应用，确认蓝牙已开后，再前往连接页重试。"
}
