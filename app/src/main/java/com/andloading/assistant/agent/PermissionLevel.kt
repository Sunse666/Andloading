package com.andloading.assistant.agent

enum class PermissionLevel {
    ALLOW,
    ASK,
    FORBID;

    companion object {
        fun fromDangerous(dangerous: Boolean): PermissionLevel =
            if (dangerous) ASK else ALLOW
    }
}
