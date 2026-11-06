package com.zcodeandriod.app.agent

/**
 * 无障碍观察产物。id 是快照内稳定序号，phone_action 凭 id 回来定位节点。
 */
data class PhoneNode(
    val id: Int,
    val label: String,
    val className: String?,
    val boundsInScreen: IntArray, // [l, t, r, b]
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val nodeRef: android.view.accessibility.AccessibilityNodeInfo,
)

data class PhoneScreen(
    val packageName: String?,
    val width: Int,
    val height: Int,
    val nodes: List<PhoneNode>,
)
