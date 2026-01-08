package ecccomp.s2240195.iotchokinapp

import com.google.gson.annotations.SerializedName

// 楽天APIレスポンス全体
data class RakutenApiResponse(
    @SerializedName("Items")
    val Items: List<RakutenItemDetail>? = null,  // 直接 RakutenItemDetail のリスト

    @SerializedName("count")
    val count: Int? = null,

    @SerializedName("page")
    val page: Int? = null,

    @SerializedName("first")
    val first: Int? = null,

    @SerializedName("last")
    val last: Int? = null,

    @SerializedName("hits")
    val hits: Int? = null,

    @SerializedName("carrier")
    val carrier: Int? = null,

    @SerializedName("pageCount")
    val pageCount: Int? = null
)

// 商品詳細

data class RakutenItemDetail(
    @SerializedName("itemName")
    val itemName: String? = null,

    @SerializedName("itemPrice")
    val itemPrice: Int? = null,

    @SerializedName("itemUrl")
    val itemUrl: String? = null,

    @SerializedName("itemCode")
    val itemCode: String? = null,

    @SerializedName("mediumImageUrls")
    val mediumImageUrls: List<String>? = null
) {
    // 最高画質の画像URLを取得
    fun getHighQualityImageUrl(): String? {
        val baseUrl = mediumImageUrls?.firstOrNull() ?: return null
        return baseUrl
            .replace(Regex("\\?_ex=\\d+x\\d+"), "?_ex=500x500")
            .replace(Regex("_ex=\\d+x\\d+"), "_ex=500x500")
    }
}