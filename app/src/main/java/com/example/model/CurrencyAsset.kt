package com.example.model

enum class AssetCategory {
  CRYPTO,
  FIAT,
}

data class CurrencyAsset(
  val code: String,
  val name: String,
  val symbol: String,
  val category: AssetCategory,
  val defaultUsdPrice: Double, // Value of 1 unit of this asset in USD
  val decimals: Int,
  val networkName: String,
  val badgeHex: Long,
  val searchAliases: List<String> = emptyList(),
)

data class CalcHistoryEntry(
  val id: String,
  val expression: String,
  val result: Double,
  val baseCode: String,
  val targetCode: String,
  val convertedResult: Double,
  val timestampMs: Long,
)

data class CurrencyPairItem(
  val baseCode: String,
  val quoteCode: String,
  val rate: Double,
  val change24hPct: Double,
  val sparkline: List<Double>,
  val isPinned: Boolean = false,
)

object AssetCatalog {
  val assets: List<CurrencyAsset> = listOf(
    // Fiat Currencies
    CurrencyAsset("USD", "US Dollar", "$", AssetCategory.FIAT, 1.0, 2, "Fiat USD", 0xFF22C55E, listOf("dollar", "usd", "america")),
    CurrencyAsset("NGN", "Nigerian Naira", "₦", AssetCategory.FIAT, 1.0 / 1645.0, 2, "Fiat NGN", 0xFF10B981, listOf("naira", "nigeria", "ngn")),
    CurrencyAsset("GBP", "British Pound", "£", AssetCategory.FIAT, 1.27, 2, "Fiat GBP", 0xFF6366F1, listOf("pound", "sterling", "gdp", "gbp", "uk")),
    CurrencyAsset("EUR", "Euro", "€", AssetCategory.FIAT, 1.06, 2, "Fiat EUR", 0xFF3B82F6, listOf("euro", "europe", "eur")),
    CurrencyAsset("CAD", "Canadian Dollar", "C$", AssetCategory.FIAT, 0.71, 2, "Fiat CAD", 0xFFEF4444, listOf("canadian", "canada", "cad")),
    CurrencyAsset("GHS", "Ghanaian Cedi", "₵", AssetCategory.FIAT, 1.0 / 15.8, 2, "Fiat GHS", 0xFFF59E0B, listOf("cedi", "ghana", "ghs")),
    CurrencyAsset("KES", "Kenyan Shilling", "KSh", AssetCategory.FIAT, 1.0 / 129.0, 2, "Fiat KES", 0xFF14B8A6, listOf("shilling", "kenya", "kes")),
    CurrencyAsset("ZAR", "South African Rand", "R", AssetCategory.FIAT, 1.0 / 18.1, 2, "Fiat ZAR", 0xFF8B5CF6, listOf("rand", "south africa", "zar")),
    CurrencyAsset("JPY", "Japanese Yen", "¥", AssetCategory.FIAT, 1.0 / 154.0, 2, "Fiat JPY", 0xFFF43F5E, listOf("yen", "japan", "jpy")),
    CurrencyAsset("AED", "UAE Dirham", "د.إ", AssetCategory.FIAT, 1.0 / 3.6725, 2, "Fiat AED", 0xFF06B6D4, listOf("dirham", "uae", "dubai", "aed")),
    CurrencyAsset("AUD", "Australian Dollar", "A$", AssetCategory.FIAT, 0.65, 2, "Fiat AUD", 0xFF84CC16, listOf("australia", "aud")),
    CurrencyAsset("CHF", "Swiss Franc", "CHF", AssetCategory.FIAT, 1.13, 2, "Fiat CHF", 0xFFEC4899, listOf("franc", "swiss", "chf")),
    CurrencyAsset("INR", "Indian Rupee", "₹", AssetCategory.FIAT, 1.0 / 84.4, 2, "Fiat INR", 0xFFF97316, listOf("rupee", "india", "inr")),
    CurrencyAsset("CNY", "Chinese Yuan", "¥", AssetCategory.FIAT, 1.0 / 7.24, 2, "Fiat CNY", 0xFFDC2626, listOf("yuan", "rmb", "china", "cny")),

    // Digital Currencies (Crypto)
    CurrencyAsset("BTC", "Bitcoin", "₿", AssetCategory.CRYPTO, 94250.0, 6, "Bitcoin Network", 0xFFF7931A, listOf("bitcoin", "btc", "satoshi")),
    CurrencyAsset("ETH", "Ethereum", "Ξ", AssetCategory.CRYPTO, 3340.0, 6, "Ethereum (ERC-20)", 0xFF627EEA, listOf("ethereum", "ether", "eth")),
    CurrencyAsset("SOL", "Solana", "◎", AssetCategory.CRYPTO, 238.5, 4, "Solana SPL", 0xFF14F195, listOf("solana", "sol")),
    CurrencyAsset("USDT", "Tether USD", "₮", AssetCategory.CRYPTO, 1.0, 2, "TRON (TRC-20)", 0xFF26A17B, listOf("tether", "usdt", "stablecoin")),
    CurrencyAsset("USDC", "USD Coin", "$", AssetCategory.CRYPTO, 1.0, 2, "Base / ERC-20", 0xFF2775CA, listOf("usdc", "circle", "stablecoin")),
    CurrencyAsset("BNB", "BNB Chain", "BNB", AssetCategory.CRYPTO, 648.0, 4, "BNB Smart Chain", 0xFFF3BA2F, listOf("binance", "bnb")),
    CurrencyAsset("XRP", "Ripple XRP", "✕", AssetCategory.CRYPTO, 1.42, 4, "XRP Ledger", 0xFF38BDF8, listOf("ripple", "xrp")),
    CurrencyAsset("TON", "Toncoin", "💎", AssetCategory.CRYPTO, 6.18, 4, "TON Network", 0xFF0098EA, listOf("ton", "toncoin")),
    CurrencyAsset("DOGE", "Dogecoin", "Ð", AssetCategory.CRYPTO, 0.39, 4, "Dogecoin", 0xFFC2A633, listOf("doge", "dogecoin")),
    CurrencyAsset("ADA", "Cardano", "₳", AssetCategory.CRYPTO, 0.98, 4, "Cardano", 0xFF0033AD, listOf("cardano", "ada")),
    CurrencyAsset("AVAX", "Avalanche", "AVAX", AssetCategory.CRYPTO, 41.2, 4, "Avalanche C-Chain", 0xFFE84142, listOf("avalanche", "avax")),
    CurrencyAsset("LINK", "Chainlink", "⬡", AssetCategory.CRYPTO, 17.8, 4, "Ethereum (ERC-20)", 0xFF2A5ADA, listOf("chainlink", "link")),
  )

  private val byCode = assets.associateBy { it.code }

  fun find(code: String): CurrencyAsset = byCode[code.uppercase()] ?: assets.first()

  val fiatAssets: List<CurrencyAsset> = assets.filter { it.category == AssetCategory.FIAT }
  val cryptoAssets: List<CurrencyAsset> = assets.filter { it.category == AssetCategory.CRYPTO }
  val settlementFiats: List<CurrencyAsset> = fiatAssets.filter { it.code in setOf("USD", "NGN", "GBP", "EUR", "CAD", "GHS", "KES", "ZAR") }

  val defaultPairs: List<Pair<String, String>> = listOf(
    "USD" to "NGN",
    "USD" to "GBP",
    "USD" to "USD",
    "USD" to "EUR",
    "USD" to "CAD",
    "USD" to "GHS",
    "USD" to "KES",
    "USD" to "ZAR",
    "USD" to "JPY",
    "USD" to "AED",
    "GBP" to "NGN",
    "EUR" to "NGN",
    "BTC" to "USD",
    "BTC" to "NGN",
    "BTC" to "GBP",
    "ETH" to "USD",
    "ETH" to "NGN",
    "SOL" to "USD",
    "SOL" to "NGN",
    "USDT" to "NGN",
    "USDT" to "GBP",
    "BNB" to "USD",
    "XRP" to "USD",
    "TON" to "USD",
  )
}
