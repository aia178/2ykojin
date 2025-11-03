package ecccomp.s2240195.iotchokinapp

object TextUtils {

    /**
     * 楽天商品名を見やすく短縮する
     */
    fun shortenProductName(fullName: String, maxLength: Int = 30): String {
        var result = fullName

        // 1. 【】『』などの装飾記号とその中身を削除
        result = result.replace(Regex("[【『\\[].+?[】』\\]]"), "")

        // 2. 不要なキーワードを削除
        val removeKeywords = listOf(
            "送料無料", "ポイント", "倍", "クーポン", "セール", "SALE",
            "ランキング", "位", "受賞", "★", "☆", "！", "!",
            "メール便", "あす楽", "即日", "当日", "翌日"
        )

        for (keyword in removeKeywords) {
            result = result.replace(keyword, "")
        }

        // 3. 連続する空白を1つに
        result = result.replace(Regex("\\s+"), " ")

        // 4. 前後の空白を削除
        result = result.trim()

        // 5. 最大文字数に制限
        if (result.length > maxLength) {
            result = result.substring(0, maxLength).trim()
            // 単語の途中で切れないようにする
            val lastSpace = result.lastIndexOf(' ')
            if (lastSpace > maxLength * 0.7) {
                result = result.substring(0, lastSpace)
            }
        }

        // 6. 空の場合は元の名前の最初の部分を返す
        if (result.isEmpty()) {
            result = fullName.take(maxLength)
        }

        return result
    }
}