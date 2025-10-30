

package ecccomp.s2240195.iotchokinapp

import com.google.firebase.Timestamp

data class Wish(
    val goalType: String = "",           // "rakuten" or "custom"
    val itemName: String = "",           // 商品名 or 目標名
    val itemUrl: String = "",            // 商品URL（楽天の場合）
    val imageUrl: String = "",           // 商品画像URL
    val itemCode: String = "",           // 商品コード（楽天の場合）
    val targetAmount: Int = 0,           // 目標金額
    val currentAmount: Int = 0,          // 現在の貯金額
    val isActive: Boolean = true,        // 進行中か
    val isSelected: Boolean = false,     // ホームで選択中か
    val createdAt: Timestamp = Timestamp.now(),
    val achievedAt: Timestamp? = null
)