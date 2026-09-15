# Private USDG transfers on Robinhood Chain

## Decision

**Do not build a new mixer, a new Zcash port, or a confidential USDG token.** For a private *wallet-to-wallet* USDG transfer on Robinhood Chain, the strongest credible near-term choice is a **RAILGUN-compatible, arbitrary-value shielded UTXO pool**, deployed only in partnership with (and reviewed by) the upstream RAILGUN maintainers. Use their audited circuit/contract release and wallet SDK unchanged wherever possible; build chain adapters, independent indexing, and a multi-relayer network around it.

This is a recommendation to use the architecture and upstream codebase, not a recommendation to copy a deployment, change a verifier, or launch a new “RAILGUN fork.” A separate Robinhood Chain pool has a separate anonymity set, so it cannot claim strong privacy at launch. If an upstream-reviewed deployment, two independent audits of the exact deployed bytecode/circuit artifacts, and a credible anonymity-set bootstrap plan cannot be obtained, the correct answer is **do not launch a production private-transfer system yet**.

This preserves sender/receiver/amount/balance privacy for transfers that remain inside the shielded set, is non-custodial at the protocol layer, works with the canonical USDG ERC-20, does not require a bridge or a separate L2, and avoids inventing the highest-risk parts of a privacy protocol. Its important costs are a 0.25% RAILGUN shield/unshield protocol fee and the operational cost of enough user activity to create meaningful anonymity—not L2 gas.

The most important caveat is chain-level censorship: Robinhood Chain documents sequencer-level screening that may exclude transactions associated with sanctioned addresses. A dapp contract cannot defeat that. Thus the design can offer strong public-ledger confidentiality, **not** censorship resistance against the chain operator or network-layer anonymity against an RPC, relayer, or sequencer that observes a user’s connection.

This report is technical research, not legal advice. Facts current as of 2026-09-14; volatile fee numbers are explicitly time-stamped.

## Robinhood Chain compatibility

| Topic | Finding | Consequence for a private pool |
|---|---|---|
| Execution | Permissionless, fully EVM-compatible Arbitrum Nitro L2; Solidity/Vyper and standard EVM tooling work. Mainnet chain ID is 4663. | Solidity shielded-pool contracts and Ethereum BN254 pairing precompiles are viable without a VM port. |
| Rollup/security | Arbitrum chain posting data to Ethereum blobs; Robinhood documents soft sequencing, posting to L1, then Ethereum finality. Its node guide says BoLD dispute resolution currently uses a **permissioned** validator set. | The pool inherits the chain’s execution/finality/security assumptions; it does not inherit Zcash’s consensus-level privacy. Do not treat a sequencer soft confirmation as final for large deposits. |
| Fees | Fee = L2 execution + L1 data availability fee; Robinhood says `eth_estimateGas` includes both. ETH is the gas token. | Groth16 proof calldata and transaction size matter, but current fees are low enough that percentage protocol/relayer fees dominate. |
| Ordering/MEV | First-come, first-served arrival at Robinhood’s sequencer; increasing gas does not buy priority. | Removes priority-gas auctions, but the sequencer still sees transaction arrival and can censor/delay. A private transfer’s calldata/proof is public once sequenced. |
| Contract constraints | 96 KB runtime code and 192 KB init-code limit, versus Ethereum’s 24 KB runtime limit; ArbOS 61. | Not a blocker for a verifier/pool. Still pin compiler/version, test pairing precompiles and the final bytecode on chain. |
| Deployment/RPC/indexing | Public RPC is rate-limited and explicitly not for production. Robinhood recommends Alchemy; also lists QuickNode, Blockdaemon, dRPC, Validation Cloud, Chainstack and GlobalStake. Archive endpoints are recommended for historical indexing. A self-run Nitro full node requires L1 execution + beacon endpoints and substantial disk/RAM. | Use at least two RPC providers plus an independently verified event/indexing pipeline. Never make one hosted indexer the source of truth for a user’s notes. |
| Existing privacy infrastructure | Robinhood’s official ecosystem list does not list RAILGUN, Privacy Pools, Tornado Cash, Aztec, Zama, or an official privacy service. | No officially supported mature privacy primitive is available. An application-layer deployment is technically possible, but is independent of Robinhood. |
| Censorship | Robinhood’s “Differences from Ethereum” documentation states sequencer-level compliance screening can exclude transactions associated with sanctioned addresses. | This is a hard limitation for every option, including stealth addresses and FHE. Do not claim “uncensorable” or “fully permissionless execution.” |

Sources: Robinhood’s [chain overview](https://docs.robinhood.com/chain/), [connection/RPC guide](https://docs.robinhood.com/chain/connecting/), [contract deployment guide](https://docs.robinhood.com/chain/deploy-smart-contracts/), [full-node guide](https://docs.robinhood.com/chain/run-a-full-node/), [gas guide](https://docs.robinhood.com/chain/gas-and-fees), and [Ethereum differences](https://docs.robinhood.com/chain/differences-from-ethereum).

### USDG

The canonical Robinhood Chain USDG proxy is `0x5fc5360D0400a0Fd4f2af552ADD042D716F1d168`, confirmed by [Robinhood’s token-contract registry](https://docs.robinhood.com/chain/contracts/). It reports 6 decimals and standard ERC-20 transfer/approval methods. A live `eth_estimateGas` against the public RPC on 2026-09-14 estimated a simple one-unit `transfer` from a funded address at **48,400 gas**.

However, USDG is not credibly “immutable vanilla ERC-20.” Paxos’s published USDG interface includes `pause`, `freeze`, `wipeFrozenAddress`, supply controls and `upgradeTo`/`upgradeToAndCall`; its repository describes USDG as centrally minted/burned and upgradeable, and provides audit links. The Robinhood deployment is an EIP-1967 proxy (implementation observed on 2026-09-14: `0x68184c449e1A8F34fa18d289737129Fd27b66F8f`). Therefore:

- A pool can custody USDG without holding a user’s key, but Paxos can pause/freeze the pool or an address and upgrade token logic.
- Treat token compatibility as **conditional**: test `transferFrom`, direct `transfer`, permit/authorization paths, return values, fee-on-transfer behavior, pausing, and frozen-pool recovery behavior against the canonical address before any launch.
- Do not support arbitrary ERC-20s merely because they compile. Maintain an explicit allowlist and test each token for reentrancy, callbacks, transfer fees/rebases, blacklist semantics, decimals and broken return values.

Sources: [Paxos USDG source and specification](https://github.com/paxosglobal/usdg-contract), [Paxos USDG guide](https://docs.paxos.com/guides/stablecoin/usdg), and the canonical contract registry above.

## What “private transfer” can and cannot mean here

A robust application-layer shielded pool can hide the following from a public chain observer:

- Which deposited note funded a private spend (sender relationship).
- The recipient’s shielded address/note and the amount of an **internal** private transfer.
- Private balance composition and history inside the pool.
- The link between a deposit and a later withdrawal, subject to the effective anonymity set.

It cannot make these facts disappear:

- The public wallet that deposits into a pool, token type and deposited amount.
- A public withdrawal recipient, amount and time. A relayer hides the gas-paying EOA, not the withdrawal itself.
- RPC/IP/device/browser fingerprint, relayer request metadata, timing, note-selection patterns, a unique amount, and small-pool correlations.
- The fact that an address transacted with the privacy contract, the number/timing of public interactions, and chain sequencer observations.
- USDG issuer controls or Robinhood sequencer policy.

“Sender privacy” below means unlinkability of an **in-pool** spend from a depositor; it does not mean the original wallet is invisible when it shields its assets.

## Technology comparison

Ratings are relative for this exact use case: private arbitrary-value USDG transfer on Robinhood Chain. `Strong` means the primitive can provide the property if used correctly and the anonymity set is adequate; it is not a guarantee against metadata analysis.

| Approach | Privacy properties | Cost / UX / infrastructure | Maturity and Robinhood Chain fit | Verdict |
|---|---|---|---|---|
| **RAILGUN shielded UTXO pool** | Strong internal sender, receiver, amount, balance and graph privacy. Arbitrary values, change notes and private addresses. Deposit/withdraw endpoints stay public. | Groth16 client proving; relay optional for pure internal sends but strongly recommended to avoid gas-payer linkage. No liquidity needed for a transfer. 0.25% on shield and unshield, no protocol fee on private transfer; relayer adds gas + market premium. | Live on Ethereum, Arbitrum, Polygon and BNB Chain; open source, SDK MIT-licensed, documented audits and bug bounty. Not currently an official Robinhood deployment, but EVM/Nitro compatibility makes deployment realistic. New chain = new anonymity set. | **Best available architecture**, contingent on upstream-reviewed deployment, independent audits and real adoption. |
| **Purpose-built arbitrary-value JoinSplit (Groth16) pool** | Same theoretical properties as RAILGUN if the circuit correctly binds asset, values, ownership, roots, nullifiers, outputs and external data. | Lowest recurring protocol fee; roughly 0.02–0.15 USD of current L2 gas per action before a relayer. Proving is tens of seconds in browser for typical circuits; needs note sync and relayers. | Directly deployable, including a USDG-only pool. But it creates an unaudited fork/new circuit/new trusted setup/new anonymity set and is the highest engineering and audit risk. | **Do not build for production.** It is an economic alternative only after years of code/audit maturity, not an initial system. |
| **Tornado Cash Classic / Nova-style mixer** | Classic fixed-denomination pool hides deposit-withdraw link but not arbitrary amounts; each denomination is its own set. Nova improves amount flexibility but is still mixer-oriented. No durable private account/balance UX comparable to RAILGUN. | Deposit then wait, note custody/recovery, and a relayer for an unlinkable withdrawal. Lower contract complexity than a full private wallet, but poor payment UX and denomination/amount correlation. | Source is deployable on EVM, but it is not deployed on Robinhood Chain. Mature historical code/audits, Groth16 setup, but governance compromise and unusually high policy/operational risk. | **Reject** for a USDG payment rail. |
| **Privacy Pools (0xbow)** | Hides the deposit-withdraw link and supports arbitrary/partial withdrawals. Its Association Set Provider (ASP) allows a user to prove association with an approved set. V1 does **not** provide the required private in-pool recipient transfer. | Needs an ASP list/root; direct or relayed withdrawal. No liquidity for a pool, but admission screening fragments the set and adds censorship/DoS dependency. | Portable Solidity/circuits, but no Robinhood deployment. V1 has mainnet deployments on other chains; V2 was public testnet at research time. Auditware found medium issues including root-history DoS and unaudited Poseidon dependency. | **Second-best only if compliant private exit matters more than private peer-to-peer sending.** Not the requested primitive. |
| **RAIL20 (existing Robinhood USDG pool)** | Claims 2-in/2-out Groth16, private amount/recipient/internal transfers. Its docs state the hosted relayer derives account keys from a wallet signature and sees notes, amounts and recipients. | Its stated Robinhood USDG relayer price is 0.5 USDG + 0.35%. Proofs are server-generated; that is an avoidable confidentiality and availability dependency. | It publishes a Robinhood USDG pool (`0x04F8…53F0`) but its GitHub project is new/small and documentation—not a third-party audit—is the evidence located. On-chain events observed on 2026-09-14 indicate only hundreds of pool events, not a mature set. | **Do not use or integrate as production infrastructure.** Existing deployment is not a substitute for assurance. |
| **Zcash Sapling/Orchard-style shielded pool** | Very strong and battle-tested shielded-note model. Orchard uses Halo 2 and avoids a per-circuit trusted setup. | Native Zcash is optimized as a protocol, not an EVM contract. Porting Orchard proof verification, note encryption, wallet synchronization and consensus rules to Solidity is costly; transparent verification is materially more expensive than Groth16 pairing verification. | No direct USDG/Robinhood support. Moving USDG into Zcash requires a bridge/wrapper and imports bridge custody/liquidity/correlation risk. | **Use as a design reference, not a codebase to port.** |
| **Aztec** | Strong private state/execution, encrypted UTXOs, client-side private execution and proof generation. Can hide more than a transfer. | Separate private rollup, private VM and ecosystem/bridge. Proving and developer stack are much heavier than a single pool. | Aztec is explicitly not EVM-compatible and settles to Ethereum, not Robinhood Chain. USDG would need bridging/wrapping; no direct deployment. | **Reject for this chain-local use case.** Revisit only for a future dedicated privacy rollup. |
| **Stealth addresses / ERC-5564** | Receiver identity linkage improves: each payment uses a one-time address. Sender, token, amount, balance and graph are public; sweeping a stealth address can re-link the receiver. | Normal ERC-20 transfer cost plus receiver funding/sweep. No ZK proving, relayer or liquidity required; broadly EVM-compatible but wallet discovery/scanning is not universal. | ERC-5564 is finalized; it can be deployed/used on Robinhood Chain. | **Useful complement**, not private payments. Use only as a low-cost receiver-address privacy mode. |
| **Confidential tokens / FHE / ERC-7984** | Can hide amounts and balances under encrypted accounting. Account identities and transaction cadence remain visible unless combined with another privacy layer. | FHE computation/ciphertext lifecycle is much heavier; Zama’s architecture needs host contracts, coprocessors, gateway, KMS/threshold MPC, relayer/oracle and key lifecycle. | ERC-7984 is still draft. Existing USDG cannot be made confidential in place; wrapping/reissuing creates a new token/pool and issuer/infrastructure dependencies. No known Robinhood deployment. | **Reject for initial USDG transfer.** Promising for issuer-designed confidential assets, not retrofitting an existing stablecoin. |
| **MPC/private execution or TEE** | Can conceal data from public chain, but privacy relies on a federation/TEE threshold rather than only a ZK proof and the user’s secret. | Operators, key shares, availability, recovery and threshold governance become core infrastructure. | No native USDG/MPC confidential transfer facility on Robinhood. A federated ledger/bridge is not the requested non-custodial EVM transfer. | **Reject.** Use MPC only for narrowly scoped relayer key management, not custody or transaction correctness. |
| **ZK coprocessor / zkVM / app-specific private rollup** | Flexible, potentially transparent-proof architecture; can eventually combine payments and private execution. | Complex prover/operator/bridge design; high latency and proof/verification cost. | Technically possible over an EVM L2 but no direct mature Robinhood product. Adds a new security domain and liquidity fragmentation. | **Future research, not an initial rail.** |

### Security/maturity notes

- **RAILGUN.** The public docs describe a Groth16 ceremony, independently integrated wallets, audited contracts/circuits and a bug bounty. It is still complex software; the 2022 Hashcloak review found three high findings in scope, reported as resolved. New deployment bytecode, verifier keys, prover artifacts, indexing and relayer implementations are all out of scope of an old audit unless explicitly reviewed.
- **Privacy Pools.** The original paper adds association sets, rather than “solving compliance.” The ASP becomes a policy/availability influence. Auditware’s 2025 core review reported no critical findings but identified an ERC-20 approval issue, fixed-root-history withdrawal DoS risk, ASP root update DoS, and an unaudited Poseidon library dependency.
- **Tornado Cash.** Classic contracts/circuits had ABDK and other audits. Its May 2023 incident was a governance hijack through a malicious proposal/deployment mechanism, rather than a proof forgery of immutable pool notes; that is nevertheless a decisive warning against upgrade/governance complexity. OFAC removed Tornado Cash addresses from the SDN list in March 2025, but this does not erase operational, counterparty or policy risk.
- **RAIL20.** Its own architecture says a central relayer derives keys from a signed message and builds proofs; that is not an acceptable trust model for a system whose goal is strong privacy. Treat “live” and contract verification as evidence of deployment, not evidence of security.

Sources: [RAILGUN overview](https://docs.railgun.org/wiki), [trusted setup](https://docs.railgun.org/wiki/learn/privacy-system/trusted-setup-ceremony), [fees](https://docs.railgun.org/community-faqs/readme/costs-and-fees), [audits/bug bounty](https://www.railgun.ch/public/en), [Privacy Pools paper](https://privacypools.com/whitepaper.pdf), [Privacy Pools core audit](https://github.com/Auditware/audits/blob/main/0xbow/Privacy%20Pools%20Core/Privacy%20Pools%20Core%20Audit%20Report.md), [Tornado Core](https://github.com/tornadocash/tornado-core), [Tornado governance incident analysis](https://blog.openzeppelin.com/openzeppelin-security-report-top-security-incidents-and-insights-from-april-june-2023), [OFAC delisting](https://home.treasury.gov/news/press-releases/sb0057), [Zcash Orchard specification](https://zips.z.cash/zip-0224), [Aztec docs](https://docs.aztec.network/), [ERC-5564](https://eips.ethereum.org/EIPS/eip-5564), [ERC-7984](https://eips.ethereum.org/EIPS/eip-7984), [Zama architecture](https://docs.zama.org/protocol/protocol), and [RAIL20 architecture](https://docs.rail20.org/architecture).

## Cost model

### Measurement basis

On 2026-09-14, the Robinhood public RPC returned `eth_gasPrice = 71,838,000 wei` (0.071838 gwei). ETH was approximately 2,529.94 USD. A direct canonical-USDG transfer estimate was 48,400 gas, or **0.000003477 ETH / about $0.0088** at that instant. These are not promises: Arbitrum’s L1 data component, ETH price, calldata, congestion, proof size and relayer market are variable.

| Action | Typical gas / direct-chain cost at the observed fee | Other fee | Practical expected cost | Comments |
|---|---:|---:|---:|---|
| Normal USDG `transfer` | 48,400 gas; about $0.009 | None | **~$0.01** | Reveals sender, receiver, amount and balances. |
| Approve a pool (one-time) | roughly a normal ERC-20 state-changing call | None | **~$0.01–0.03** | Prefer permit/authorization only after USDG’s exact support and signature domain are verified. |
| Shield/deposit (RAILGUN-like) | ~100k–220k gas, **~$0.02–0.05** | RAILGUN: 0.25% of deposited amount | **$0.02–0.05 + 0.25%** | The deposit wallet, token and amount are public. A custom lean pool can remove the percentage, but not the security cost of becoming a new protocol. |
| Private in-pool transfer | ~300k–600k gas including Groth16 verification/call data, **~$0.05–0.12** | No RAILGUN protocol fee | **~$0.05–0.15 direct; relayer quote if used** | 2-in/2-out proof, nullifiers and encrypted output notes. Gas payer should be unrelated; a relayer is normally needed for strongest sender privacy. |
| Private withdrawal/unshield | ~350k–650k gas, **~$0.06–0.13** | RAILGUN: 0.25% + relayer market fee | **$0.06–0.15 + 0.25% + relayer** | Recipient and public amount become visible; withdrawal timing/amount can collapse anonymity. |
| Relayer-assisted private send | Same proof execution, paid by relayer | competitive gas premium; RAILGUN docs cite generally ~10% over gas, while RAIL20 publishes 0.5 USDG + 0.35% for USDG | **gas-equivalent + 10–50% gas premium for a competitive network; avoid percentage-of-value fees** | A fee denominated in USDG allows a recipient/sender without ETH to transact. A $0.50 floor is expensive for small payments on this L2. |

The order-of-magnitude conclusion is robust: **gas is cheap; percentage fees and privacy economics dominate.** At $1,000, a 0.25% shield plus 0.25% unshield is $5, vastly more than the L2 gas. At $10, recurring proof gas/relayer minimums dominate. A production service should not promise inexpensive micro-payments until the relayer fee policy is clear.

The current RAIL20 deployment illustrates why “existing” is not automatically cost-efficient: its own published USDG relay fee is 0.5 USDG plus 0.35%, which is $4 on a $1,000 transfer before considering deposit/withdraw. It also centralizes proof generation and observability in the relayer.

## Top-three security analysis

### 1. RAILGUN-compatible arbitrary-value shielded pool — recommended

**Core assumptions.** Groth16 proof soundness over BN254; secrecy of the circuit setup toxic waste (the existing multi-party ceremony must have had at least one honest participant); Poseidon collision resistance; secure note encryption/key derivation; correct Solidity verifier/pool; and the underlying Robinhood Chain. The cryptography prevents a public observer from learning note ownership/amounts, but it does not generate anonymity—other users and behavior do.

**Double spend/nullifier/commitment risks.** The circuit must bind the spent note to a current or retained Merkle root, derive nullifiers from a secret only the note owner knows, make nullifiers globally unique, bind every output commitment to value/asset/owner/randomness, and enforce exact value conservation across inputs, outputs, public value and fees. One missing range check, asset binding, root-history edge case, signature binding, or duplicate-nullifier check can mint value or steal notes. Test malformed proof/public-signal combinations and all old-root/nullifier races.

**Proof and setup risks.** A malicious proving key/setup participant can potentially forge proofs and inflate the pool. Reusing an audited circuit/zkey avoids creating a new ceremony but does not authenticate a new deployment: pin artifacts by content hash, rederive the verifier key, and prove deployed bytecode matches a reviewed build. Do not swap a verifier key, alter public inputs or regenerate a circuit without a new ceremony, formal review and audit.

**Contract and upgrade risks.** No owner/admin/proxy should be able to drain notes, alter verifier keys, write nullifiers, modify trees, set arbitrary fees, or upgrade custody logic. If emergency controls are legally required, they must be narrowly specified, time-locked, independently controlled, and explicitly acknowledged as a privacy/censorship compromise. A “pause” still traps value and can be an attack surface. Keep transfer-only scope; do not add generic private DeFi adapters, bridges or swaps initially.

**Relayer and metadata risks.** Relayers must receive only a signed, finalized, already-proved transaction payload plus fee/reimbursement instructions—not seed phrases, spending keys, viewing keys, a reusable wallet signature, plaintext notes or recipient mapping. Run at least three independent relayers; permit direct self-broadcast; rotate relay endpoints; use privacy-preserving network transport. A relayer can censor, delay, price-gouge, log IP/metadata or front-run only what it sees; correct binding of `relayer`, `fee`, `recipient`, `chainId`, pool and expiry into the proof prevents it from redirecting funds.

**Correlation risks.** Unique deposits/withdrawals, immediate exit, one-deposit/one-withdraw, self-funded gas, rare asset pools, regular timing, and a small set collapse anonymity despite sound ZK. Require enough time and activity; default to in-pool receipt rather than public withdrawal; use fresh destination addresses and relayers. Never report raw deposit count as effective anonymity.

### 2. Privacy Pools — technically credible but wrong payment primitive

**Core assumptions.** Same Groth16/Poseidon/Merkle/nullifier assumptions, plus correctness/availability of the ASP association set. A user proves membership in an approved subset, which can reduce policy exposure but also reduces anonymity compared with the full pool and creates a list/root timing dependency.

**Attack surface.** A compromised/malicious ASP can deny admission, update roots so pending proofs fail, partition users into small sets, or make surveillance/policy decisions. The cited audit identifies root-history overwrite/DoS and root update considerations. Fee/withdraw recipient must be bound in public inputs; ERC-20 transfers need SafeERC20-style handling. Its primary private output is a withdrawal, so a public withdrawal recipient/amount necessarily leaks more than a private recipient note.

**Why not first.** For this task it either forces a public withdrawal to the recipient or needs a nonstandard extension that becomes a new private-wallet protocol. It is useful only if selective association-set proof is a hard design requirement, not as a shortcut to private peer-to-peer transfers.

### 3. Tornado/Nova-style mixer — mature primitive, unsuitable architecture

**Core assumptions.** Groth16 soundness/trusted setup, commitment/tree/nullifier correctness, immutable verifier/pool, note secrecy and a sufficient per-denomination/pool anonymity set. A relayer must honestly deliver a correctly bound withdrawal; it cannot steal if recipient/fee are proof-bound.

**Attack surface.** Fixed denominations make amount classes public and fragment sets. Deposit/withdraw timing and exact value make matching practical. Note loss is loss of funds. A fresh withdrawal address needs a relayer or it must be funded in a way that links it. Classic ERC-20 token edge cases, root history, front-end/RPC metadata, and relayer monopolies remain. Governance/upgrades caused a well-documented 2023 takeover risk even though the core mixing concept is simple.

**Why not first.** It has no good arbitrary USDG payment/account model and produces an awkward deposit–wait–withdraw workflow. It is cheaper to reason about, not cheaper or stronger for daily private transfers.

## Exact recommended architecture

```text
public USDG wallet
  └─ shield USDG (public depositor + amount) ──> immutable RAILGUN-compatible vault
                                                   ├─ append-only commitment tree
                                                   ├─ nullifier set
                                                   └─ fixed reviewed Groth16 verifier

client-side wallet / local prover
  └─ encrypted notes + viewing/spending keys + locally verified event sync
       └─ JoinSplit proof: inputs, values, owners, asset, root, nullifiers,
                          recipient/change commitments and relay fee are bound
            └─ independent relayer(s) or self-broadcast
                 └─ private recipient note (no public USDG transfer)
```

1. **Vault and circuit.** Start USDG-only, arbitrary-value notes, private 2-in/2-out JoinSplit transfers, encrypted recipient/change notes, append-only commitment tree, retained roots, and a global nullifier set. Reuse a release-pinned upstream RAILGUN circuit, verifier, contracts and SDK; preserve the verified circuit/artifact relationship. Do not introduce a custom “simpler” circuit.
2. **Keys and proving.** Generate/hold the private spending/viewing material client-side. Generate proofs client-side or in a user-controlled local service. A remote proving service is permitted only if it sees an encrypted witness and cannot derive/reconstruct spending or viewing keys; it is not the default.
3. **Broadcast.** Accept direct EOA submission for availability, but supply multiple independent relayers so a sender does not pay on-chain gas from a linked EOA. Relayers accept only serialized, proof-bound transactions. Relayer fees must be capped and bound in proof; use USDG fee payment if desired. Never make a single hosted relayer necessary to spend.
4. **Indexing.** Wallets reconstruct state from chain events and verify roots locally. Operate redundant archive-RPC/event sources and publish reproducible snapshots/content hashes. An indexer can accelerate sync but never decides balances or proof validity.
5. **Governance.** Prefer a fixed, non-upgradeable custody/verifier path. If an upstream deployment needs governance for a separate treasury/rewards module, keep it logically incapable of changing pool custody/verifier state. Publish source, compiler, bytecode, verifier key, ceremony/provenance and reproducible deployment manifest.
6. **Boundaries.** No private bridging, swaps, generic cross-contract calls, token registration, arbitrary relayer logic, fee conversion, admin withdrawals, or compliance oracle in the first production pool. Each is a separate security system. Add only after the transfer core has an independent audit, long-running monitoring and a meaningful set.
7. **USDG policy.** Explicitly document that Paxos can pause/freeze/upgrade USDG and that Robinhood can sequence-screen. There is no honest architecture that makes these powers disappear.

## Reuse versus build

### Reuse

- The upstream [RAILGUN wallet SDK](https://github.com/Railgun-Community/wallet), circuit artifacts, contracts, note model, Merkle/nullifier implementation, test vectors and deployment discipline—after a release-specific legal/licensing and security review.
- RAILGUN’s established Groth16 ceremony provenance and audited verifier/circuit relationship; independently reproduce it before deployment.
- Standard, audited libraries for ERC-20 interaction and access control only where absolutely needed. Use the canonical USDG proxy address and its published ABI, not a copied interface that silently assumes 18 decimals or immutability.
- Arbitrum/Nitro gas estimation and Robinhood’s supported RPC/archive-node options; an independent event indexer built from canonical logs.
- Existing peer-reviewed concepts as references: Zcash Orchard for privacy design, Privacy Pools for association-set research, ERC-5564 as an optional receiver-address layer.

### Do not build yourself

- A new ZK circuit, trusted-setup ceremony, verifier key, Poseidon implementation, Merkle/nullifier logic, note-encryption format or wallet key derivation scheme.
- A “lightweight Tornado clone,” a custom EVM Orchard/Sapling port, a cross-chain privacy bridge, a private DEX, a fee swapper, or an FHE/MPC/KMS network.
- A custodial server that derives wallet keys from a signature, scans a user’s notes, produces proofs with plaintext witness data, or is the only relayer.
- An upgradeable vault, arbitrary admin recovery, global pause that strands funds, or a sanctions/association list in the proof path without openly accepting the availability/privacy trade-off.
- Claims that a fresh pool has “strong anonymity” before effective-set measurement supports it.

## Launch gates, not implementation steps

No production release should proceed until all are true:

- The exact deployed contract bytecode, circuit source, verification key, proving artifacts and SDK version are reproducibly matched and independently audited.
- A specialist ZK audit covers constraints/public-input binding; a separate Solidity audit covers all vault, token and relay paths; fuzz/property tests cover conservation, duplicate nullifiers, root rotation, fee/recipient binding and hostile ERC-20s.
- USDG integration has been tested against pause/freeze/upgrade and 6-decimal behavior, and disclosure/recovery policy is decided for a frozen vault.
- Relayers cannot obtain keys/witnesses, are multi-operator, fee-capped, independently monitored and optional for liveness.
- The pool has an independently measured effective anonymity set and behavior policy (not simply total commitments), plus user-visible warnings for fast/unique deposits and public exits.
- The system’s threat model explicitly excludes Robinhood sequencer censorship and network-layer metadata unless independent transport/RPC measures are deployed.

## Sources

1. Robinhood Chain. [About Robinhood Chain](https://docs.robinhood.com/chain/); [Connecting](https://docs.robinhood.com/chain/connecting/); [Deploy a contract](https://docs.robinhood.com/chain/deploy-smart-contracts/); [Run a full node](https://docs.robinhood.com/chain/run-a-full-node/); [Differences from Ethereum](https://docs.robinhood.com/chain/differences-from-ethereum); [Gas & Fees](https://docs.robinhood.com/chain/gas-and-fees); [Token contracts](https://docs.robinhood.com/chain/contracts/). Accessed 2026-09-14.
2. Paxos. [USDG contract repository](https://github.com/paxosglobal/usdg-contract) and [USDG documentation](https://docs.paxos.com/guides/stablecoin/usdg). Accessed 2026-09-14.
3. RAILGUN. [Protocol overview](https://docs.railgun.org/wiki), [trusted setup](https://docs.railgun.org/wiki/learn/privacy-system/trusted-setup-ceremony), [fees](https://docs.railgun.org/community-faqs/readme/costs-and-fees), [unshielding](https://docs.railgun.org/developer-guide/wallet/transactions/unshielding), [wallet SDK](https://github.com/Railgun-Community/wallet), and [security/audit material](https://www.railgun.ch/public/en). Accessed 2026-09-14.
4. Buterin, Illum, Nadler, Schär, Soleimani. [Blockchain Privacy and Regulatory Compliance: Privacy Pools](https://privacypools.com/whitepaper.pdf), 2023; [0xbow Privacy Pools documentation](https://docs.privacypools.com/); [Auditware core audit](https://github.com/Auditware/audits/blob/main/0xbow/Privacy%20Pools%20Core/Privacy%20Pools%20Core%20Audit%20Report.md), 2025.
5. Tornado Cash. [Core contracts](https://github.com/tornadocash/tornado-core); [circuit documentation](https://docs.tornadoeth.cash/tornado-cash-classic/circuits); [ABDK audit](https://tornado.cash/audits/TornadoCash_contract_audit_ABDK.pdf); OpenZeppelin, [2023 governance-hijack analysis](https://blog.openzeppelin.com/openzeppelin-security-report-top-security-incidents-and-insights-from-april-june-2023); U.S. Treasury, [2025 delisting](https://home.treasury.gov/news/press-releases/sb0057).
6. Zcash. [ZIP 224: Orchard Shielded Protocol](https://zips.z.cash/zip-0224) and [protocol specification](https://zips.z.cash/protocol/protocol.pdf). Accessed 2026-09-14.
7. Aztec. [Documentation](https://docs.aztec.network/) and [architecture overview](https://docs.aztec.network/developers/docs/foundational-topics). Accessed 2026-09-14.
8. Ethereum. [ERC-5564: Stealth Addresses](https://eips.ethereum.org/EIPS/eip-5564); [ERC-7984: Confidential Fungible Token](https://eips.ethereum.org/EIPS/eip-7984). Accessed 2026-09-14.
9. Zama. [Protocol architecture](https://docs.zama.org/protocol/protocol), [FHE overview](https://docs.zama.org/fhevm/fundamentals/architecture_overview/fhe-on-blockchain), and [threshold KMS architecture](https://github.com/zama-ai/kms/blob/main/ai-docs/ARCHITECTURE.md). Accessed 2026-09-14.
10. RAIL20. [Architecture](https://docs.rail20.org/architecture) and [contract/fee documentation](https://docs.rail20.org/contracts). These are project claims, not an endorsement or audit. Accessed 2026-09-14.
