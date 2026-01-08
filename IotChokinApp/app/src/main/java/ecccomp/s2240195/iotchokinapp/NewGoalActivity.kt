package ecccomp.s2240195.iotchokinapp

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

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

    // カスタム目標の画像選択関連
    private lateinit var cardImagePicker: MaterialCardView
    private lateinit var ivCustomGoalImage: ImageView
    private lateinit var placeholderImagePicker: LinearLayout
    private lateinit var btnRemoveImage: MaterialButton
    private var selectedImageUri: Uri? = null

    private var productList = mutableListOf<RakutenProduct>()
    private lateinit var adapter: ProductAdapter

    // 最後の検索キーワードを保持（リトライ用）
    private var lastSearchKeyword = ""

    // 画像選択用のランチャー
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            showSelectedImage(it)
        }
    }

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

        // 画像選択UI
        cardImagePicker = findViewById(R.id.cardImagePicker)
        ivCustomGoalImage = findViewById(R.id.ivCustomGoalImage)
        placeholderImagePicker = findViewById(R.id.placeholderImagePicker)
        btnRemoveImage = findViewById(R.id.btnRemoveImage)

        // 画像選択カードのクリックで画像を選択
        cardImagePicker.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        // 画像削除ボタン
        btnRemoveImage.setOnClickListener {
            removeSelectedImage()
        }
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

                        val imageUrl = item.getHighQualityImageUrl() ?: ""

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
        // 画像が選択されている場合はローカルにコピー
        val imageUrl = if (selectedImageUri != null) {
            saveImageToLocalStorage(selectedImageUri!!) ?: ""
        } else {
            ""
        }

        val wish = Wish(
            goalType = "custom",
            itemName = goalName,
            targetAmount = targetAmount,
            currentAmount = 0,
            imageUrl = imageUrl,
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

    // 画像をアプリのローカルストレージにコピー
    private fun saveImageToLocalStorage(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val fileName = "goal_${UUID.randomUUID()}.jpg"
            val file = File(filesDir, fileName)
            
            FileOutputStream(file).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            inputStream.close()
            
            file.absolutePath
        } catch (e: Exception) {
            Toast.makeText(this, "画像保存エラー: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        }
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

    // 選択した画像をプレビュー表示
    private fun showSelectedImage(uri: Uri) {
        ivCustomGoalImage.load(uri) {
            crossfade(true)
        }
        ivCustomGoalImage.visibility = View.VISIBLE
        placeholderImagePicker.visibility = View.GONE
        btnRemoveImage.visibility = View.VISIBLE
    }

    // 画像選択を解除
    private fun removeSelectedImage() {
        selectedImageUri = null
        ivCustomGoalImage.visibility = View.GONE
        ivCustomGoalImage.setImageResource(android.R.drawable.ic_menu_gallery)
        placeholderImagePicker.visibility = View.VISIBLE
        btnRemoveImage.visibility = View.GONE
    }
}