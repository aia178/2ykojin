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

    private lateinit var tabLayout: TabLayout
    private lateinit var rakutenSearchCard: MaterialCardView
    private lateinit var customGoalCard: MaterialCardView
    private lateinit var etSearchProduct: TextInputEditText
    private lateinit var btnSearch: MaterialButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchLoading: View
    private lateinit var emptySearchView: View
    private lateinit var errorSearchView: View
    private lateinit var btnRetrySearch: MaterialButton
    private lateinit var etTargetAmount: TextInputEditText
    private lateinit var etGoalName: TextInputEditText
    private lateinit var btnSave: MaterialButton
    private lateinit var firestore: FirebaseFirestore

    private var productList = mutableListOf<RakutenProduct>()
    private lateinit var adapter: ProductAdapter

    // 最後の検索キーワードを保持（リトライ用）
    private var lastSearchKeyword = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_goal)

        initViews()
        setupRecyclerView()
        setupTabs()

        firestore = FirebaseFirestore.getInstance()

        btnSearch.setOnClickListener {
            val keyword = etSearchProduct.text?.toString()?.trim() ?: ""
            if (keyword.length < 2) {
                Toast.makeText(this, "2文字以上で入力してください", Toast.LENGTH_SHORT).show()
            } else {
                searchProducts(keyword)
            }
        }

        btnRetrySearch.setOnClickListener {
            if (lastSearchKeyword.isNotEmpty()) {
                searchProducts(lastSearchKeyword)
            }
        }

        btnSave.setOnClickListener {
            val goalName = etGoalName.text.toString().trim()
            val targetAmountStr = etTargetAmount.text.toString().trim()
            val targetAmount = targetAmountStr.toIntOrNull() ?: 0

            if (goalName.isEmpty() || targetAmount <= 0) {
                Toast.makeText(this, "目標名と目標設定額を正しく入力してください", Toast.LENGTH_SHORT).show()
            } else {
                saveCustomGoalToFirestore(goalName, targetAmount)
            }
        }

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
        searchLoading = findViewById(R.id.searchLoading)
        emptySearchView = findViewById(R.id.emptySearchView)
        errorSearchView = findViewById(R.id.errorSearchView)
        btnRetrySearch = findViewById(R.id.btnRetrySearch)
        btnSave = findViewById(R.id.btnSave)
        etGoalName = findViewById(R.id.etGoalName)
        etTargetAmount = findViewById(R.id.etTargetAmount)
    }

    private fun setupRecyclerView() {
        adapter = ProductAdapter(productList) { product ->
            showEditGoalNameDialog(product)
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

    /**
     * 商品検索を実行し、UI状態を管理するメソッド
     */
    @SuppressLint("NotifyDataSetChanged")
    private fun searchProducts(keyword: String) {
        // キーワードを保存（リトライ用）
        lastSearchKeyword = keyword

        // 1. 検索前の状態設定
        recyclerView.visibility = View.GONE
        emptySearchView.visibility = View.GONE
        errorSearchView.visibility = View.GONE
        searchLoading.visibility = View.VISIBLE

        // 検索ボタンを無効化（連打防止）
        btnSearch.isEnabled = false

        val call = RakutenApiClient.apiService.searchItem(
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
                // 検索完了後、ボタンを再有効化
                btnSearch.isEnabled = true
                searchLoading.visibility = View.GONE

                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    val items = apiResponse?.Items

                    if (items.isNullOrEmpty()) {
                        // 2. 検索結果が空の場合
                        productList.clear()
                        adapter.notifyDataSetChanged()
                        emptySearchView.visibility = View.VISIBLE
                        return
                    }

                    val products = items.mapNotNull { item ->
                        if (item.itemName.isNullOrBlank() || item.itemPrice == null) {
                            return@mapNotNull null
                        }

                        val imageUrl = item.mediumImageUrls?.firstOrNull() ?: ""

                        RakutenProduct(
                            itemName = item.itemName,
                            itemPrice = item.itemPrice,
                            itemUrl = item.itemUrl ?: "",
                            itemCode = item.itemCode ?: "",
                            imageUrl = imageUrl
                        )
                    }

                    if (products.isEmpty()) {
                        // 有効な結果がない場合も空状態を表示
                        productList.clear()
                        adapter.notifyDataSetChanged()
                        emptySearchView.visibility = View.VISIBLE
                    } else {
                        // 3. 検索成功：結果を表示
                        productList.clear()
                        productList.addAll(products)
                        adapter.notifyDataSetChanged()
                        recyclerView.visibility = View.VISIBLE

                        Toast.makeText(
                            this@NewGoalActivity,
                            "${products.size}件見つかりました",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    // 4. 通信エラー（HTTPエラー）
                    errorSearchView.visibility = View.VISIBLE
                    Toast.makeText(
                        this@NewGoalActivity,
                        "通信エラー: ${response.code()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFailure(call: Call<RakutenApiResponse>, t: Throwable) {
                // 検索完了後、ボタンを再有効化
                btnSearch.isEnabled = true
                searchLoading.visibility = View.GONE

                // 5. 通信失敗（ネットワークエラーなど）
                errorSearchView.visibility = View.VISIBLE
                Toast.makeText(
                    this@NewGoalActivity,
                    "通信失敗: ${t.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun saveGoalToFirestore(product: RakutenProduct) {
        val shortName = TextUtils.shortenProductName(product.itemName)

        val wish = Wish(
            goalType = "rakuten",
            itemName = shortName,
            itemUrl = product.itemUrl,
            imageUrl = product.imageUrl,
            itemCode = product.itemCode,
            targetAmount = product.itemPrice,
            currentAmount = 0,
            active = true,
            selected = true
        )

        saveGoalExclusively(
            wish = wish,
            onSuccess = {
                Toast.makeText(this, "目標を保存しました", Toast.LENGTH_SHORT).show()
                finish()
            },
            onError = { e ->
                Toast.makeText(this, "保存失敗: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        )
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
            active = true,
            selected = true
        )

        saveGoalExclusively(
            wish = wish,
            onSuccess = {
                Toast.makeText(this, "目標を保存しました!!", Toast.LENGTH_SHORT).show()
                finish()
            },
            onError = { e ->
                Toast.makeText(this, "保存失敗: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun saveGoalExclusively(
        wish: Wish,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val goals = firestore.collection("goals")

        goals.whereEqualTo("selected", true)
            .get()
            .addOnSuccessListener { snapshot ->
                val batch = firestore.batch()

                snapshot.documents.forEach { doc ->
                    batch.update(doc.reference, "selected", false)
                }

                val newGoalRef = goals.document()
                batch.set(newGoalRef, wish)

                batch.commit()
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { onError(it) }
            }
            .addOnFailureListener { onError(it) }
    }

    private fun showEditGoalNameDialog(product: RakutenProduct) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_display_name, null)
        val etEditGoalName = dialogView.findViewById<TextInputEditText>(R.id.etDisplayName)

        val shortName = TextUtils.shortenProductName(product.itemName)
        etEditGoalName.setText(shortName)
        etEditGoalName.setSelection(shortName.length)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialogView.findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<MaterialButton>(R.id.btnSave).setOnClickListener {
            val editedName = etEditGoalName.text.toString().trim()
            if (editedName.isEmpty()) {
                Toast.makeText(this, "目標名を入力してください", Toast.LENGTH_SHORT).show()
            } else {
                dialog.dismiss()
                saveGoalToFirestore(product.copy(itemName = editedName))
            }
        }

        dialog.show()
    }
}