package com.tencent.edgeagent.domain.agent

import com.tencent.edgeagent.domain.model.ActionParams
import com.tencent.edgeagent.domain.model.ActionType
import com.tencent.edgeagent.domain.model.DeviceControlType
import com.tencent.edgeagent.domain.model.InferenceSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class L1CommandRouterTest {

    private val router = L1CommandRouter.getInstance()

    @Test
    fun resolve_mapsVolumeUpToDeviceControl() {
        val response = router.resolve("调高音量")

        assertNotNull(response)
        assertEquals(InferenceSource.LOCAL_RAG, response?.source)
        assertEquals(ActionType.DEVICE_CONTROL, response?.action)
        val params = response?.actionParams as ActionParams.DeviceControl
        assertEquals(DeviceControlType.VOLUME_UP, params.controlType)
    }

    @Test
    fun resolve_mapsOpenCameraToOpenApp() {
        val response = router.resolve("打开相机")

        assertNotNull(response)
        assertEquals(ActionType.OPEN_APP, response?.action)
        val params = response?.actionParams as ActionParams.OpenApp
        assertEquals("com.android.camera", params.packageName)
    }

    @Test
    fun resolve_mapsWifiCommandToSettingsEntry() {
        val response = router.resolve("连接 WiFi")

        assertNotNull(response)
        assertEquals(ActionType.DEVICE_CONTROL, response?.action)
        val params = response?.actionParams as ActionParams.DeviceControl
        assertEquals(DeviceControlType.WIFI_TOGGLE, params.controlType)
    }

    @Test
    fun resolve_mapsHomeCommandToHomeAction() {
        val response = router.resolve("回到桌面")

        assertNotNull(response)
        assertEquals(ActionType.HOME, response?.action)
    }

    @Test
    fun resolve_mapsCloseKeyboardToBackAction() {
        val response = router.resolve("关闭键盘")

        assertNotNull(response)
        assertEquals(ActionType.BACK, response?.action)
    }

    @Test
    fun resolve_mapsMiuiBrowserToBrowserPackage() {
        val response = router.resolve("打开浏览器")

        assertNotNull(response)
        assertEquals(ActionType.OPEN_APP, response?.action)
        val params = response?.actionParams as ActionParams.OpenApp
        assertEquals("com.android.browser", params.packageName)
    }

    @Test
    fun resolve_doesNotTakeOverWechatMessageTask() {
        val response = router.resolve("打开微信给 Nick 发送消息：你好")

        assertNull(response)
    }
}
