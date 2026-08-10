/**
 * The web client's platform layer, as one browser global: everything the Kotlin/Wasm app needs
 * from the browser that it cannot (or should not) do itself — fetch, localStorage, ed25519
 * signing (tweetnacl), and transaction assembly (@solana/web3.js).
 *
 * The Kotlin<->JS boundary carries ONLY strings (hex / base58 / base64 / JSON), matching the PDA
 * seam in :core. Promises reject with Error(message); Kotlin sees the message via await().
 *
 * Load order in index.html: solana-web3.min.js, tweetnacl.min.js, then this file, then the wasm
 * bundle.
 */
(function () {
  'use strict';

  const web3 = () => {
    if (!globalThis.solanaWeb3) throw new Error('solana-web3.min.js not loaded');
    return globalThis.solanaWeb3;
  };
  const naclLib = () => {
    if (!globalThis.nacl) throw new Error('tweetnacl.min.js not loaded');
    return globalThis.nacl;
  };

  const hexToBytes = (hex) => {
    const a = new Uint8Array(hex.length / 2);
    for (let i = 0; i < a.length; i++) a[i] = parseInt(hex.substr(i * 2, 2), 16);
    return a;
  };
  const bytesToHex = (bytes) =>
    Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
  const bytesToB64 = (bytes) => {
    let s = '';
    for (let i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i]);
    return btoa(s);
  };

  // Base58 (Bitcoin alphabet) — needed for the ER auth signature; web3.js does not export bs58.
  const B58_ALPHABET = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
  const b58encode = (bytes) => {
    const digits = [0];
    for (const byte of bytes) {
      let carry = byte;
      for (let i = 0; i < digits.length; i++) {
        const v = digits[i] * 256 + carry;
        digits[i] = v % 58;
        carry = Math.floor(v / 58);
      }
      while (carry) { digits.push(carry % 58); carry = Math.floor(carry / 58); }
    }
    let out = '';
    for (const byte of bytes) { if (byte !== 0) break; out += B58_ALPHABET[0]; }
    for (let i = digits.length - 1; i >= 0; i--) out += B58_ALPHABET[digits[i]];
    return out;
  };

  const keypairFromSeed = (seedHex) => naclLib().sign.keyPair.fromSeed(hexToBytes(seedHex));

  const rpcPost = async (url, method, params) => {
    const resp = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ jsonrpc: '2.0', id: 1, method, params }),
    });
    const body = await resp.text();
    if (!resp.ok) throw new Error(method + ' failed (' + resp.status + '): ' + body);
    const json = JSON.parse(body);
    if (json.error) throw new Error(method + ' error: ' + JSON.stringify(json.error));
    return json.result;
  };

  // Multi-account results ride back as one string: entries joined by ',', '!' marking a missing
  // account (base64 never contains ',' or '!', and an existing-but-empty account is '').
  const NULL_ACCOUNT = '!';

  globalThis.dgBridge = {
    // ── identity / storage ────────────────────────────────────────────────────
    randomSeedHex() {
      const seed = new Uint8Array(32);
      crypto.getRandomValues(seed);
      return bytesToHex(seed);
    },
    localGet(key) { return localStorage.getItem(key) || ''; },
    localSet(key, value) { localStorage.setItem(key, value); },
    pubkeyFromSeed(seedHex) { return b58encode(keypairFromSeed(seedHex).publicKey); },
    nowMs() { return String(Date.now()); },

    // ── ER auth: challenge → sign → login → token ────────────────────────────
    async erAuth(erRpc, seedHex) {
      const kp = keypairFromSeed(seedHex);
      const pubkey = b58encode(kp.publicKey);
      const chResp = await fetch(erRpc + '/auth/challenge?pubkey=' + pubkey);
      const chBody = await chResp.text();
      if (!chResp.ok) throw new Error('ER challenge failed (' + chResp.status + '): ' + chBody);
      const challenge = JSON.parse(chBody).challenge;
      if (!challenge) throw new Error('ER challenge missing in response');
      const sig = naclLib().sign.detached(new TextEncoder().encode(challenge), kp.secretKey);
      const loginResp = await fetch(erRpc + '/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ pubkey, challenge, signature: b58encode(sig) }),
      });
      const loginBody = await loginResp.text();
      if (!loginResp.ok) throw new Error('ER auth failed (' + loginResp.status + '): ' + loginBody);
      const token = JSON.parse(loginBody).token;
      if (!token) throw new Error('ER token missing in response');
      return token;
    },

    // ── browser wallet (Phantom-style provider on window.solana) ─────────────
    walletAvailable() {
      const p = globalThis.phantom?.solana || globalThis.solana;
      return p && p.signMessage ? '1' : '';
    },

    // Connect + sign the session message; the SHA-256 of that signature is the session seed
    // (identical derivation to the Android wallet flow, so one wallet = one session identity
    // everywhere). Returns "walletAddressBase58|seedHex".
    async walletSession(message) {
      const provider = globalThis.phantom?.solana || globalThis.solana;
      if (!provider) throw new Error('No wallet extension found');
      const resp = await provider.connect();
      const address = (resp.publicKey || provider.publicKey).toString();
      const signed = await provider.signMessage(new TextEncoder().encode(message), 'utf8');
      const sig = signed.signature || signed;
      const digest = await crypto.subtle.digest('SHA-256', sig instanceof Uint8Array ? sig : new Uint8Array(sig));
      return address + '|' + bytesToHex(new Uint8Array(digest));
    },

    // ── transaction assembly + signing ───────────────────────────────────────
    // ixsJson: [{p: programIdBase58, d: dataHex, k: [{a: base58, s: 0|1, w: 0|1}]}]
    signTx(seedHex, blockhash, ixsJson) {
      const w = web3();
      const kp = w.Keypair.fromSecretKey(keypairFromSeed(seedHex).secretKey);
      const tx = new w.Transaction({ recentBlockhash: blockhash, feePayer: kp.publicKey });
      for (const ix of JSON.parse(ixsJson)) {
        tx.add(new w.TransactionInstruction({
          programId: new w.PublicKey(ix.p),
          keys: ix.k.map((m) => ({ pubkey: new w.PublicKey(m.a), isSigner: !!m.s, isWritable: !!m.w })),
          data: hexToBytes(ix.d),
        }));
      }
      tx.sign(kp);
      return bytesToB64(tx.serialize());
    },

    // ── JSON-RPC reads/sends (always confirmed commitment; see Android Rpc.kt for why) ──
    async rpcAccountDataB64(url, pubkey) {
      const result = await rpcPost(url, 'getAccountInfo',
        [pubkey, { encoding: 'base64', commitment: 'confirmed' }]);
      const value = result && result.value;
      if (!value) return NULL_ACCOUNT;
      return (value.data && value.data[0]) || '';
    },

    async rpcMultiAccountDataB64(url, pubkeysCsv) {
      const keys = pubkeysCsv.length === 0 ? [] : pubkeysCsv.split(',');
      if (keys.length === 0) return '';
      const result = await rpcPost(url, 'getMultipleAccounts',
        [keys, { encoding: 'base64', commitment: 'confirmed' }]);
      const value = (result && result.value) || [];
      // Exactly one entry per requested key — callers pair results positionally.
      return keys.map((_, i) => {
        const v = value[i];
        if (!v) return NULL_ACCOUNT;
        return (v.data && v.data[0]) || '';
      }).join(',');
    },

    async rpcLatestBlockhash(url) {
      const result = await rpcPost(url, 'getLatestBlockhash', [{ commitment: 'confirmed' }]);
      return result.value.blockhash;
    },

    // Preflight skipped: the ER clones accounts lazily and L1 preflight simulates against
    // finalized — both reject valid transactions. Failures surface via rpcSigStatus.
    async rpcSendRaw(url, txB64) {
      const result = await rpcPost(url, 'sendTransaction',
        [txB64, { encoding: 'base64', skipPreflight: true, preflightCommitment: 'confirmed' }]);
      if (!result) throw new Error('sendTransaction returned no signature');
      return result;
    },

    // '' = node has not seen the signature; 'err:<json>' = failed on chain; else the
    // confirmation level ('processed' | 'confirmed' | 'finalized').
    async rpcSigStatus(url, signature) {
      const result = await rpcPost(url, 'getSignatureStatuses', [[signature]]);
      const st = result && result.value && result.value[0];
      if (!st) return '';
      if (st.err !== null && st.err !== undefined) return 'err:' + JSON.stringify(st.err);
      return st.confirmationStatus || 'processed';
    },
  };
})();
