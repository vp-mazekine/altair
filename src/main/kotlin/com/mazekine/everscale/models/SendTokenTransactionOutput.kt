package com.mazekine.everscale.models

data class SendTokenTransactionOutput(
    val data: TokenTransactionOutputData,
    val errorMessage: String? = "",
    val status: EVERRequestStatus
)
