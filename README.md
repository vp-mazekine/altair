# Altair

A convenient console utility for running airdrops of TIP 3.1 tokens on Everscale network.

## How to use

```shell
$> java -jar altair.jar [ARGUMENTS]
```

### Arguments

| Key | Explanation |
| --- | --- |
| `-al`, `--airdrop_list` | Source data file with addresses and amounts of tokens to distribute |
| `-cfg`, `--config` | Path to configuration file |

#### Source data file

The source file is a plain text file in the following format:
```
ADDRESS,AMOUNT
ADDRESS,AMOUNT
ADDRESS,AMOUNT
ADDRESS,AMOUNT
...
```

*Notes*
* `AMOUNT` shall be specified in natural token units, i.e. if you want to send 2 USDT, indicate `2`
* `AMOUNT` must not contain thousands separators or be specified in scientific notation
* Only dot is considered as a valid decimal separator
* Source file must not contain empty or invalid strings

*Supported delimiters*
* Comma
* Semicolon
* Tab

#### Configuration

Configuration file is in JSON format:
```json
{
  "token": {
    "root_address": "ADDRESS_OF_ROOT_TOKEN",
    "decimals": NUMBER_OF_TOKEN_DECIMALS
  },
  ["airdrop_giver_address": "PREVIOUSLY_CREATED_ADDRESS",]
  "api_config": {
    "endpoint": "https://tonapi.broxus.com",
    "prefix": "/ton/v3",
    "key": "",
    "secret": ""
  }
}
```

##### Explanation of parameters
| Name | Format | Description |
| ---  | --- | --- |
| `token`.`root_address` | `address` | Root address of the token that you want to distribute |
| `token`.`decimals` | `int` | Number of decimal places |
| `api_config`.`endpoint` | `url` | Base address of the Broxus Wallet API instance |
| `api_config`.`prefix` | `string` | Typically equals to `/ton/v3`. Change with caution |
| `api_config`.`key` | `string` | API key |
| `api_config`.`secret` | `string` | API secret for signing the calls |
| `airdrop_giver_address` | `address` | *(Optional)* Address, from which the distribution will be done.<br/>Altair will create a new address for airdrop each time, unless you specify a concrete one.<br/><br/>⚠️ The specified address must be earlier created in Wallet API under your key. |
| `debug` | `boolean` | *(Optional)* Reserved for future use, turns on debug mode |
| `retry_attempts` | `int` | *(Optional)* How many times the script will attempt to resend the failed transaction. Defaults to `5` |

### Notes

#### Balance checks

Upon running, the script will first check if there are sufficient balances of EVERs and tokens to run the distribution.

It allocates `0.5 EVER` per each transfer (plus 1 on top of total).

If there are not enough funds, it will ask you to send the missing amount to the created/specified address and will wait until you do so. Press **ENTER** once you have sent funds to refresh the balances (it's ok to do so many times).

Same applies to the token amount.

#### Simultaneous sending

The script was tested to send at least 100 token tx/sec. However, the result may vary depending on the network condition and productivity of the server where the Wallet API instance is installed.

Should you manage to achieve much higher performance, don't hesitate to tell me your results.

In case some transactions fail, **DO NOT RERUN** the same list of addresses again. Better take failed addresses from the [final report](#final-report) and create a separate data file with them. 

#### Final report

After the script has finished its job, it will save the final report in the `logs` folder in the same directory where the `jar` file is located.

It will contain the source data together with the status of the transaction, message and transaction hashes (to find transactions on [EverScan](htts://everscan.io)).

### Dependencies

* Broxus Wallet API
* Java 16 (preferably Adopt OpenJDK)