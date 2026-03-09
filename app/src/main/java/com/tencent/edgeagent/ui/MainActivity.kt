package com.tencent.edgeagent.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.tencent.edgeagent.R
import com.tencent.edgeagent.service.EdgeAgentAccessibilityService
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 主界面 Activity
 */
class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    
    private lateinit var tvBuildTime: TextView
    private lateinit var tvState: TextView
    private lateinit var tvModelInfo: TextView
    private lateinit var tvCloudStatus: TextView
    private lateinit var tvLastResponse: TextView
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var btnOpenAccessibility: Button
    private lateinit var etCustomPrompt: EditText
    private lateinit var btnExecuteCustom: Button
    private lateinit var btnTest1: Button
    private lateinit var btnTest2: Button
    private lateinit var btnTest3: Button
    private lateinit var btnTest4: Button
    private lateinit var btnTest5: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        Timber.d("MainActivity 启动")
        
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        
        initViews()
        observeViewModel()
        checkAccessibilityPermission()
    }
    
    override fun onResume() {
        super.onResume()
        // 每次回到前台时检查权限状态
        checkAccessibilityPermission()
    }
    
    /**
     * 检查无障碍权限状态
     */
    private fun checkAccessibilityPermission() {
        val isEnabled = isAccessibilityServiceEnabled()
        
        if (isEnabled) {
            tvAccessibilityStatus.text = "✅ 无障碍权限已开启"
            tvAccessibilityStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            btnOpenAccessibility.visibility = android.view.View.GONE
        } else {
            tvAccessibilityStatus.text = "❌ 无障碍权限未开启"
            tvAccessibilityStatus.setTextColor(getColor(android.R.color.holo_red_dark))
            btnOpenAccessibility.visibility = android.view.View.VISIBLE
        }
    }
    
    /**
     * 检查无障碍服务是否已启用
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = EdgeAgentAccessibilityService.getInstance()
        return service != null
    }
    
    /**
     * 打开无障碍设置页面
     */
    private fun openAccessibilitySettings() {
        AlertDialog.Builder(this)
            .setTitle("开启无障碍权限")
            .setMessage("VisionAgent 需要无障碍权限来执行自动化操作。\n\n请手动前往：设置 -> 更多设置 -> 无障碍 -> VisionAgent")
            .setPositiveButton("知道了", null)
            .show()
    }
    
    private fun initViews() {
        tvBuildTime = findViewById(R.id.tv_build_time)
        tvState = findViewById(R.id.tv_state)
        tvModelInfo = findViewById(R.id.tv_model_info)
        tvCloudStatus = findViewById(R.id.tv_cloud_status)
        tvLastResponse = findViewById(R.id.tv_last_response)
        tvAccessibilityStatus = findViewById(R.id.tv_accessibility_status)
        btnOpenAccessibility = findViewById(R.id.btn_open_accessibility)
        etCustomPrompt = findViewById(R.id.et_custom_prompt)
        btnExecuteCustom = findViewById(R.id.btn_execute_custom)
        btnTest1 = findViewById(R.id.btn_test1)
        btnTest2 = findViewById(R.id.btn_test2)
        btnTest3 = findViewById(R.id.btn_test3)
        btnTest4 = findViewById(R.id.btn_test4)
        btnTest5 = findViewById(R.id.btn_test5)
        
        // 显示编译时间
        tvBuildTime.text = "编译时间: ${com.tencent.edgeagent.BuildConfig.BUILD_TIME}"
        
        // 无障碍权限按钮
        btnOpenAccessibility.setOnClickListener {
            openAccessibilitySettings()
        }
        
        // 自定义指令按钮
        btnExecuteCustom.setOnClickListener {
            val prompt = etCustomPrompt.text.toString().trim()
            if (prompt.isNotEmpty()) {
                viewModel.testInference(prompt)
            } else {
                AlertDialog.Builder(this)
                    .setTitle("提示")
                    .setMessage("请输入指令")
                    .setPositiveButton("确定", null)
                    .show()
            }
        }
        
        btnTest1.setOnClickListener {
            viewModel.testInference("点击屏幕中心")
        }
        
        btnTest2.setOnClickListener {
            viewModel.testInference("向上滑动")
        }
        
        btnTest3.setOnClickListener {
            viewModel.testInference("打开微信")
        }
        
        btnTest4.setOnClickListener {
            viewModel.testInference("打开美团")
        }
        
        btnTest5.setOnClickListener {
            viewModel.testInference("打开电话")
        }
    }
    
    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.agentState.collect { state ->
                tvState.text = "当前状态: ${state.name}"
            }
        }
        
        lifecycleScope.launch {
            viewModel.modelInfo.collect { info ->
                if (info != null) {
                    tvModelInfo.text = """
                        模型信息:
                        名称: ${info.name}
                        版本: ${info.version}
                        大小: ${info.sizeInMB} MB
                        多模态: ${if (info.supportsMultimodal) "是" else "否"}
                        平均推理时间: ${info.avgInferenceTimeMs} ms
                    """.trimIndent()
                }
            }
        }
        
        lifecycleScope.launch {
            viewModel.lastResponse.collect { response ->
                if (response != null) {
                    tvLastResponse.text = """
                        最后响应:
                        来源: ${response.source.name}
                        动作: ${response.action.name}
                        置信度: ${"%.2f".format(response.confidence)}
                        推理时间: ${response.inferenceTimeMs} ms
                    """.trimIndent()
                }
            }
        }
        
        lifecycleScope.launch {
            viewModel.cloudStatus.collect { status ->
                tvCloudStatus.text = "云端状态: $status"
            }
        }
    }
}
