package com.treasurenova.bep20manager.logic

object UsdtBep20Matcher {
    private val usdt = Regex("""\busdt\b""", RegexOption.IGNORE_CASE)
    // Boundaries keep bep20 from matching inside unrelated tokens such as xbep20 or bep200.
    private val bep20 = Regex(
        """(?<![A-Za-z0-9])(?:bep-?20|bnb smart chain)(?![A-Za-z0-9])""",
        RegexOption.IGNORE_CASE,
    )
    private val rejectedNetwork = Regex(
        """trc-?20|\btron\b|erc-?20|\bpolygon\b|\barbitrum\b|\boptimism\b|\bavalanche\b|\bsolana\b""",
        RegexOption.IGNORE_CASE,
    )
    private val trc = Regex("""trc-?20|\btron\b""", RegexOption.IGNORE_CASE)
    private val bareBnb = Regex("""\bbnb\b""", RegexOption.IGNORE_CASE)
    private val exactAddress = Regex("""(?<![A-Za-z0-9])0x[a-fA-F0-9]{40}(?![A-Za-z0-9])""")
    private val anyHexToken = Regex("""(?<![A-Za-z0-9])0x[a-fA-F0-9]+(?![A-Za-z0-9])""")
    private val wholeAddress = Regex("""^0x[a-fA-F0-9]{40}$""")

    /** USDT, BEP20, and the address must sit in one tight cluster, not an 800-char concatenation. */
    private const val TIGHT_SPAN = 200

    fun classify(block: String): AddressDecision {
        val text = block.trim()
        if (text.isEmpty()) {
            return AddressDecision.Missing("USDT BEP20 deposit address label was not visible")
        }
        if (rejectedNetwork.containsMatchIn(text)) {
            val network = if (trc.containsMatchIn(text)) "TRC20" else "other network"
            return AddressDecision.NetworkMismatch(network)
        }
        if (distinctAddresses(text).size > 1) {
            return AddressDecision.Missing("Multiple addresses were visible in the USDT BEP20 block")
        }
        val hasUsdt = usdt.containsMatchIn(text)
        val hasBep = bep20.containsMatchIn(text)
        if (!hasUsdt || !hasBep) {
            if (bareBnb.containsMatchIn(text) || hasBep) {
                return AddressDecision.NetworkMismatch(if (hasBep) "BEP20 without USDT" else "BNB")
            }
            return AddressDecision.Missing("USDT and BEP20 were not both identified in the same deposit block")
        }
        val cluster = tightCluster(text)
        if (cluster == null) {
            if (distinctAddresses(text).isEmpty() && anyHexToken.containsMatchIn(text)) {
                return AddressDecision.Missing("Visible value is not a BEP20 address")
            }
            if (distinctAddresses(text).isEmpty()) {
                return AddressDecision.Missing("USDT BEP20 deposit address was empty")
            }
            return AddressDecision.Missing("USDT, BEP20, and the address were not in the same tight deposit block")
        }
        val address = cluster
        if (!wholeAddress.matches(address)) {
            return AddressDecision.Missing("Visible value is not a BEP20 address")
        }
        return AddressDecision.Ok(address)
    }

    fun fromBlocks(blocks: List<String>): AddressDecision {
        val texts = blocks.map { it.trim() }.filter { it.isNotEmpty() }
        if (texts.any { distinctAddresses(it).size > 1 }) {
            return AddressDecision.Missing("Multiple addresses were visible in the USDT BEP20 block")
        }
        val conflict = texts.firstOrNull { conflictingNetworks(it) }
        if (conflict != null) {
            val network = if (trc.containsMatchIn(conflict)) "TRC20" else "other network"
            return AddressDecision.NetworkMismatch(network)
        }
        val tight = texts.filterNot { isUnrelatedGluedParent(it, texts) }
        val found = mutableListOf<String>()
        var mismatch: AddressDecision.NetworkMismatch? = null
        var invalid: AddressDecision.Missing? = null
        for (block in tight) {
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

    /**
     * A parent string that merely joins separate children is not a deposit block when USDT, BEP20,
     * and the address live in different children and no child pairs the address with those labels.
     */
    internal fun isUnrelatedGluedParent(block: String, blocks: List<String>): Boolean {
        val parts = blocks.filter { other ->
            other != block && other.isNotBlank() && other.length < block.length && block.contains(other)
        }
        if (parts.size < 2) return false
        if (parts.any { coherent(it) }) return false
        val labelAndValue = parts.any { usdt.containsMatchIn(it) && bep20.containsMatchIn(it) && distinctAddresses(it).isEmpty() } &&
            parts.any { distinctAddresses(it).size == 1 && !usdt.containsMatchIn(it) && !bep20.containsMatchIn(it) }
        if (labelAndValue && parts.all { distinctAddresses(it).size <= 1 } && !parts.any { conflictingNetworks(it) }) {
            return false
        }
        val hasUsdt = parts.any { usdt.containsMatchIn(it) }
        val hasBep = parts.any { bep20.containsMatchIn(it) }
        val hasAddress = parts.any { distinctAddresses(it).isNotEmpty() }
        val paired = parts.any { distinctAddresses(it).isNotEmpty() && (usdt.containsMatchIn(it) || bep20.containsMatchIn(it)) }
        return hasUsdt && hasBep && hasAddress && !paired
    }

    private fun coherent(text: String): Boolean = classify(text) is AddressDecision.Ok

    private fun conflictingNetworks(text: String): Boolean {
        if (!rejectedNetwork.containsMatchIn(text)) return false
        return usdt.containsMatchIn(text) || bep20.containsMatchIn(text) || distinctAddresses(text).isNotEmpty()
    }

    private fun distinctAddresses(text: String): List<String> =
        exactAddress.findAll(text).map { it.value }.distinct().toList()

    private fun tightCluster(text: String): String? {
        val usdtMatch = usdt.find(text) ?: return null
        val bepMatch = bep20.find(text) ?: return null
        val addresses = exactAddress.findAll(text).toList()
        if (addresses.size != 1) {
            if (addresses.isEmpty() && anyHexToken.containsMatchIn(text)) return null
            return null
        }
        val addr = addresses[0]
        val start = minOf(usdtMatch.range.first, bepMatch.range.first, addr.range.first)
        val end = maxOf(usdtMatch.range.last, bepMatch.range.last, addr.range.last)
        if (end - start > TIGHT_SPAN) return null
        if (rejectedNetwork.containsMatchIn(text.substring(start, end + 1))) return null
        return addr.value
    }
}
