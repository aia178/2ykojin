package ecccomp.s2240195.iotchokinapp

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class NewGoalActivity : AppCompatActivity() {

    // UI Components
    private lateinit var tabLayout: TabLayout
    private lateinit var rakutenSearchCard: MaterialCardView
    private lateinit var customGoalCard: MaterialCardView
    private lateinit var etSearchProduct: TextInputEditText
    private lateinit var btnSearch: MaterialButton
    private lateinit var recyclerView: RecyclerView

    // Adapter
    private var productList = mutableListOf<RakutenProduct>()
    private lateinit var adapter: ProductAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_goal)

        // ビューの初期化
        initViews()

        // RecyclerView設定
        setupRecyclerView()

        // タブ切り替え設定
        setupTabs()

        // 検索ボタン
        btnSearch.setOnClickListener {
            val keyword = etSearchProduct.text.toString().trim()
            if (keyword.isEmpty()) {
                Toast.makeText(this, "キーワードを入力してください", Toast.LENGTH_SHORT).show()
            } else {
                searchProducts(keyword)
            }
        }

        // 戻るボタン
        findViewById<android.widget.ImageButton>(R.id.btnBack)?.setOnClickListener {
            finish()
        }
    }

    private fun initViews() {
        tabLayout = findViewById(R.id.tabLayout)
        rakutenSearchCard = findViewById(R.id.rakutenSearchCard)
        customGoalCard = findViewById(R.id.customGoalCard)
        etSearchProduct = findViewById(R.id.etSearchProduct)
        btnSearch = findViewById(R.id.btnSearch)
        recyclerView = findViewById(R.id.rvSearchResults)
    }

    private fun setupRecyclerView() {
        adapter = ProductAdapter(productList) { product ->
            // 商品がクリックされたときの処理
            Toast.makeText(this, "${product.itemName} を選択", Toast.LENGTH_SHORT).show()
            // TODO: Firestoreに保存する処理を追加
        }

        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun setupTabs() {
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        rakutenSearchCard.visibility = View.VISIBLE
                        customGoalCard.visibility = View.GONE
                    }
                    1 -> {
                        rakutenSearchCard.visibility = View.GONE
                        customGoalCard.visibility = View.VISIBLE
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun searchProducts(keyword: String) {
        // API呼び出し
        val call = SEARCH_SERVICE.apiService.searchItem(
            applicationId = Config.APPLICATION_ID,
            keyword = keyword,
            hits = 30,
            imageFlag = 1
        )

        call.enqueue(object : Callback<RakutenApiResponse> {
            override fun onResponse(
                call: Call<RakutenApiResponse>,
                response: Response<RakutenApiResponse>
            ) {
                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    val items = apiResponse?.Items

                    if (items.isNullOrEmpty()) {
                        Toast.makeText(
                            this@NewGoalActivity,
                            "検索結果がありません",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        // RakutenProduct に変換
                        val products = items.mapNotNull { wrapper ->
                            val item = wrapper.Item
                            val imageUrl = item.mediumImageUrls.firstOrNull()?.imageUrl ?: ""

                            RakutenProduct(
                                itemName = item.itemName,
                                itemPrice = item.itemPrice,
                                itemUrl = item.itemUrl,
                                itemCode = item.itemCode,
                                imageUrl = imageUrl
                            )
                        }

                        // リスト更新
                        productList.clear()
                        productList.addAll(products)
                        adapter.notifyDataSetChanged()

                        Toast.makeText(
                            this@NewGoalActivity,
                            "${products.size}件見つかりました",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        this@NewGoalActivity,
                        "通信エラー: ${response.code()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFailure(call: Call<RakutenApiResponse>, t: Throwable) {
                Toast.makeText(
                    this@NewGoalActivity,
                    "通信失敗: ${t.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }
}