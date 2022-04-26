package com.mazekine.everscale.models

data class AccountTokenBalanceOutput(
    val data: List<TokenBalanceOutput>,
    var errorMessage: String,
    val status: EVERRequestStatus
)

data class TokenBalanceOutput(
    val accountStatus: AccountStatus,
    val address: Address,
    val balance: String,
    val createAt: Long,
    val networkBalance: String,
    val rootAddress: String,
    val serviceId: String,
    val updatedAt: Long
)