package com.mazekine.everscale.models

data class TokenTransactionOutputData (
    val account: Address,
    val blockHash: String,
    val blockTime: String,
    val createdAt: Long,
    val direction: TransactionDirection,
    val error: String? = null,
    val id: String,
    val messageHash: String,
    val rootAddress: String,
    val status: TokenTransactionStatus,
    val transactionHash: String?,
    val updatedAt: Long,
    val value: String
)

enum class TokenTransactionStatus {
    New, Done, Error
}