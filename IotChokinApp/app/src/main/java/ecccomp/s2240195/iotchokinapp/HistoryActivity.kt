package ecccomp.s2240195.iotchokinapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.Timestamp
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.components.XAxis
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date


class HistoryActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var rvHistory: RecyclerView
    private lateinit var emptyView: LinearLayout
    private lateinit var btnBack: ImageButton
    private lateinit var adapter: HistoryAdapter
    private lateinit var lineChartHistory: LineChart
    private var depositList = mutableListOf<Deposit>()
    private var historyListener: com.google.firebase.firestore.ListenerRegistration? = null



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        initViews()
        setupRecyclerView()
        firestore = FirebaseFirestore.getInstance()
        loadHistoryData()
        setupChartAppearance()


        // 戻るボタンで前の画面へ戻る
        btnBack.setOnClickListener {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        historyListener?.remove()
    }

    // Viewの取得
    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        rvHistory = findViewById(R.id.rvHistory)
        emptyView = findViewById(R.id.emptyView)
        lineChartHistory = findViewById(R.id.lineChartHistory)
    }

    // RecyclerViewの設定
    private fun setupRecyclerView() {
        adapter = HistoryAdapter(depositList)
        rvHistory.adapter = adapter
        rvHistory.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
    }

    private fun setupChartAppearance() {
        // データが無いときの表示
        lineChartHistory.setNoDataText("まだ貯金履歴がありません")

        // 説明テキストは非表示
        lineChartHistory.description.isEnabled = false

        // タッチ操作基本設定
        lineChartHistory.setTouchEnabled(true)
        lineChartHistory.isDragEnabled = true
        lineChartHistory.setScaleEnabled(true)
        lineChartHistory.setPinchZoom(true)

        // アニメーション
        lineChartHistory.animateY(800)
    }

    // Firestoreから履歴取得
    @SuppressLint("NotifyDataSetChanged")
    private fun loadHistoryData() {
        historyListener = firestore.collection("deposits")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Toast.makeText(this, "データ取得失敗: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                depositList.clear()

                if (snapshots == null || snapshots.isEmpty) {
                    showEmptyState()
                    return@addSnapshotListener
                }

                for (document in snapshots.documents) {
                    val amount = document.getLong("amount")?.toInt() ?: 0
                    val timestamp = document.getTimestamp("timestamp") ?: Timestamp.now()
                    val goalId = document.getString("goalId") ?: ""
                    val goalName = document.getString("goalName") ?: ""

                    val deposit = Deposit(amount, timestamp, goalId, goalName)
                    depositList.add(deposit)
                }

                adapter.notifyDataSetChanged()
                showListState()
                updateChartWithDeposits(depositList)
            }
    }



    // 履歴0件の表示
    private fun showEmptyState() {
        rvHistory.visibility = android.view.View.GONE
        emptyView.visibility = android.view.View.VISIBLE
    }

    // 履歴ありの表示
    private fun showListState() {
        emptyView.visibility = android.view.View.GONE
        rvHistory.visibility = android.view.View.VISIBLE
    }

    /**
     * 履歴データを使って「全体の累計貯金額」の折れ線グラフを描画する
     */
    private fun updateChartWithDeposits(deposits: List<Deposit>) {
        if (deposits.isEmpty()) {
            lineChartHistory.clear()
            return
        }

        // 日付昇順にソート（古い順）
        val sorted = deposits.sortedBy { it.timestamp.toDate().time }

        val entries = ArrayList<Entry>()
        var runningTotal = 0f

        // X軸用に時刻(long)を保持
        val xValues = mutableListOf<Long>()

        sorted.forEachIndexed { index, deposit ->
            runningTotal += deposit.amount.toFloat()
            val timeMillis = deposit.timestamp.toDate().time

            // Xは index を使う（実際の時刻はラベル側で表示）
            entries.add(Entry(index.toFloat(), runningTotal))
            xValues.add(timeMillis)
        }

        // DataSet を作成
        val dataSet = LineDataSet(entries, "累計貯金額").apply {
            color = android.graphics.Color.parseColor("#4CAF50")
            lineWidth = 2f
            setDrawCircles(true)
            circleRadius = 3f
            setCircleColor(android.graphics.Color.parseColor("#388E3C"))
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        val lineData = LineData(dataSet)
        lineChartHistory.data = lineData

        // X軸を「日付ラベル」にする
        val xAxis = lineChartHistory.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 1f
        xAxis.setDrawGridLines(false)

        val dateFormat = SimpleDateFormat("M/d", Locale.getDefault())

        xAxis.valueFormatter = com.github.mikephil.charting.formatter.IndexAxisValueFormatter(
            xValues.map { millis ->
                dateFormat.format(Date(millis))
            }
        )

        // Y軸（左だけ使う）
        val leftAxis = lineChartHistory.axisLeft
        leftAxis.setDrawGridLines(true)
        leftAxis.axisMinimum = 0f

        val rightAxis = lineChartHistory.axisRight
        rightAxis.isEnabled = false

        lineChartHistory.legend.isEnabled = false

        lineChartHistory.invalidate() // 再描画
    }
}
