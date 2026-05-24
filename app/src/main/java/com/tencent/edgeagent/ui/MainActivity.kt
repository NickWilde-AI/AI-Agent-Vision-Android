package com.tencent.edgeagent.ui

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tencent.edgeagent.BuildConfig
import com.tencent.edgeagent.R
import com.tencent.edgeagent.domain.model.AgentState
import com.tencent.edgeagent.service.EdgeAgentAccessibilityService
import com.tencent.edgeagent.service.ScreenCaptureService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 主界面 Activity
 *
 * 职责：
 * 1. 展示 Agent 运行状态
 * 2. 权限管理（无障碍服务 + 屏幕录制）
 * 3. 接收用户指令并触发执行
 * 4. 管理 MediaProjection 屏幕录制授权流程
 */
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    // Views
    private lateinit var tvBuildTime: TextView
    private lateinit var tvAccessibilityDot: View
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var btnOpenAccessibility: TextView
    private lateinit var tvCaptureDot: View
    private lateinit var tvScreenCaptureStatus: TextView
    private lateinit var btnRequestScreenCapture: TextView
    private lateinit var tvModelInfo: TextView
    private lateinit var btnModelHealthCheck: Button
    private lateinit var tvCloudStatus: TextView
    private lateinit var tvState: TextView
    private lateinit var tvExecutionResult: TextView
    private lateinit var tvLastResponse: TextView
    private lateinit var etCustomPrompt: EditText
    private lateinit var btnExecuteCustom: Button
    private lateinit var btnTest1: Button
    private lateinit var btnTest2: Button
    private lateinit var btnTest3: Button
    private lateinit var btnTest4: Button
    private lateinit var btnTest5: Button

    // MediaProjection 授权结果
    private val screenCapturePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            Timber.i("屏幕录制授权成功")
            // 启动屏幕录制前台服务
            ScreenCaptureService.start(this, result.resultCode, result.data!!)
            updateScreenCaptureStatus(true)
        } else {
            Timber.w("屏幕录制授权被拒绝")
            updateScreenCaptureStatus(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupBuildTime()
        setupClickListeners()
        observeViewModel()

        Timber.d("MainActivity 创建")
    }

    override fun onResume() {
        super.onResume()
        // 每次回到前台刷新权限状态（用户可能去设置页面开了权限）
        refreshPermissionStatus()
    }

    // ────────────────────────────────────────────
    // 绑定 Views
    // ────────────────────────────────────────────

    private fun bindViews() {
        tvBuildTime = findViewById(R.id.tv_build_time)
        tvAccessibilityDot = findViewById(R.id.tv_accessibility_dot)
        tvAccessibilityStatus = findViewById(R.id.tv_accessibility_status)
        btnOpenAccessibility = findViewById(R.id.btn_open_accessibility)
        tvCaptureDot = findViewById(R.id.tv_capture_dot)
        tvScreenCaptureStatus = findViewById(R.id.tv_screen_capture_status)
        btnRequestScreenCapture = findViewById(R.id.btn_request_screen_capture)
        tvModelInfo = findViewById(R.id.tv_model_info)
        btnModelHealthCheck = findViewById(R.id.btn_model_health_check)
        tvCloudStatus = findViewById(R.id.tv_cloud_status)
        tvState = findViewById(R.id.tv_state)
        tvExecutionResult = findViewById(R.id.tv_execution_result)
        tvLastResponse = findViewById(R.id.tv_last_response)
        etCustomPrompt = findViewById(R.id.et_custom_prompt)
        btnExecuteCustom = findViewById(R.id.btn_execute_custom)
        btnTest1 = findViewById(R.id.btn_test1)
        btnTest2 = findViewById(R.id.btn_test2)
        btnTest3 = findViewById(R.id.btn_test3)
        btnTest4 = findViewById(R.id.btn_test4)
        btnTest5 = findViewById(R.id.btn_test5)
    }

    // ────────────────────────────────────────────
    // Build 时间
    // ────────────────────────────────────────────

    private fun setupBuildTime() {
        val buildTime = try {
            val buildTimeMs = BuildConfig.BUILD_TIME
            val sdf = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
            sdf.format(Date(buildTimeMs))
        } catch (e: Exception) {
            "未知"
        }
        tvBuildTime.text = "Build: $buildTime"
    }

    // ────────────────────────────────────────────
    // 点击事件
    // ────────────────────────────────────────────

    private fun setupClickListeners() {
        // 去开启无障碍
        btnOpenAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // 去授权屏幕录制
        btnRequestScreenCapture.setOnClickListener {
            requestScreenCapturePermission()
        }

        btnModelHealthCheck.setOnClickListener {
            viewModel.runLocalModelHealthCheck()
        }

        // 执行自定义指令
        btnExecuteCustom.setOnClickListener {
            val prompt = etCustomPrompt.text.toString().trim()
            if (prompt.isNotEmpty()) {
                hideKeyboard()
                viewModel.executeCommand(prompt)
            }
        }

        // 快捷测试
        btnTest1.setOnClickListener { viewModel.executeCommand("点击屏幕中心") }
        btnTest2.setOnClickListener { viewModel.executeCommand("向上滑动") }
        btnTest3.setOnClickListener { viewModel.executeCommand("打开微信") }
        btnTest4.setOnClickListener { viewModel.executeCommand("打开美团") }
        btnTest5.setOnClickListener { viewModel.executeCommand("打开电话") }
    }

    // ────────────────────────────────────────────
    // 观察 ViewModel
    // ────────────────────────────────────────────

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.agentState.collectLatest { state ->
                updateStateUI(state)
            }
        }

        lifecycleScope.launch {
            viewModel.lastResponse.collectLatest { response ->
                if (response != null) {
                    tvLastResponse.text = buildString {
                        append("action: ${response.action}\n")
                        append("confidence: ${"%,.2f".format(response.confidence)}\n")
                        append("source: ${response.source}\n")
                        if (response.rawOutput != null) {
                            append("output: ${response.rawOutput.take(120)}")
                        }
                    }
                } else {
                    tvLastResponse.text = "上次响应: 无"
                }
            }
        }

        lifecycleScope.launch {
            viewModel.modelInfo.collectLatest { info ->
                tvModelInfo.text = info?.name ?: "MockVLM"
            }
        }

        lifecycleScope.launch {
            viewModel.executionResult.collectLatest { result ->
                if (result != null) {
                    tvExecutionResult.text = result
                }
            }
        }

        lifecycleScope.launch {
            viewModel.cloudStatus.collectLatest { status ->
                tvCloudStatus.text = status
            }
        }
    }

    // ────────────────────────────────────────────
    // 状态 UI 更新
    // ────────────────────────────────────────────

    private fun updateStateUI(state: AgentState) {
        tvState.text = state.name
        val isIdle = state == AgentState.IDLE || state == AgentState.COMPLETED || state == AgentState.ERROR
        btnExecuteCustom.isEnabled = isIdle
        btnTest1.isEnabled = isIdle
        btnTest2.isEnabled = isIdle
        btnTest3.isEnabled = isIdle
        btnTest4.isEnabled = isIdle
        btnTest5.isEnabled = isIdle
        btnModelHealthCheck.isEnabled = isIdle
    }

    // ────────────────────────────────────────────
    // 权限状态刷新
    // ────────────────────────────────────────────

    private fun refreshPermissionStatus() {
        // 无障碍服务
        val accessibilityEnabled = EdgeAgentAccessibilityService.getInstance() != null
        updateAccessibilityStatus(accessibilityEnabled)

        // 屏幕录制服务
        val screenCaptureEnabled = ScreenCaptureService.getInstance() != null
        updateScreenCaptureStatus(screenCaptureEnabled)
    }

    private fun updateAccessibilityStatus(enabled: Boolean) {
        if (enabled) {
            tvAccessibilityDot.setBackgroundResource(R.drawable.dot_green)
            tvAccessibilityStatus.text = "已开启"
            tvAccessibilityStatus.setTextColor(getColor(R.color.va_green))
            btnOpenAccessibility.visibility = View.GONE
        } else {
            tvAccessibilityDot.setBackgroundResource(R.drawable.dot_red)
            tvAccessibilityStatus.text = "未开启"
            tvAccessibilityStatus.setTextColor(getColor(R.color.va_red))
            btnOpenAccessibility.visibility = View.VISIBLE
        }
    }

    private fun updateScreenCaptureStatus(enabled: Boolean) {
        if (enabled) {
            tvCaptureDot.setBackgroundResource(R.drawable.dot_green)
            tvScreenCaptureStatus.text = "已授权"
            tvScreenCaptureStatus.setTextColor(getColor(R.color.va_green))
            btnRequestScreenCapture.visibility = View.GONE
        } else {
            tvCaptureDot.setBackgroundResource(R.drawable.dot_red)
            tvScreenCaptureStatus.text = "未授权"
            tvScreenCaptureStatus.setTextColor(getColor(R.color.va_red))
            btnRequestScreenCapture.visibility = View.VISIBLE
        }
    }

    // ────────────────────────────────────────────
    // MediaProjection 授权
    // ────────────────────────────────────────────

    private fun requestScreenCapturePermission() {
        val mediaProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCapturePermissionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    // ────────────────────────────────────────────
    // 工具方法
    // ────────────────────────────────────────────

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }
}
