package chain

import kotlin.js.Promise

/**
 * Kotlin face of `globalThis.dgBridge` (vendor/dg-bridge.js) — the web platform layer: fetch,
 * localStorage, ed25519 signing and transaction assembly. Strings only across the boundary,
 * like the PDA seam in :core.
 */

private fun jsRandomSeedHex(): String = js("dgBridge.randomSeedHex()")
private fun jsLocalGet(key: String): String = js("dgBridge.localGet(key)")
private fun jsLocalSet(key: String, value: String): Unit = js("dgBridge.localSet(key, value)")
private fun jsPubkeyFromSeed(seedHex: String): String = js("dgBridge.pubkeyFromSeed(seedHex)")
private fun jsNowMs(): String = js("dgBridge.nowMs()")
private fun jsErAuth(erRpc: String, seedHex: String): Promise<JsString> = js("dgBridge.erAuth(erRpc, seedHex)")
private fun jsSignTx(seedHex: String, blockhash: String, ixsJson: String): String =
    js("dgBridge.signTx(seedHex, blockhash, ixsJson)")
private fun jsRpcAccountData(url: String, pubkey: String): Promise<JsString> =
    js("dgBridge.rpcAccountDataB64(url, pubkey)")
private fun jsRpcMultiAccountData(url: String, pubkeysCsv: String): Promise<JsString> =
    js("dgBridge.rpcMultiAccountDataB64(url, pubkeysCsv)")
private fun jsRpcLatestBlockhash(url: String): Promise<JsString> = js("dgBridge.rpcLatestBlockhash(url)")
private fun jsRpcSendRaw(url: String, txB64: String): Promise<JsString> = js("dgBridge.rpcSendRaw(url, txB64)")
private fun jsRpcSigStatus(url: String, signature: String): Promise<JsString> =
    js("dgBridge.rpcSigStatus(url, signature)")

object Bridge {
    fun randomSeedHex(): String = jsRandomSeedHex()
    fun localGet(key: String): String = jsLocalGet(key)
    fun localSet(key: String, value: String) = jsLocalSet(key, value)
    fun pubkeyFromSeed(seedHex: String): String = jsPubkeyFromSeed(seedHex)
    fun nowSec(): Long = jsNowMs().toDouble().toLong() / 1000
    fun erAuth(erRpc: String, seedHex: String): Promise<JsString> = jsErAuth(erRpc, seedHex)
    fun signTx(seedHex: String, blockhash: String, ixsJson: String): String = jsSignTx(seedHex, blockhash, ixsJson)
    fun rpcAccountData(url: String, pubkey: String): Promise<JsString> = jsRpcAccountData(url, pubkey)
    fun rpcMultiAccountData(url: String, csv: String): Promise<JsString> = jsRpcMultiAccountData(url, csv)
    fun rpcLatestBlockhash(url: String): Promise<JsString> = jsRpcLatestBlockhash(url)
    fun rpcSendRaw(url: String, txB64: String): Promise<JsString> = jsRpcSendRaw(url, txB64)
    fun rpcSigStatus(url: String, signature: String): Promise<JsString> = jsRpcSigStatus(url, signature)
}
