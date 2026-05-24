package com.tencent.edgeagent.data.vision

import org.junit.Assert.assertTrue
import org.junit.Test

class BasicLocalVisionEngineTest {

    @Test
    fun observeUiTree_extractsVisibleAndClickableText() {
        val uiTree = """
            UI Tree with bounds:
            Clickable Elements (2):
              #1 label='搜索' class='TextView' id='search' bounds=[0,0,100,80] center=(50,40)
              #2 label='发送' class='Button' id='send' bounds=[900,2000,1040,2140] center=(970,2070)

            Node Tree:
              [android.widget.EditText] text='输入消息' bounds=[0,1900,800,2140] center=(400,2020) [editable]
              [android.widget.TextView] desc='联系人 Nick' bounds=[0,400,400,500] center=(200,450)
        """.trimIndent()

        val observation = BasicLocalVisionEngine.getInstance().observeUiTree(
            packageName = "com.tencent.mm",
            hasRealScreenshot = false,
            uiTreeText = uiTree
        )

        assertTrue(observation.visibleTexts.contains("输入消息"))
        assertTrue(observation.visibleTexts.contains("联系人 Nick"))
        assertTrue(observation.clickableLabels.contains("搜索"))
        assertTrue(observation.clickableLabels.contains("发送"))
        assertTrue(observation.editableHints.isNotEmpty())
    }
}
