package chain

import com.interstellargames.darkgalaxy.core.chain.Address
import kotlinx.coroutines.await
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Web mirror of the Android Rpc object: raw JSON-RPC reads with explicit confirmed commitment,
 * sends with preflight skipped. The HTTP + JSON handling lives in dg-bridge.js; this converts
 * the string results to bytes.
 */
@OptIn(ExperimentalEncodingApi::class)
object Rpc {

    private const val NULL_ACCOUNT = "!"

    private fun decode(b64: String): ByteArray? =
        if (b64 == NULL_ACCOUNT) null else Base64.decode(b64)

    /** Account data at confirmed commitment, or null if the account doesn't exist. */
    suspend fun getAccountData(rpcUrl: String, account: Address): ByteArray? =
        decode(Bridge.rpcAccountData(rpcUrl, account.base58).await<JsString>().toString())

    /** Data per account at confirmed commitment; null entries for accounts that don't exist. */
    suspend fun getMultipleAccountsData(rpcUrl: String, accounts: List<Address>): List<ByteArray?> {
        if (accounts.isEmpty()) return emptyList()
        val joined = Bridge.rpcMultiAccountData(rpcUrl, accounts.joinToString(",") { it.base58 })
            .await<JsString>().toString()
        val parts = joined.split(",")
        // Always one entry per requested account — callers pair results positionally.
        return accounts.indices.map { i -> parts.getOrNull(i)?.let { decode(it) } }
    }

    suspend fun getLatestBlockhash(rpcUrl: String): String =
        Bridge.rpcLatestBlockhash(rpcUrl).await<JsString>().toString()

    suspend fun sendRaw(rpcUrl: String, base64Tx: String): String =
        Bridge.rpcSendRaw(rpcUrl, base64Tx).await<JsString>().toString()

    /** "" = unseen · "err:<json>" = failed on chain · else the confirmation level. */
    suspend fun getSignatureStatus(rpcUrl: String, signature: String): String =
        Bridge.rpcSigStatus(rpcUrl, signature).await<JsString>().toString()
}
