package ecccomp.s2240195.iotchokinapp

import android.annotation.SuppressLint
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
import com.google.firebase.firestore.FirebaseFirestore
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

    private lateinit var etTargetAmount: TextInputEditText
    private lateinit var etGoalName: TextInputEditText
    private lateinit var btnSave: MaterialButton
    private lateinit var firestore: FirebaseFirestore

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
        // Firestore初期化
        firestore = FirebaseFirestore.getInstance()

        // 検索ボタン
        btnSearch.setOnClickListener {
            val keyword = etSearchProduct.text.toString().trim()
            if (keyword.isEmpty()) {
                Toast.makeText(this, "キーワードを入力してください", Toast.LENGTH_SHORT).show()
            } else {
                searchProducts(keyword)
            }
        }

        btnSave.setOnClickListener {
            val goalName = etGoalName.text.toString().trim()
            val targetAmountStr = etTargetAmount.text.toString().trim()
            val  targetAmount = targetAmountStr.toIntOrNull() ?: 0

            if (goalName.isEmpty() || targetAmount <= 0){
                Toast.makeText(application, "目標名と目標金額を正しく入力してください", Toast.LENGTH_SHORT).show()
            }else{
                saveCustomGoalToFirestore(goalName, targetAmount)
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
        btnSave = findViewById(R.id.btnSave)
        etGoalName = findViewById(R.id.etGoalName)
        etTargetAmount = findViewById(R.id.etTargetAmount)
    }

    private fun setupRecyclerView() {
        adapter = ProductAdapter(productList) { product ->
            // 商品がクリックされたときの処理
            Toast.makeText(this, "${product.itemName} を選択", Toast.LENGTH_SHORT).show()
            saveGoalToFirestore(product)
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
        val call = RakutenApiClient.apiService.searchItem(
            applicationId = Config.APPLICATION_ID,
            keyword = keyword,
            hits = 30,
            imageFlag = 1
        )

        call.enqueue(object : Callback<RakutenApiResponse> {
            @SuppressLint("NotifyDataSetChanged")
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
                        val products = items.map { wrapper ->
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
    private fun saveGoalToFirestore(product: RakutenProduct) {
        val wish = Wish(
            goalType = "rakuten",
            itemName = product.itemName,
            itemUrl = product.itemUrl,
            imageUrl = product.imageUrl,
            itemCode = product.itemCode,
            targetAmount = product.itemPrice,
            currentAmount = 0,
            isActive = true,
            isSelected = true
        )

        firestore.collection("goals")
            .add(wish)
            .addOnSuccessListener {
                Toast.makeText(this, "目標を保存しました", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "保存失敗: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    private fun saveCustomGoalToFirestore(goalName: String, targetAmount: Int) {
        val wish = Wish(
            goalType = "custom",
            itemName = goalName,
            targetAmount = targetAmount,
            currentAmount = 0,
            imageUrl = "",
            itemUrl = "",
            itemCode = "",
            isActive = true,
            isSelected = true
        )

        firestore.collection("goals").add(wish)
            .addOnSuccessListener {
                Toast.makeText(this, "目標を保存しました!!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "保存失敗；；: ${e.message}", Toast.LENGTH_SHORT).show()
            }

    }

}