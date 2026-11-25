package com.example.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class OpenExchangeRateResponse(
  val result: String? = null,
  @Json(name = "base_code") val baseCode: String? = null,
  val rates: Map<String, Double>? = null,
)

@JsonClass(generateAdapter = true)
data class CoinbaseRatesResponse(
  val data: CoinbaseRatesData? = null,
)

@JsonClass(generateAdapter = true)
data class CoinbaseRatesData(
  val currency: String? = null,
  val rates: Map<String, String>? = null,
)

interface FiatRatesApi {
  @GET("v6/latest/USD")
  suspend fun getLatestUsdRates(): OpenExchangeRateResponse
}

interface CryptoRatesApi {
  @GET("v2/exchange-rates")
  suspend fun getExchangeRates(@Query("currency") currency: String = "USD"): CoinbaseRatesResponse

  @GET("api/v3/simple/price")
  suspend fun getCoinGeckoPrices(
    @Query("ids") ids: String = "bitcoin,ethereum,solana,tether,usd-coin,binancecoin,ripple,the-open-network,dogecoin,cardano,avalanche-2,chainlink",
    @Query("vs_currencies") vsCurrencies: String = "usd",
    @Query("include_24hr_change") include24hChange: Boolean = true,
  ): Map<String, Map<String, Double>>
}
