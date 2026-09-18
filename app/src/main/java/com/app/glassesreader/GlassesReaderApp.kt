package com.app.glassesreader

import android.app.Application
import com.rokid.cxr.link.CXRLink

class GlassesReaderApp : Application() {

    companion object {
        /** 进程内复用的 CXR-L 链路（CUSTOMVIEW 会话） */
        @JvmStatic
        var sharedLink: CXRLink? = null

        fun resetSession() {
            sharedLink = null
        }
    }
}
