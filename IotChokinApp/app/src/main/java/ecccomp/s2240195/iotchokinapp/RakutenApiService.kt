package ecccomp.s2240195.iotchokinapp

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


interface RakutenApiService {
    // ここにAPI呼び出しのメソッド
    @GET("IchibaItem/Search/20170706")
    fun searchItem(
        @Query("applicationId") applicationId: String,
        @Query("keyword") keyword: String,
        @Query("hits") hits: Int = 30,
        @Query("imageFlag") imageFlag: Int = 1
    ): Call<RakutenApiResponse>
}

object RakutenApiClient{
    private val retrofit = Retrofit.Builder()
        .addConverterFactory(GsonConverterFactory.create())
        .baseUrl(Config.BASE_URL)
        .build()
    val apiService: RakutenApiService = retrofit.create(RakutenApiService::class.java)
}