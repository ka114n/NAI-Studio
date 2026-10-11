package com.kallan.naistudio.services

/** Remaining quota for the header; never infer a total from the current balance. */
data class BalanceAllowance(val labelKey: String, val percent: Int?, val unlimited: Boolean = false) {
    companion object {
        fun resolve(account: AccountSummary, gateway: Boolean, quota: GatewayQuota?): BalanceAllowance? {
            if (gateway) {
                if (quota == null) return BalanceAllowance("balance.quotaRemaining", null)
                if (quota.imageModelScope == "all" && !quota.v5Unlimited && quota.v5DailyLimit > 0) {
                    return BalanceAllowance("balance.v5Remaining", remaining(quota.v5LeftToday, quota.v5DailyLimit))
                }
                if (quota.anlasEnabled && quota.anlasMonthlyLimit > 0) {
                    return BalanceAllowance("balance.quotaRemaining", remaining(quota.anlasLeft, quota.anlasMonthlyLimit))
                }
                if (quota.imageModelScope == "all" && quota.v5Unlimited) {
                    return BalanceAllowance("balance.v5Remaining", null, unlimited = true)
                }
                return BalanceAllowance("balance.quotaRemaining", null)
            }
            val usage = account.opusUsage
            if (usage != null) {
                val percent = if (usage.isNegative) 0 else usage.percent.takeIf { it.isFinite() }?.coerceIn(0.0, 100.0)?.toInt()
                return BalanceAllowance("balance.v5Remaining", percent)
            }
            return if (account.tierLevel == 3) BalanceAllowance("balance.v5Remaining", null) else null
        }

        private fun remaining(left: Int, limit: Int): Int =
            (left.toLong().coerceIn(0L, limit.toLong()) * 100L / limit).toInt()
    }
}
