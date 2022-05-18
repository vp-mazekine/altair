import com.google.gson.GsonBuilder
import com.importre.crayon.*
import com.mazekine.everscale.EVER
import com.mazekine.everscale.models.AccountType
import com.mazekine.everscale.models.TokenTransactionStatus
import com.mazekine.everscale.models.TransactionStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import models.Config
import org.slf4j.LoggerFactory
import java.io.File
import java.math.BigDecimal
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.system.exitProcess

val EVER_DECIMALS = BigDecimal(1_000_000_000)

fun main(args: Array<String>) {
    val gson = GsonBuilder().setLenient().setPrettyPrinting().create()
    val logger = LoggerFactory.getLogger("TokenAirdrop")

    println("\n${bold}${cyan}TIP 3.1 Token Airdrop v. 1.0$reset\n")

    val usage = """
        ${bold}Usage:$reset ${brightGreen}java -jar ./tair.jar ${yellow}[ARGUMENTS]$reset
        
        ${bold}Arguments:$reset
            -al, --airdrop_list   CSV file with addresses and amounts of tokens to distribute
            -cfg, --config        Configuration file
    """.trimIndent()

    if (args.isEmpty()) {
        println(usage)
        exitProcess(0x1)
    }


    var i = 0
    val airdropList: MutableMap<String, BigDecimal> = mutableMapOf()
    var config: Config? = null

    while (i <= args.lastIndex) {
        when (args[i].lowercase()) {
            "-al", "--airdrop_list" -> {
                if (i < args.lastIndex) {
                    val sourceFile = File(args[i + 1])
                    if (!sourceFile.exists()) {
                        logger.error("${brightRed}Airdrop file doesn't exist: ${sourceFile.absolutePath}$reset")
                        exitProcess(0x2)
                    }

                    val rawText = sourceFile.readText().replace("\r", "").trim()

                    airdropList.putAll(
                        rawText.split("\n").map {
                            val (address, amount) = it.split(",", "\t", ";")
                            address to amount.toBigDecimal()
                        }
                    )

                    i++
                }
            }

            "-cfg", "--config" -> {
                if (i < args.lastIndex) {
                    val configFile = File(args[i + 1])
                    if (!configFile.exists()) {
                        logger.error("${brightRed}Configuration file not found$reset")
                        exitProcess(0x3)
                    }

                    try {
                        config = gson.fromJson(configFile.bufferedReader(), Config::class.java)
                    } catch (e: Exception) {
                        logger.error("${brightRed}Configuration file is not in correct format$reset")
                        exitProcess(0x4)
                    }
                    i++
                }
            }
        }

        i++
    }

    if (airdropList.isEmpty()) {
        logger.error("${brightRed}Airdrop file is empty$reset")
        exitProcess(0x4)
    }

    if (config == null) {
        logger.error("${brightRed}Configuration is missing$reset")
        exitProcess(0x5)
    }

    //  Load configuration
    EVER.loadConfiguration(config.apiConfig)

    logger.info("Configuration loaded...")

    //  Get airdrop giver address
    val giver: String = (config.airdropGiverAddress ?: runBlocking {
        logger.info("Creating airdrop giver address...")
        i = 1
        while (true) {
            logger.info("Attempt $i")
            EVER.createAddress(
                AccountType.HighloadWallet,
                1,
                1,
                listOf()
            )?.let {
                return@runBlocking it
            }

            i++
        }
    }) as String

    logger.info("Tokens will be sent from address $brightGreen$giver$reset")

    //  Calculate required ÊVER amount
    val requiredEverBalance = (0.5 * airdropList.size + 1.0).toBigDecimal()
    var currentEverBalance = getAddressBalance(giver).divide(EVER_DECIMALS)

    while (currentEverBalance < requiredEverBalance) {
        logger.warn(
            "${brightYellow}Giver EVER balance is insufficient:\n" +
                    "\tRequired:\t$requiredEverBalance EVER\n" +
                    "\tCurrent :\t$currentEverBalance EVER\n" +
                    "\tMissing :\t${requiredEverBalance - currentEverBalance} EVER\n$reset"
        )
        print("\nPress ${brightGreen}[ENTER]$reset after you refill the balance > ")
        readln()
        currentEverBalance = getAddressBalance(giver).divide(EVER_DECIMALS)
    }

    logger.info("Gas balance is sufficient: $brightGreen$currentEverBalance EVER$reset")

    //  Calculate required token amount
    val TOKEN_DECIMALS = BigDecimal(10).pow(config.token.decimals)
    val requiredTokenBalance = airdropList.map { it.value }.sumOf { it }.multiply(TOKEN_DECIMALS)
    var currentTokenBalance =
        getAddressTokenBalance(giver, config.token.rootAddress).stripTrailingZeros()
    while (currentTokenBalance < requiredTokenBalance) {
        logger.warn(
            "${brightYellow}Giver token balance is insufficient:\n" +
                    "\tRequired:\t${requiredTokenBalance.divide(TOKEN_DECIMALS)}\n" +
                    "\tCurrent :\t${currentTokenBalance.divide(TOKEN_DECIMALS)}\n" +
                    "\tMissing :\t${(requiredTokenBalance - currentTokenBalance).divide(TOKEN_DECIMALS)}\n$reset"
        )
        print("\nPress ${brightGreen}[ENTER]$reset after you refill the token balance > ")
        readln()
        currentTokenBalance =
            getAddressTokenBalance(giver, config.token.rootAddress).stripTrailingZeros()
    }

    logger.info("Token balance is sufficient: $brightGreen${currentTokenBalance.divide(TOKEN_DECIMALS)}$reset")

    print("\nInitiate transfers? ${brightGreen}[Y/n]$reset > ")
    readlnOrNull()?.let {
        if(it.lowercase().trim() == "n") {
            println("Airdrop was cancelled by the user.")
            exitProcess(0x6)
        }
    }

    logger.info("Initiating transfers...")

    val result: MutableList<AirdropResult> = airdropList
        .map { (address, amount) ->
            AirdropResult(
                address,
                amount.multiply(TOKEN_DECIMALS).stripTrailingZeros(),
                "",
                "",
                result = TokenTransactionStatus.New
            )
        }.toMutableList()

    i = 1
    runBlocking {
        while (result.any { it.result == TokenTransactionStatus.New }) {
            val job = launch {
                result
                    .filter { it.result == TokenTransactionStatus.New }
                    .forEach { target ->
                        launch {
                            logger.info(
                                "Sending $brightGreen${
                                    target.amount.divide(TOKEN_DECIMALS).toPlainString()
                                }$reset tokens to address $brightCyan${target.address}$reset..."
                            )

                            var tokenTxId: String? = null
                            var counter: Int = config.retryAttempts
                            while (tokenTxId == null && counter > 0) {
                                tokenTxId = EVER.createTokenTransaction(
                                    giver,
                                    target.address,
                                    config.token.rootAddress,
                                    target.amount.toPlainString(),
                                    onFail = { reason: EVER.TransactionFailReason ->
                                        logger.error(
                                            "Sending of ${
                                                target.amount.divide(TOKEN_DECIMALS).toPlainString()
                                            } tokens to address ${target.address} has failed with the reason $reason"
                                        )
                                    }
                                )?.id

                                if (tokenTxId == null) logger.info(
                                    "Resending $brightGreen${
                                        target.amount.divide(
                                            TOKEN_DECIMALS
                                        ).toPlainString()
                                    }$reset tokens to address $brightCyan${target.address}$reset..."
                                )

                                counter--
                            }

                            var tokenTxResult = EVER.getTransaction(txId = tokenTxId)
                            while (tokenTxResult?.status != TransactionStatus.Done && tokenTxResult?.status != TransactionStatus.Error) {
                                delay(1_000)
                                tokenTxResult = EVER.getTransaction(txId = tokenTxId)
                            }

                            val index = result.indexOf(target)
                            when (tokenTxResult.status) {
                                TransactionStatus.Done -> {
                                    logger.info(
                                        "$brightGreen${
                                            target.amount.divide(TOKEN_DECIMALS).toPlainString()
                                        }$reset tokens were sent to address $brightCyan${target.address}$reset"
                                    )
                                    result[index].result = TokenTransactionStatus.Done
                                    result[index].messageHash = tokenTxResult.messageHash
                                    result[index].txHash = tokenTxResult.transactionHash
                                }
                                else -> {
                                    logger.error(
                                        "Sending of ${
                                            target.amount.divide(TOKEN_DECIMALS).toPlainString()
                                        } tokens to address ${target.address} has failed"
                                    )
                                    result[index].result = TokenTransactionStatus.Error
                                }
                            }
                        }
                    }
            }

            job.join()
        }
    }

    logger.info(
        "Finished with the following results:\n" +
                "\t${brightGreen}Done$reset :\t${result.filter { it.result == TokenTransactionStatus.Done }.size}\n" +
                "\t${brightYellow}New$reset  :\t${result.filter { it.result == TokenTransactionStatus.New }.size}\n" +
                "\t${brightRed}Error$reset:	${result.filter { it.result == TokenTransactionStatus.Error }.size}"
    )

    Path("logs/").createDirectories()
    val log = File("logs/" + System.currentTimeMillis() + ".csv")
    log.writeText(
        "Address,Amount,Result,Message Hash,Tx Hash\n" +
                result.joinToString("\n") {
                    "${it.address},${
                        it.amount.divide(TOKEN_DECIMALS).toPlainString()
                    },${it.result},${it.messageHash},${it.txHash}"
                }
    )
    logger.info("Log saved to: $brightGreen${log.absolutePath}$reset")
}

fun getAddressBalance(address: String): BigDecimal {
    val logger = LoggerFactory.getLogger("TokenAirdrop")

    return runBlocking {
        logger.info("Getting address balance...")
        var i = 1
        while (true) {
            EVER.getAddressInfo(address)?.let {
                it.balance.toBigDecimalOrNull()?.let { b ->
                    return@runBlocking b
                }
            }
            i++
            logger.info("Attempt $i")
        }
    } as BigDecimal
}

fun getAddressTokenBalance(
    address: String,
    tokenRootAddress: String,
    decimals: Int = 0,
    maxHops: Int = 100
): BigDecimal {
    val logger = LoggerFactory.getLogger("TokenAirdrop")

    return runBlocking {
        logger.info("Getting token $tokenRootAddress balance on address $address...")
        var i = 1
        while (true) {
            try {
                return@runBlocking (EVER
                    .getTokenInfo(address, tokenRootAddress)
                    ?.balance
                    ?.toBigDecimalOrNull()
                    ?.setScale(decimals)
                    ?: BigDecimal(0)) / BigDecimal(10).pow(decimals)
            } catch (e: Exception) {
            }

            i++
            if (i > maxHops) return@runBlocking BigDecimal(0)
            logger.info("Attempt $i")
        }
    } as BigDecimal
}

data class AirdropResult(
    val address: String,
    val amount: BigDecimal,
    val txId: String,
    var messageHash: String,
    var txHash: String? = null,
    var result: TokenTransactionStatus
)