package com.zcodeandriod.app.agent

/**
 * 手机操作安全策略（dsh-go PhonePermissionPolicy 的简化移植）：
 * - 受保护系统包一律拒绝动作（back/home 除外）
 * - 支付/银行上下文的节点拒绝点击与长按
 * - 疑似凭据的文本先脱敏再给模型
 */
object PhonePolicy {

    private val PROTECTED_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.miui.securitycenter",
    )

    // 支付/银行：包名与界面关键词双拦（中英）
    private val PAYMENT_PACKAGE_REGEX = Regex(
        "(?i)(alipay|wallet|bank| UNIONPAY |unionpay|tenpay|wechatpay|ninebot.*pay|com\\..*\\.pay|.*banks?|icbc|cmb|ccb|abc|boc|psbc|citic|spdb|ceb|cmbc|pingan)",
    )
    private val PAYMENT_LABEL_REGEX = Regex(
        "(?i)(付款|收款|转账|支付|余额宝|银行卡|支付密码|confirm payment|pay now|checkout|transfer money|payment)",
    )
    private val SENSITIVE_LABEL_REGEX = Regex(
        "(?i)(password|passwd|pwd|密码|验证码|captcha|otp|cvv|cvc|身份证|secret|token)",
    )

    fun isProtectedPackage(pkg: String?): Boolean = pkg in PROTECTED_PACKAGES

    fun isPaymentContext(pkg: String?, label: String): Boolean =
        (pkg != null && PAYMENT_PACKAGE_REGEX.containsMatchIn(pkg)) ||
            PAYMENT_LABEL_REGEX.containsMatchIn(label)

    /** 敏感节点标签脱敏后给模型 */
    fun redact(label: String): String =
        if (SENSITIVE_LABEL_REGEX.containsMatchIn(label)) "[redacted]" else label

    /** 观察是否允许：受保护包整屏只给包名不给树，防凭据页泄漏 */
    fun observeRefusal(pkg: String?): String? =
        if (isProtectedPackage(pkg)) "protected_window: $pkg 拒绝读取内容" else null
}
