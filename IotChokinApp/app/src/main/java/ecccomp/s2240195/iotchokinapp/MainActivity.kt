package ecccomp.s2240195.iotchokinapp

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import android.widget.ProgressBar
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import android.view.LayoutInflater
import com.google.firebase.firestore.FirebaseFirestore
import coil.load

class MainActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var goalTitle: TextView
    private lateinit var currentAmount: TextView
    private lateinit var targetAmount: TextView
    private lateinit var remainingAmount: TextView
    private lateinit var achievementBadge: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var productImage: ImageView
    private lateinit var allGoalsContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ビューの初期化
        goalTitle = findViewById(R.id.goalTitle)
        currentAmount = findViewById(R.id.currentAmount)
        targetAmount = findViewById(R.id.targetAmount)
        remainingAmount = findViewById(R.id.remainingAmount)
        achievementBadge = findViewById(R.id.achievementBadge)
        progressBar = findViewById(R.id.progressBar)
        productImage = findViewById(R.id.productImage)
        allGoalsContainer = findViewById(R.id.allGoalsContainer)

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

    @SuppressLint("SetTextI18n")
    private fun loadUserData() {
        // 選択中の目標を表示
        firestore.collection("goals")
            .whereEqualTo("selected", true)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Toast.makeText(this, "読み込み失敗: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                snapshots?.let { documents ->
                    if (documents.isEmpty) {
                        showNoGoalState()
                        return@addSnapshotListener
                    }

                    for (document in documents) {
                        val itemName = document.getString("itemName") ?: ""
                        val imageUrl = document.getString("imageUrl") ?: ""
                        val targetAmountValue = document.getLong("targetAmount")?.toInt() ?: 0
                        val currentAmountValue = document.getLong("currentAmount")?.toInt() ?: 0

                        val remaining = targetAmountValue - currentAmountValue
                        val remainingText = if (remaining > 0) {
                            "¥${String.format("%,d", remaining)}"
                        } else {
                            "達成！"
                        }

                        val progress = if (targetAmountValue > 0) {
                            ((currentAmountValue.toFloat() / targetAmountValue.toFloat()) * 100).toInt()
                        } else {
                            0
                        }

                        goalTitle.text = itemName
                        currentAmount.text = "¥${String.format("%,d", currentAmountValue)}"
                        targetAmount.text = "¥${String.format("%,d", targetAmountValue)}"
                        remainingAmount.text = remainingText
                        achievementBadge.text = "${progress}%"
                        progressBar.progress = progress.coerceIn(0, 100)

                        if (imageUrl.isNotEmpty()) {
                            productImage.load(imageUrl) {
                                crossfade(true)
                                placeholder(android.R.drawable.ic_menu_gallery)
                                error(android.R.drawable.ic_menu_gallery)
                            }
                        } else {
                            productImage.setImageResource(android.R.drawable.ic_menu_gallery)
                        }
                    }
                }
            }

        // すべての目標を表示
        loadAllGoals()
    }

    @SuppressLint("SetTextI18n", "InflateParams")
    private fun loadAllGoals() {
        firestore.collection("goals")
            .whereEqualTo("isActive", true)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    return@addSnapshotListener
                }

                allGoalsContainer.removeAllViews()

                snapshots?.let { documents ->
                    for (document in documents) {
                        val goalId = document.id
                        val itemName = document.getString("itemName") ?: ""
                        val imageUrl = document.getString("imageUrl") ?: ""
                        val targetAmountValue = document.getLong("targetAmount")?.toInt() ?: 0
                        val currentAmountValue = document.getLong("currentAmount")?.toInt() ?: 0
                        val isSelected = document.getBoolean("selected") ?: false

                        val progress = if (targetAmountValue > 0) {
                            ((currentAmountValue.toFloat() / targetAmountValue.toFloat()) * 100).toInt()
                        } else {
                            0
                        }

                        // カードビューを生成
                        val cardView = LayoutInflater.from(this).inflate(
                            R.layout.item_goal_card,
                            null
                        ) as com.google.android.material.card.MaterialCardView

                        // 選択中の目標は枠を金色に
                        if (isSelected) {
                            cardView.strokeColor = getColor(android.R.color.holo_orange_light)
                            cardView.strokeWidth = 6
                        } else {
                            cardView.strokeColor = getColor(android.R.color.darker_gray)
                            cardView.strokeWidth = 2
                        }

                        val goalImage = cardView.findViewById<ImageView>(R.id.goalCardImage)
                        val goalName = cardView.findViewById<TextView>(R.id.goalCardName)
                        val goalProgress = cardView.findViewById<TextView>(R.id.goalCardProgress)

                        if (imageUrl.isNotEmpty()) {
                            goalImage.load(imageUrl) {
                                crossfade(true)
                                placeholder(android.R.drawable.ic_menu_gallery)
                            }
                        } else {
                            goalImage.setImageResource(android.R.drawable.ic_menu_gallery)
                        }

                        goalName.text = itemName
                        goalProgress.text = "${progress}%"

                        // カードをタップしたら選択中の目標に切り替え
                        cardView.setOnClickListener {
                            switchSelectedGoal(goalId)
                        }

                        allGoalsContainer.addView(cardView)
                    }

                    // 新規追加ボタン
                    val addButton = LayoutInflater.from(this).inflate(
                        R.layout.item_add_goal_card,
                        null
                    )
                    addButton.setOnClickListener {
                        val intent = Intent(this, NewGoalActivity::class.java)
                        startActivity(intent)
                    }
                    allGoalsContainer.addView(addButton)
                }
            }
    }

    private fun switchSelectedGoal(newGoalId: String) {
        val batch = firestore.batch()

        // すべての selected を false に
        firestore.collection("goals")
            .whereEqualTo("selected", true)
            .get()
            .addOnSuccessListener { snapshot ->
                snapshot.documents.forEach { doc ->
                    batch.update(doc.reference, "selected", false)
                }

                // 新しい目標を selected = true に
                val newGoalRef = firestore.collection("goals").document(newGoalId)
                batch.update(newGoalRef, "selected", true)

                batch.commit()
                    .addOnSuccessListener {
                        Toast.makeText(this, "目標を切り替えました", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "切り替え失敗: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
    }

    @SuppressLint("SetTextI18n")
    private fun showNoGoalState() {
        goalTitle.text = "目標を設定してください"
        currentAmount.text = "¥0"
        targetAmount.text = "¥0"
        remainingAmount.text = "¥0"
        achievementBadge.text = "0%"
        progressBar.progress = 0
        productImage.setImageResource(android.R.drawable.ic_menu_gallery)
    }
}