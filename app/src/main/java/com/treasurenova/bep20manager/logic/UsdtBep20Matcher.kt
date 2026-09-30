package com.treasurenova.bep20manager.logic

object UsdtBep20Matcher {
    private val usdt = Regex("""\busdt\b""", RegexOption.IGNORE_CASE)
    private val bep20 = Regex("""bep-?20|bnb smart chain""", RegexOption.IGNORE_CASE)
    private val rejectedNetwork = Regex(
        """trc-?20|\btron\b|erc-?20|\bpolygon\b|\barbitrum\b|\boptimism\b|\bavalanche\b|\bsolana\b""",
        RegexOption.IGNORE_CASE,
    )
    private val trc = Regex("""trc-?20|\btron\b""", RegexOption.IGNORE_CASE)
    private val bareBnb = Regex("""\bbnb\b""", RegexOption.IGNORE_CASE)
    private val exactAddress = Regex("""(?<![A-Za-z0-9])0x[a-fA-F0-9]{40}(?![A-Za-z0-9])""")
    private val anyHexToken = Regex("""(?<![A-Za-z0-9])0x[a-fA-F0-9]+(?![A-Za-z0-9])""")
    private val wholeAddress = Regex("""^0x[a-fA-F0-9]{40}$""")

    fun classify(block: String): AddressDecision {
        val text = block.trim()
        if (text.isEmpty()) {
            return AddressDecision.Missing("USDT BEP20 deposit address label was not visible")
        }
        if (rejectedNetwork.containsMatchIn(text)) {
            val network = if (trc.containsMatchIn(text)) "TRC20" else "other network"
            return AddressDecision.NetworkMismatch(network)
        }
        val hasUsdt = usdt.containsMatchIn(text)
        val hasBep = bep20.containsMatchIn(text)
        if (!hasUsdt || !hasBep) {
            if (bareBnb.containsMatchIn(text) || hasBep) {
                return AddressDecision.NetworkMismatch(if (hasBep) "BEP20 without USDT" else "BNB")
            }
            return AddressDecision.Missing("USDT and BEP20 were not both identified in the same deposit block")
        }
        val addresses = exactAddress.findAll(text).map { it.value }.distinct().toList()
        if (addresses.size > 1) {
            return AddressDecision.Missing("Multiple addresses were visible in the USDT BEP20 block")
        }
        if (addresses.size == 1) {
            val address = addresses[0]
            if (!wholeAddress.matches(address)) {
                return AddressDecision.Missing("Visible value is not a BEP20 address")
            }
            return AddressDecision.Ok(address)
        }
        if (anyHexToken.containsMatchIn(text)) {
            return AddressDecision.Missing("Visible value is not a BEP20 address")
        }
        return AddressDecision.Missing("USDT BEP20 deposit address was empty")
    }

    fun fromBlocks(blocks: List<String>): AddressDecision {
        val found = mutableListOf<String>()
        var mismatch: AddressDecision.NetworkMismatch? = null
        var invalid: AddressDecision.Missing? = null
        for (block in blocks) {
            when (val decision = classify(block)) {
                is AddressDecision.Ok -> found += decision.address
                is AddressDecision.NetworkMismatch -> if (mismatch == null) mismatch = decision
                is AddressDecision.Missing -> {
                    if (decision.reason.contains("not a BEP20 address") && invalid == null) invalid = decision
                }
                else -> Unit
            }
        }
        val distinct = found.distinct()
        if (distinct.size == 1) return AddressDecision.Ok(distinct[0])
        if (distinct.size > 1) {
            return AddressDecision.Missing("More than one USDT BEP20 address was visible")
        }
        if (invalid != null) return invalid
        if (mismatch != null) return mismatch
        return AddressDecision.Missing("USDT and BEP20 were not both identified in the same deposit block")
    }
}
