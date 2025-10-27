package ecccomp.s2240195.iotchokinapp

data class RakutenApiResponse (
    val Items: List<RakutenItemWrapper> //　商品リスト
)


data class RakutenItemWrapper(
    val Item: RakutenItemDetail //　商品
)

data class RakutenItemDetail(
    val itemName: String, //　商品名
    val itemPrice: Int, //　値段
    val itemUrl: String, // うｒｌ
    val itemCode: String, // 商品コード
    val mediumImageUrls: List<RakutenImageUrl> // 画像まとめ
)

data class RakutenImageUrl(
    val imageUrl: String //　商品画像
)


