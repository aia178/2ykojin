package ecccomp.s2240195.iotchokinapp

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load

class ProductAdapter(
    private val products: List<RakutenProduct>,
    private val onItemClick: (RakutenProduct) -> Unit
) : RecyclerView.Adapter<ProductViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_product, parent, false)
        return ProductViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val product = products[position]
        holder.titleName.text = product.itemName
        holder.price.text = "¥${product.itemPrice}"
        holder.image.load(product.imageUrl)
        holder.itemView.setOnClickListener {
            onItemClick(product)
        }
    }

    override fun getItemCount(): Int = products.size
}

class ProductViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val image: ImageView = view.findViewById(R.id.ivThumb)
    val titleName: TextView = view.findViewById(R.id.tvName)
    val price: TextView = view.findViewById(R.id.tvPrice)
}
