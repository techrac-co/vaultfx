package com.example.data

import android.util.Log
import com.example.data.network.CryptoRatesApi
import com.example.data.network.FiatRatesApi
import com.example.model.AssetCatalog
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit
import kotlin.math.sin
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

data class MarketSnapshot(
  val usdPrices: Map<String, Double> = AssetCatalog.assets.associate { it.code to it.defaultUsdPrice },
  val change24hPct: Map<String, Double> = default24hChanges,
  val isLive: Boolean = false,
  val isRefreshing: Boolean = false,
  val lastUpdatedMs: Long = System.currentTimeMillis(),
) {
  fun usdValueOf(code: String): Double =
    usdPrices[code.uppercase()] ?: AssetCatalog.find(code).defaultUsdPrice

  fun convert(amount: Double, fromCode: String, toCode: String): Double {
    if (fromCode.equals(toCode, ignoreCase = true)) return amount
    val fromUsd = usdValueOf(fromCode)
    val toUsd = usdValueOf(toCode)
    if (toUsd <= 0.0) return 0.0
    return (amount * fromUsd) / toUsd
  }

  fun rate(fromCode: String, toCode: String): Double = convert(1.0, fromCode, toCode)

  fun pairChange24h(baseCode: String, quoteCode: String): Double {
    val baseChg = change24hPct[baseCode.uppercase()] ?: 0.0
    val quoteChg = change24hPct[quoteCode.uppercase()] ?: 0.0
    return baseChg - quoteChg
  }

  fun sparklineFor(baseCode: String, quoteCode: String, points: Int = 24, rangeFactor: Double = 1.0): List<Double> {
    val current = rate(baseCode, quoteCode)
    val chg = pairChange24h(baseCode, quoteCode)
    val seed = (baseCode + quoteCode).hashCode()
    val volatility = if (AssetCatalog.find(baseCode).category.name == "CRYPTO" || AssetCatalog.find(quoteCode).category.name == "CRYPTO") 0.025 else 0.004
    val drift = (chg / 100.0) * rangeFactor
    return List(points) { idx ->
      val progress = idx.toDouble() / (points - 1).coerceAtLeast(1)
      val wave = sin((idx + (seed % 11)) * 0.65) * volatility * rangeFactor * (1.0 - progress * 0.4)
      val trend = -drift * (1.0 - progress)
      (current * (1.0 + trend + wave)).coerceAtLeast(current * 0.5)
    }
  }
}

private val default24hChanges = mapOf(
  "BTC" to 2.84,
  "ETH" to 3.41,
  "SOL" to 5.12,
  "USDT" to 0.01,
  "USDC" to 0.00,
  "BNB" to 1.67,
  "XRP" to 4.25,
  "TON" to -1.18,
  "DOGE" to 6.05,
  "ADA" to 2.14,
  "AVAX" to -0.85,
  "LINK" to 3.72,
  "USD" to 0.00,
  "NGN" to -0.32,
  "GBP" to 0.18,
  "EUR" to 0.11,
  "CAD" to -0.09,
  "GHS" to -0.24,
  "KES" to 0.05,
  "ZAR" to 0.41,
  "JPY" to -0.27,
  "AED" to 0.00,
  "AUD" to 0.22,
  "CHF" to 0.15,
  "INR" to -0.04,
  "CNY" to -0.08,
)

class MarketRatesRepository {
  private val moshi = Moshi.Builder()
    .addLast(KotlinJsonAdapterFactory())
    .build()

  private val okHttp = OkHttpClient.Builder()
    .connectTimeout(6, TimeUnit.SECONDS)
    .readTimeout(6, TimeUnit.SECONDS)
    .build()

  private val fiatApi: FiatRatesApi = Retrofit.Builder()
    .baseUrl("https://open.er-api.com/")
    .client(okHttp)
    .addConverterFactory(MoshiConverterFactory.create(moshi))
    .build()
    .create(FiatRatesApi::class.java)

  private val coinbaseApi: CryptoRatesApi = Retrofit.Builder()
    .baseUrl("https://api.coinbase.com/")
    .client(okHttp)
    .addConverterFactory(MoshiConverterFactory.create(moshi))
    .build()
    .create(CryptoRatesApi::class.java)

  private val coinGeckoApi: CryptoRatesApi = Retrofit.Builder()
    .baseUrl("https://api.coingecko.com/")
    .client(okHttp)
    .addConverterFactory(MoshiConverterFactory.create(moshi))
    .build()
    .create(CryptoRatesApi::class.java)

  private val _snapshot = MutableStateFlow(MarketSnapshot())
  val snapshot: StateFlow<MarketSnapshot> = _snapshot.asStateFlow()

  private val coinGeckoIdMap = mapOf(
    "bitcoin" to "BTC",
    "ethereum" to "ETH",
    "solana" to "SOL",
    "tether" to "USDT",
    "usd-coin" to "USDC",
    "binancecoin" to "BNB",
    "ripple" to "XRP",
    "the-open-network" to "TON",
    "dogecoin" to "DOGE",
    "cardano" to "ADA",
    "avalanche-2" to "AVAX",
    "chainlink" to "LINK",
  )

  suspend fun refreshRates() {
    _snapshot.value = _snapshot.value.copy(isRefreshing = true)
    val updatedPrices = _snapshot.value.usdPrices.toMutableMap()
    val updatedChanges = _snapshot.value.change24hPct.toMutableMap()
    var anyLiveSuccess = false

    coroutineScope {
      val fiatJob = async {
        runCatching { fiatApi.getLatestUsdRates() }.getOrNull()
      }
      val coinbaseJob = async {
        runCatching { coinbaseApi.getExchangeRates("USD") }.getOrNull()
      }
      val geckoJob = async {
        runCatching { coinGeckoApi.getCoinGeckoPrices() }.getOrNull()
      }

      fiatJob.await()?.rates?.let { rates ->
        anyLiveSuccess = true
        for (asset in AssetCatalog.fiatAssets) {
          val perUsd = rates[asset.code]
          if (perUsd != null && perUsd > 0.0) {
            updatedPrices[asset.code] = 1.0 / perUsd
          }
        }
      }

      coinbaseJob.await()?.data?.rates?.let { rates ->
        anyLiveSuccess = true
        for (asset in AssetCatalog.cryptoAssets) {
          val perUsd = rates[asset.code]?.toDoubleOrNull()
          if (perUsd != null && perUsd > 0.0) {
            updatedPrices[asset.code] = 1.0 / perUsd
          }
        }
      }

      geckoJob.await()?.let { geckoMap ->
        anyLiveSuccess = true
        for ((id, values) in geckoMap) {
          val code = coinGeckoIdMap[id] ?: continue
          values["usd"]?.takeIf { it > 0.0 }?.let { updatedPrices[code] = it }
          values["usd_24h_change"]?.let { updatedChanges[code] = it }
        }
      }
    }

    if (!anyLiveSuccess) {
      Log.w("MarketRatesRepo", "Live rate fetch unavailable; retaining cached baseline rates")
    }

    _snapshot.value = _snapshot.value.copy(
      usdPrices = updatedPrices,
      change24hPct = updatedChanges,
      isLive = anyLiveSuccess || _snapshot.value.isLive,
      isRefreshing = false,
      lastUpdatedMs = if (anyLiveSuccess) System.currentTimeMillis() else _snapshot.value.lastUpdatedMs,
    )
  }
}
