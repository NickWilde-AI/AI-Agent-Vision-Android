package com.tencent.edgeagent.ui

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.tencent.edgeagent.R
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 主界面 Activity
 */
class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    
    private lateinit var tvState: TextView
    private lateinit var tvModelInfo: TextView
    private lateinit var tvCloudStatus: TextView
    private lateinit var tvLastResponse: TextView
    private lateinit var tvLog: TextView
    private lateinit var btnTest1: Button
    private lateinit var btnTest2: Button
    private lateinit var btnTest3: Button
    private lateinit var btnTest4: Button
    
    private val logBuilder = StringBuilder()
    private fun addLog(message: String) {
        logBuilder.append("${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())} - $message\n")
        tvLog.text = logBuilder.toString()
        // 自动滚动到底部
        tvLog.post {
            val scrollView = tvLog.parent as? android.widget.ScrollView
            scrollView?.fullScroll(android.view.View.FOCUS_DOWN)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        Timber.d("MainActivity 启动")
        
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        
        initViews()
        observeViewModel()
    }
    
    private fun initViews() {
        tvState = findViewById(R.id.tv_state)
        tvModelInfo = findViewById(R.id.tv_model_info)
        tvCloudStatus = findViewById(R.id.tv_cloud_status)
        tvLastResponse = findViewById(R.id.tv_last_response)
        tvLog = findViewById(R.id.tv_log)
        btnTest1 = findViewById(R.id.btn_test1)
        btnTest2 = findViewById(R.id.btn_test2)
        btnTest3 = findViewById(R.id.btn_test3)
        btnTest4 = findViewById(R.id.btn_test4)
        
        btnTest1.setOnClickListener {
            addLog("🔵 用户点击: 点击屏幕中心")
            viewModel.testInference("点击屏幕中心")
        }
        
        btnTest2.setOnClickListener {
            addLog("🔵 用户点击: 向上滑动")
            viewModel.testInference("向上滑动")
        }
        
        btnTest3.setOnClickListener {
            addLog("🔵 用户点击: 打开 Chrome")
            viewModel.testInference("打开Chrome")
        }
        
        btnTest4.setOnClickListener {
            addLog("🔵 用户点击: 打开 YouTube")
            viewModel.testInference("打开YouTube")
        }
    }
    
    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.agentState.collect { state ->
                tvState.text = "当前状态: ${state.name}"
                addLog("📊 状态变化: ${state.name}")
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
                    addLog("✅ 模型加载完成: ${info.name}")
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
                    addLog("🎯 推理完成: ${response.action.name}, 置信度=${String.format("%.2f", response.confidence)}")
                }
            }
        }
        
        lifecycleScope.launch {
            viewModel.executionResult.collect { result ->
                if (result != null) {
                    addLog(result)
                }
            }
        }
        
        lifecycleScope.launch {
            viewModel.cloudStatus.collect { status ->
                tvCloudStatus.text = "云端状态: $status"
                addLog("☁️ 云端状态: $status")
            }
        }
    }
}
