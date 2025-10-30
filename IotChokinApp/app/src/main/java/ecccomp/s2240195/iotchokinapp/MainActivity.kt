package ecccomp.s2240195.iotchokinapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import android.widget.ProgressBar
import android.widget.ImageView
import android.widget.Toast
import com.google.firebase.firestore.FirebaseFirestore
import coil.load

class MainActivity : AppCompatActivity() {
    
    private lateinit var firestore: FirebaseFirestore
    private lateinit var goalTitle: TextView
    private lateinit var currentAmount: TextView
    private lateinit var targetAmount: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var productImage: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ビューの初期化
        goalTitle = findViewById(R.id.goalTitle)
        currentAmount = findViewById(R.id.currentAmount)
        targetAmount = findViewById(R.id.targetAmount)
        progressBar = findViewById(R.id.progressBar)
        productImage = findViewById(R.id.productImage)

        // Firestoreのインスタンス
        firestore = FirebaseFirestore.getInstance()

        // データ読み込み
        loadUserData()

        // 新しい目標ボタンの処理
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnNewGoal)?.setOnClickListener {
            val intent = Intent(this, NewGoalActivity::class.java)
            startActivity(intent)
        }
    }

        private fun loadUserData() {
            // ユーザーデータの読み込み処理
            firestore.collection("goals")
                .whereEqualTo("isSelected", true)
                .get()
                .addOnSuccessListener { documents ->
                    for (document in documents) {
                        val goalType = document.getString("goalType") ?: ""
                        val itemName = document.getString("itemName") ?: ""
                        val itemUrl = document.getString("itemUrl") ?: ""
                        val imageUrl = document.getString("imageUrl") ?: ""
                        val itemCode = document.getString("itemCode") ?: ""
                        val targetAmountValue = document.getLong("targetAmount")?.toInt() ?: 0
                        val currentAmountValue = document.getLong("currentAmount")?.toInt() ?: 0

                        // UIに反映
                        goalTitle.text = itemName
                        currentAmount.text = "¥${currentAmountValue}"
                        targetAmount.text = "¥${targetAmountValue}"

                        // 進捗率計算
                        val progress = if (targetAmountValue > 0) {
                            (currentAmountValue * 100) / targetAmountValue
                        } else {
                            0
                        }
                        progressBar.progress = progress

                        // 画像読み込み（Coil使用）
                        productImage.load(imageUrl)
                    }
                }
                .addOnFailureListener { e ->
                    // 読み込み失敗時の処理
                    Toast.makeText(this, "読み込み失敗: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}