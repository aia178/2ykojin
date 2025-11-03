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
import com.google.firebase.firestore.ListenerRegistration
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

    private var selectedGoalListener: ListenerRegistration? = null
    private var allGoalsListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        goalTitle = findViewById(R.id.goalTitle)
        currentAmount = findViewById(R.id.currentAmount)
        targetAmount = findViewById(R.id.targetAmount)
        remainingAmount = findViewById(R.id.remainingAmount)
        achievementBadge = findViewById(R.id.achievementBadge)
        progressBar = findViewById(R.id.progressBar)
        productImage = findViewById(R.id.productImage)
        allGoalsContainer = findViewById(R.id.allGoalsContainer)

        firestore = FirebaseFirestore.getInstance()

        loadUserData()

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnNewGoal)?.setOnClickListener {
            val intent = Intent(this, NewGoalActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        selectedGoalListener?.remove()
        allGoalsListener?.remove()
    }

    @SuppressLint("SetTextI18n")
    private fun loadUserData() {
        selectedGoalListener = firestore.collection("goals")
            .whereEqualTo("selected", true)
            .whereEqualTo("active", true)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Toast.makeText(this, "読み込み失敗: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshots == null || snapshots.isEmpty) {
                    showNoGoalState()
                    return@addSnapshotListener
                }

                val document = snapshots.documents[0]
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

        loadAllGoals()
    }

    @SuppressLint("SetTextI18n", "InflateParams")
    private fun loadAllGoals() {
        allGoalsListener = firestore.collection("goals")
            .whereEqualTo("active", true)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Toast.makeText(this, "目標リスト取得失敗: ${error.message}", Toast.LENGTH_SHORT).show()
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

                        val cardView = LayoutInflater.from(this).inflate(
                            R.layout.item_goal_card,
                            null
                        ) as com.google.android.material.card.MaterialCardView

                        if (isSelected) {
                            cardView.strokeColor = getColor(android.R.color.holo_orange_light)
                            cardView.strokeWidth = 8
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

                        cardView.setOnClickListener {
                            switchSelectedGoal(goalId)
                        }

                        allGoalsContainer.addView(cardView)
                    }
                }
            }
    }
    private fun switchSelectedGoal(newGoalId: String) {
        val batch = firestore.batch()

        firestore.collection("goals")
            .whereEqualTo("selected", true)
            .get()
            .addOnSuccessListener { snapshot ->
                snapshot.documents.forEach { doc ->
                    batch.update(doc.reference, "selected", false)
                }

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