package com.kallan.naistudio.services

import org.junit.Assert.*
import org.junit.Test

class BalanceAllowanceTest {
    private fun quota(left: Int = 15, limit: Int = 50) = GatewayQuota(left, limit, false, true, 40, 200, "all")
    @Test fun thirdPartyUsesRemainingRatherThanUsedPercentage() {
        val result = BalanceAllowance.resolve(AccountSummary(), true, quota())!!
        assertEquals("balance.v5Remaining", result.labelKey)
        assertEquals(30, result.percent)
    }
    @Test fun monthlyQuotaIsUsedWhenV5HasNoFiniteLimit() {
        assertEquals(20, BalanceAllowance.resolve(AccountSummary(), true, quota().copy(v5Unlimited = true))!!.percent)
    }
    @Test fun unknownAndUnlimitedAreNotInventedPercentages() {
        assertNull(BalanceAllowance.resolve(AccountSummary(), true, null)!!.percent)
        val result = BalanceAllowance.resolve(AccountSummary(), true, quota().copy(v5Unlimited = true, anlasEnabled = false))!!
        assertNull(result.percent)
        assertTrue(result.unlimited)
    }
    @Test fun largeQuotasDoNotOverflowAndInvalidBalancesClamp() {
        assertEquals(100, BalanceAllowance.resolve(AccountSummary(), true, quota(Int.MAX_VALUE, Int.MAX_VALUE))!!.percent)
        assertEquals(0, BalanceAllowance.resolve(AccountSummary(), true, quota(-1))!!.percent)
    }
    @Test fun officialQuotaDoesNotDependOnSelectedGenerationModel() {
        val account = AccountSummary(opusUsage = OpusGenerationUsage(62.0, false, 0.0))
        assertEquals(62, BalanceAllowance.resolve(account, false, null)!!.percent)
        assertEquals(0, BalanceAllowance.resolve(account.copy(opusUsage = OpusGenerationUsage(62.0, true, 0.0)), false, null)!!.percent)
        assertNull(BalanceAllowance.resolve(AccountSummary(anlasBalance = 200), false, null))
    }
}
