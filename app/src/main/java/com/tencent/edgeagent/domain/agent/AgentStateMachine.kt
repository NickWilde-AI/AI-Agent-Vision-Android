package com.tencent.edgeagent.domain.agent

import com.tencent.edgeagent.domain.model.AgentEvent
import com.tencent.edgeagent.domain.model.AgentState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Agent 状态机（单例模式）
 */
class AgentStateMachine private constructor() {

    private val _currentState = MutableStateFlow(AgentState.IDLE)
    val currentState: StateFlow<AgentState> = _currentState.asStateFlow()

    private val _lastEvent = MutableStateFlow<AgentEvent?>(null)
    val lastEvent: StateFlow<AgentEvent?> = _lastEvent.asStateFlow()

    private val validTransitions = mapOf(
        AgentState.IDLE to setOf(
            AgentState.PERCEIVING,
            AgentState.ERROR
        ),
        AgentState.PERCEIVING to setOf(
            AgentState.REASONING_LOCAL,
            AgentState.ERROR,
            AgentState.IDLE
        ),
        AgentState.REASONING_LOCAL to setOf(
            AgentState.EXECUTING,
            AgentState.REASONING_CLOUD,
            AgentState.ERROR,
            AgentState.IDLE
        ),
        AgentState.REASONING_CLOUD to setOf(
            AgentState.EXECUTING,
            AgentState.ERROR,
            AgentState.IDLE
        ),
        AgentState.EXECUTING to setOf(
            AgentState.COMPLETED,
            AgentState.ERROR,
            AgentState.IDLE
        ),
        AgentState.COMPLETED to setOf(
            AgentState.IDLE
        ),
        AgentState.ERROR to setOf(
            AgentState.IDLE
        )
    )

    fun handleEvent(event: AgentEvent) {
        _lastEvent.value = event
        
        val newState = when (event) {
            is AgentEvent.UserTriggered -> {
                if (_currentState.value == AgentState.IDLE) {
                    AgentState.PERCEIVING
                } else {
                    Timber.w("收到用户触发事件，但当前状态不是 IDLE: ${_currentState.value}")
                    null
                }
            }
            
            is AgentEvent.PerceptionComplete -> {
                if (_currentState.value == AgentState.PERCEIVING) {
                    AgentState.REASONING_LOCAL
                } else {
                    Timber.w("收到感知完成事件，但当前状态不是 PERCEIVING: ${_currentState.value}")
                    null
                }
            }
            
            is AgentEvent.LocalReasoningComplete -> {
                if (_currentState.value == AgentState.REASONING_LOCAL) {
                    if (event.response.confidence >= CONFIDENCE_THRESHOLD) {
                        AgentState.EXECUTING
                    } else {
                        Timber.i("本地推理置信度不足 (${event.response.confidence}), 转云端兜底")
                        AgentState.REASONING_CLOUD
                    }
                } else {
                    Timber.w("收到本地推理完成事件，但当前状态不是 REASONING_LOCAL: ${_currentState.value}")
                    null
                }
            }
            
            is AgentEvent.CloudReasoningComplete -> {
                if (_currentState.value == AgentState.REASONING_CLOUD) {
                    AgentState.EXECUTING
                } else {
                    Timber.w("收到云端推理完成事件，但当前状态不是 REASONING_CLOUD: ${_currentState.value}")
                    null
                }
            }
            
            is AgentEvent.ExecutionComplete -> {
                if (_currentState.value == AgentState.EXECUTING) {
                    AgentState.COMPLETED
                } else {
                    Timber.w("收到执行完成事件，但当前状态不是 EXECUTING: ${_currentState.value}")
                    null
                }
            }
            
            is AgentEvent.Error -> {
                Timber.e(event.throwable, "Agent 错误: ${event.message}")
                AgentState.ERROR
            }
            
            is AgentEvent.Reset -> {
                AgentState.IDLE
            }
        }

        newState?.let { transitionTo(it) }
    }

    private fun transitionTo(newState: AgentState) {
        val currentState = _currentState.value
        
        if (isValidTransition(currentState, newState)) {
            Timber.d("状态转换: $currentState → $newState")
            _currentState.value = newState
            
            if (newState == AgentState.COMPLETED) {
                Timber.d("任务完成，自动回到 IDLE 状态")
                _currentState.value = AgentState.IDLE
            }
            
            if (newState == AgentState.ERROR) {
                Timber.d("错误状态，自动回到 IDLE 状态")
                _currentState.value = AgentState.IDLE
            }
        } else {
            Timber.e("非法状态转换: $currentState → $newState")
        }
    }

    private fun isValidTransition(from: AgentState, to: AgentState): Boolean {
        return validTransitions[from]?.contains(to) == true
    }

    fun reset() {
        Timber.d("重置状态机")
        _currentState.value = AgentState.IDLE
        _lastEvent.value = null
    }

    companion object {
        private const val CONFIDENCE_THRESHOLD = 0.75f
        
        @Volatile
        private var instance: AgentStateMachine? = null
        
        fun getInstance(): AgentStateMachine {
            return instance ?: synchronized(this) {
                instance ?: AgentStateMachine().also { instance = it }
            }
        }
    }
}
