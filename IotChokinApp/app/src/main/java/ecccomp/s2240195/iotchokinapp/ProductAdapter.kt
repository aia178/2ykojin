package ecccomp.s2240195.iotchokinapp

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load

// Adapter：RecyclerView で商品リストを表示するクラス
class ProductAdapter(
    private val products: List<RakutenProduct>,             // 商品データのリスト
    private val onItemClick: (RakutenProduct) -> Unit       // クリック時の処理（ラムダで受け取る）
) : RecyclerView.Adapter<ProductViewHolder>() {

    // ViewHolderの生成：1行分のレイアウトを inflate して ViewHolder に包む
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_product, parent, false)
        return ProductViewHolder(view)
    }

    // ViewHolderとデータを結びつける（ここで実際に表示内容を更新）
    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val product = products[position]  // 現在の表示対象データを取得

        // 商品名をテキストビューに反映
        holder.titleName.text = product.itemName

        // 価格を表示（例：「¥9999」）
        holder.price.text = "¥${product.itemPrice}"

        // 画像URLを読み込んで表示（Coilを使用）
        holder.image.load(product.imageUrl)

        // アイテム全体をタップしたときの動作を設定
        holder.itemView.setOnClickListener {
            onItemClick(product)
        }
    }

    // リストの件数を返す（RecyclerViewが描画回数を決めるために必要）
    override fun getItemCount(): Int = products.size
}

// 1行分のビュー要素を保持するクラス（findViewByIdをまとめておく）
class ProductViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val image: ImageView = view.findViewById(R.id.ivThumb)
    val titleName: TextView = view.findViewById(R.id.tvName)
    val price: TextView = view.findViewById(R.id.tvPrice)
}
