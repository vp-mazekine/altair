package models

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import com.mazekine.everscale.models.APIConfig

data class Config(
    @Expose
    val token: TokenConfig,

    @Expose @SerializedName("airdrop_giver_address")
    val airdropGiverAddress: String? = null,

    @Expose @SerializedName("api_config")
    val apiConfig: APIConfig,

    val debug: Boolean = false
)

data class TokenConfig(
    @Expose @SerializedName("root_address")
    val rootAddress: String,
    val decimals: Int = 0
)