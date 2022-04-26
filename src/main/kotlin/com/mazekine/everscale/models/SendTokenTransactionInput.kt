package com.mazekine.everscale.models

import java.util.*

data class SendTokenTransactionInput (
    val fromAddress: String,
    val recipientAddress: String,
    val rootAddress: String,
    val value: String,
    val id: UUID,
    val notifyReceiver: Boolean = true,
    val sendGasTo: String? = null,
    val fee: String? = null
) : JsonCompatibleInput()
