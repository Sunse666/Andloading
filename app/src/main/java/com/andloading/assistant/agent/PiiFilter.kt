package com.andloading.assistant.agent

import com.andloading.assistant.data.SettingsStore

object PiiFilter {

    data class Options(
        val phone: Boolean = true,
        val idCard: Boolean = true,
        val bankCard: Boolean = true,
        val email: Boolean = true,
    ) {
        val anyEnabled: Boolean
            get() = phone || idCard || bankCard || email
    }

    fun fromSettings(settings: SettingsStore): Options = Options(
        phone = settings.piiSanitizePhone,
        idCard = settings.piiSanitizeIdCard,
        bankCard = settings.piiSanitizeBankCard,
        email = settings.piiSanitizeEmail,
    )

    private val PHONE = Regex("(?<!\\d)1[3-9]\\d{9}(?!\\d)")
    private val ID_CARD = Regex("(?<!\\d)\\d{17}[\\dXx](?!\\d)")
    private val BANK_CARD = Regex("(?<!\\d)\\d{16,19}(?!\\d)")
    private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")

    fun sanitize(text: String, options: Options): String {
        if (!options.anyEnabled || text.isEmpty()) return text
        var out = text
        if (options.email) out = out.replace(EMAIL, "[邮箱]")
        if (options.idCard) out = out.replace(ID_CARD, "[身份证]")
        if (options.bankCard) out = out.replace(BANK_CARD, "[银行卡]")
        if (options.phone) out = out.replace(PHONE, "[手机号]")
        return out
    }
}
