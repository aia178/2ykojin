package ecccomp.s2240195.iotchokinapp

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.* // TextView/Toast等
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
// ⬜A: Retrofit/Converter の import（未導入なら追加）
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
// ⬜B: あなたの API/Adapter クラスの import（パッケージに合わせる）
import ecccomp.s2240195.iotchokinapp.data.api.RakutenApiService
import ecccomp.s2240195.iotchokinapp.data.api.RakutenApiResponse

class NewGoalActivity : AppCompatActivity() {

    // region --- UI Components ---
    private lateinit var tabLayout: TabLayout
    private lateinit var rakutenSearchCard: MaterialCardView
    private lateinit var customGoalCard: MaterialCardView

    // 検索タブ内
    private lateinit var editKeyword: EditText
    private lateinit var btnSearch: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var progress: ProgressBar
    // （任意）空・エラー状態ビューがあれば使う
    private var stateEmpty: View? = null
    private var stateError: View? = null
    private var btnRetry: Button? = null
    // endregion

    // region --- Adapter / API ---
    private lateinit var adapter: ProductAdapter  // ⬜1: 既存のアダプタ型に合わせる
    private lateinit var apiService: RakutenApiService
    // endregion

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_goal)

        // region --- View 初期化（IDはレイアウトに合わせて埋める） ---
        tabLayout = findViewById(R.id.tabLayout)
        rakutenSearchCard = findViewById(R.id.rakutenSearchCard)
        customGoalCard = findViewById(R.id.customGoalCard)

        editKeyword = findViewById(/* ⬜2: 検索キーワード EditText のID */ R.id.editKeyword)
        btnSearch   = findViewById(/* ⬜3: 検索ボタンのID */ R.id.btnSearch)
        recyclerView= findViewById(/* ⬜4: RecyclerView のID */ R.id.rvProducts)
        progress    = findViewById(/* ⬜5: ProgressBar のID */ R.id.progress)

        // 任意：空・エラー状態ビューを使う場合のみ設定
        stateEmpty  = findViewById(/* ⬜6: 空状態ビューID（なければ null のまま） */ 0)
        stateError  = findViewById(/* ⬜7: エラー状態ビューID（なければ null のまま） */ 0)
        btnRetry    = findViewById(/* ⬜8: 再試行ボタンID（なければ null のまま） */ 0)
        // endregion

        // 戻るボタン
        findViewById<ImageButton>(R.id.btnBack)?.setOnClickListener { finish() }

        // region --- RecyclerView 設定 ---
        adapter = /* ⬜9: 生成方法 */ ProductAdapter()
        recyclerView.apply {
            adapter = this@NewGoalActivity.adapter
            layoutManager = LinearLayoutManager(this@NewGoalActivity)
            setHasFixedSize(true)
        }
        // endregion

        // region --- Retrofit / API 初期化 ---
        // 既にどこかで apiService をDIしている場合は、下記ブロックは削除し、差し替えること
        val retrofit = Retrofit.Builder()
            .baseUrl(/* ⬜10: 楽天API BASE_URL */ "https://app.rakuten.co.jp/services/api/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(RakutenApiService::class.java)
        // endregion

        // region --- タブ切替 ---
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> { // 楽天検索タブ
                        rakutenSearchCard.visibility = View.VISIBLE
                        customGoalCard.visibility = View.GONE
                    }
                    1 -> { // カスタム目標タブ
                        rakutenSearchCard.visibility = View.GONE
                        customGoalCard.visibility = View.VISIBLE
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        // endregion

        // region --- 検索ボタン押下：楽天検索の実行 ---
        btnSearch.setOnClickListener {
            val keyword = editKeyword.text.toString().trim()

            // 入力バリデーション
            if (keyword.isEmpty()) {
                Toast.makeText(this, "キーワードを入力してください", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 状態切替：Loading を前面に出し、他は隠す
            setLoading(true)

            // API呼び出し（Retrofit Callback形式）
            val call = apiService.searchItem(
                /* applicationId = */ /* ⬜11: 安全に保持したアプリID */ BuildConfig.RAKUTEN_APP_ID,
                /* keyword       = */ keyword,
                /* hits          = */ /* ⬜12: 1ページ件数 */ 30,
                /* imageFlag     = */ /* ⬜13: 画像必須フラグ */ 1
            )

            call.enqueue(object : Callback<RakutenApiResponse> {
                override fun onResponse(
                    call: Call<RakutenApiResponse>,
                    response: Response<RakutenApiResponse>
                ) {
                    setLoading(false)

                    if (!response.isSuccessful) {
                        // 業務メモ：HTTPステータスに応じて文面を調整（429/503等）
                        Log.e("RakutenAPI", "HTTP Error: ${response.code()}")
                        showError("通信エラー: ${response.code()}")
                        return
                    }

                    // 業務メモ：DTOの階層はプロジェクト定義に依存。items が null/空を許容。
                    val items = response.body()?.items

                    if (items.isNullOrEmpty()) {
                        showEmpty()
                        return
                    }

                    // 検索結果反映
                    adapter.submitList(items)
                    recyclerView.visibility = View.VISIBLE
                    stateEmpty?.visibility = View.GONE
                    stateError?.visibility = View.GONE

                    Log.d("RakutenAPI", "検索結果: ${items.size}件")
                }

                override fun onFailure(call: Call<RakutenApiResponse>, t: Throwable) {
                    // 業務メモ：ネットワーク障害/タイムアウト等
                    setLoading(false)
                    Log.e("RakutenAPI", "通信失敗", t)
                    showError("通信に失敗しました。ネットワークをご確認ください。")
                }
            })
        }
        // endregion

        // 任意：エラー時再試行
        btnRetry?.setOnClickListener {
            // 業務メモ：ユーザー操作を統一するため、検索ボタンのクリックを再利用
            btnSearch.performClick()
        }
    }

    // region --- UI 状態切替ヘルパ ---
    private fun setLoading(isLoading: Boolean) {
        progress.visibility = if (isLoading) View.VISIBLE else View.GONE
        if (isLoading) {
            recyclerView.visibility = View.GONE
            stateEmpty?.visibility = View.GONE
            stateError?.visibility = View.GONE
        }
    }

    private fun showEmpty() {
        recyclerView.visibility = View.GONE
        stateError?.visibility = View.GONE
        stateEmpty?.visibility = View.VISIBLE
        Toast.makeText(this, "該当する商品が見つかりませんでした", Toast.LENGTH_SHORT).show()
    }

    private fun showError(message: String) {
        recyclerView.visibility = View.GONE
        stateEmpty?.visibility = View.GONE
        stateError?.visibility = View.VISIBLE
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    // endregion
}
