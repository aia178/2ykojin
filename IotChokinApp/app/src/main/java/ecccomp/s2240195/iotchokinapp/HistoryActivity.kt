package ecccomp.s2240195.iotchokinapp

import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.Timestamp

class HistoryActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var rvHistory: RecyclerView
    private lateinit var emptyView: LinearLayout
    private lateinit var btnBack: ImageButton
    private lateinit var adapter: HistoryAdapter
    private var depositList = mutableListOf<Deposit>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        initViews()
        setupRecyclerView()
        firestore = FirebaseFirestore.getInstance()
        loadHistoryData()

        // 戻るボタンで前の画面へ戻る
        btnBack.setOnClickListener {
            finish()
        }
    }

    // Viewの取得
    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        rvHistory = findViewById(R.id.rvHistory)
        emptyView = findViewById(R.id.emptyView)
    }

    // RecyclerViewの設定
    private fun setupRecyclerView() {
        adapter = HistoryAdapter(depositList)
        rvHistory.adapter = adapter
        rvHistory.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
    }

    // Firestoreから履歴取得
    private fun loadHistoryData() {
        firestore.collection("deposits")
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
}
