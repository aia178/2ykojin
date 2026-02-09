package ecccomp.s2240195.iotchokinapp

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import android.widget.ProgressBar
import android.widget.ImageView
import android.widget.Toast
import android.view.LayoutInflater
import android.view.View
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import android.widget.ImageButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import coil.load
import com.google.firebase.Timestamp
import android.widget.LinearLayout
import java.io.File

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
    private var statisticsListener: ListenerRegistration? = null
    private var currentGoalId: String? = null

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

        findViewById<MaterialButton>(R.id.btnNewGoal)?.setOnClickListener {
            val intent = Intent(this, NewGoalActivity::class.java)
            startActivity(intent)
        }

        findViewById<MaterialButton>(R.id.btnHistory)?.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }

        findViewById<MaterialButton>(R.id.btnGraph)?.setOnClickListener {
            val intent = Intent(this, GraphActivity::class.java)
            startActivity(intent)
        }
    }

    private var currentGoalDepositsListener: ListenerRegistration? = null

    override fun onDestroy() {
        super.onDestroy()
        selectedGoalListener?.remove()
        allGoalsListener?.remove()
        currentGoalDepositsListener?.remove()
        statisticsListener?.remove()
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
                val newGoalId = document.id
                
                // 目標切替時はリスナーを張り直す
                if (currentGoalId != newGoalId) {
                    currentGoalId = newGoalId
                    setupCurrentGoalDepositsListener(newGoalId)
                }

                val itemName = document.getString("itemName") ?: ""
                val imageUrl = document.getString("imageUrl") ?: ""
                val targetAmountValue = document.getLong("targetAmount")?.toInt() ?: 0
                val currentAmountValue = document.getLong("currentAmount")?.toInt() ?: 0
                val achievedAt = document.getTimestamp("achievedAt")

                if (currentAmountValue >= targetAmountValue && achievedAt == null) {
                    firestore.collection("goals")
                        .document(currentGoalId!!)
                        .update("achievedAt", Timestamp.now())
                        .addOnSuccessListener {
                            val itemUrl = document.getString("itemUrl") ?: ""
                            val goalType = document.getString("goalType") ?: "custom"
                            showAchievementDialog(itemName, imageUrl, itemUrl, goalType)
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "更新失敗: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }

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
                    // ローカルファイルかURLかを判定
                    val imageSource = if (imageUrl.startsWith("/")) {
                        File(imageUrl)  // ローカルファイル
                    } else {
                        imageUrl  // URL
                    }
                    productImage.load(imageSource) {
                        crossfade(true)
                        placeholder(android.R.drawable.ic_menu_gallery)
                        error(android.R.drawable.ic_menu_gallery)
                    }
                } else {
                    productImage.setImageResource(android.R.drawable.ic_menu_gallery)
                }
            }

        loadAllGoals()
        loadStatistics()
    }

    // 選択中目標の合計貯金額を反映する
    private fun setupCurrentGoalDepositsListener(goalId: String) {
        currentGoalDepositsListener?.remove()
        
        currentGoalDepositsListener = firestore.collection("deposits")
            .whereEqualTo("goalId", goalId)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("MainActivity", "Deposits listener failed: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    var totalAmount = 0
                    for (doc in snapshots.documents) {
                        val amount = doc.getLong("amount")?.toInt() ?: 0
                        totalAmount += amount
                    }

                    firestore.collection("goals")
                        .document(goalId)
                        .update("currentAmount", totalAmount)
                        .addOnFailureListener { e ->
                            Log.e("MainActivity", "Failed to update goal amount: ${e.message}")
                        }
                }
            }
    }

    @SuppressLint("SetTextI18n")
    private fun loadStatistics() {
        val tvMonthlySavings = findViewById<TextView>(R.id.tvMonthlySavings)
        val tvTotalSavings = findViewById<TextView>(R.id.tvTotalSavings)

        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val startOfMonth = Timestamp(calendar.time)

        // リアルタイムで統計を更新
        statisticsListener = firestore.collection("deposits")
            .addSnapshotListener { documents, error ->
                if (error != null) {
                    Log.e("MainActivity", "Statistics listener failed: ${error.message}")
                    return@addSnapshotListener
                }

                var total = 0
                var monthly = 0

                documents?.let {
                    for (doc in it) {
                        val amount = doc.getLong("amount")?.toInt() ?: 0
                        val timestamp = doc.getTimestamp("timestamp")

                        total += amount

                        if (timestamp != null && timestamp.seconds >= startOfMonth.seconds) {
                            monthly += amount
                        }
                    }
                }

                tvMonthlySavings.text = "¥${String.format("%,d", monthly)}"
                tvTotalSavings.text = "¥${String.format("%,d", total)}"
            }
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
                            allGoalsContainer,
                            false
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
                            val imageSource = if (imageUrl.startsWith("/")) {
                                File(imageUrl)
                            } else {
                                imageUrl
                            }
                            goalImage.load(imageSource) {
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

                        val btnMenu = cardView.findViewById<ImageButton>(R.id.btnGoalMenu)

                        btnMenu.setOnClickListener { view ->
                            showGoalMenu(view, goalId, itemName, targetAmountValue, imageUrl)
                        }

                        allGoalsContainer.addView(cardView)
                    }
                }
            }
    }

    private fun switchSelectedGoal(newGoalId: String) {
        val goals = firestore.collection("goals")
        goals.whereEqualTo("selected", true)
            .get()
            .addOnSuccessListener { snapshot ->
                val batch = firestore.batch()
                snapshot.documents.forEach { doc ->
                    if (doc.id != newGoalId) {
                        batch.update(doc.reference, "selected", false)
                    }
                }
                batch.update(goals.document(newGoalId), "selected", true)
                batch.commit()
                    .addOnSuccessListener {
                        Toast.makeText(this, "目標を切り替えました", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "切り替え失敗: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "切り替え失敗: ${e.message}", Toast.LENGTH_SHORT).show()
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

    private fun showGoalMenu(
        view: View,
        goalId: String,
        goalName: String,
        targetAmount: Int,
        imageUrl: String
    ) {
        val popup = android.widget.PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.menu_goal_card_overflow, popup.menu)

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_edit_display_name -> {
                    showEditNameDialog(goalId, goalName)
                    true
                }
                R.id.action_edit_target_amount -> {
                    showEditAmountDialog(goalId, targetAmount)
                    true
                }
                R.id.action_delete_goal -> {
                    deleteGoal(goalId, imageUrl)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showEditNameDialog(goalId: String, currentName: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_display_name, null)
        val etDisplayName = dialogView.findViewById<TextInputEditText>(R.id.etDisplayName)

        etDisplayName.setText(currentName)
        val safePosition = currentName.length.coerceAtMost(40)
        etDisplayName.setSelection(safePosition)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogView.findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<MaterialButton>(R.id.btnSave).setOnClickListener {
            val newName = etDisplayName.text.toString().trim()

            if (newName.isEmpty()) {
                Toast.makeText(this, "目標名は空にできません", Toast.LENGTH_SHORT).show()
            } else if (newName.length < 2) {
                Toast.makeText(this, "2文字以上で入力してください", Toast.LENGTH_SHORT).show()
            } else {
                firestore.collection("goals")
                    .document(goalId)
                    .update("itemName", newName)
                    .addOnSuccessListener {
                        Toast.makeText(this, "目標名を更新しました", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "更新失敗: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        dialog.show()
    }

    private fun showEditAmountDialog(goalId: String, currentAmount: Int) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_amount, null)
        val etAmount = dialogView.findViewById<TextInputEditText>(R.id.etAmount)

        etAmount.setText(currentAmount.toString())
        etAmount.setSelection(currentAmount.toString().length)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        dialogView.findViewById<MaterialButton>(R.id.btnCancelAmount).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<MaterialButton>(R.id.btnSaveAmount).setOnClickListener {
            val newAmountStr = etAmount.text.toString().trim()

            if (newAmountStr.isEmpty()) {
                Toast.makeText(this, "金額を入力してください", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newAmount = newAmountStr.toIntOrNull()
            if (newAmount == null) {
                Toast.makeText(this, "正しい数値を入力してください", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newAmount < 1 || newAmount > 10_000_000) {
                Toast.makeText(this, "1〜10,000,000円の範囲で設定してください", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            firestore.collection("goals")
                .document(goalId)
                .update("targetAmount", newAmount)
                .addOnSuccessListener {
                    Toast.makeText(this, "目標金額を更新しました", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "更新失敗: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        dialog.show()
    }

    private fun deleteGoal(goalId: String, imageUrl: String) {
        firestore.collection("deposits")
            .whereEqualTo("goalId", goalId)
            .get()
            .addOnSuccessListener { snapshot ->
                val refsToDelete = snapshot.documents.map { it.reference }.toMutableList()
                refsToDelete.add(firestore.collection("goals").document(goalId))

                commitDeleteBatches(refsToDelete, 0) {
                    deleteLocalImageIfNeeded(imageUrl)
                    Toast.makeText(this, "目標を削除しました", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "削除失敗: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun commitDeleteBatches(
        refs: List<com.google.firebase.firestore.DocumentReference>,
        startIndex: Int,
        onComplete: () -> Unit
    ) {
        if (startIndex >= refs.size) {
            onComplete()
            return
        }

        val endIndex = minOf(startIndex + 450, refs.size)
        val batch = firestore.batch()
        for (i in startIndex until endIndex) {
            batch.delete(refs[i])
        }

        batch.commit()
            .addOnSuccessListener {
                commitDeleteBatches(refs, endIndex, onComplete)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "削除失敗: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteLocalImageIfNeeded(imageUrl: String) {
        if (!imageUrl.startsWith("/")) return
        try {
            val file = File(imageUrl)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to delete image: ${e.message}")
        }
    }

    private fun showAchievementDialog(goalName: String, imageUrl: String, itemUrl: String, goalType: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_achievement, null)

        val ivAchievedProduct = dialogView.findViewById<ImageView>(R.id.ivAchievedProduct)
        val tvAchievedGoalName = dialogView.findViewById<TextView>(R.id.tvAchievedGoalName)
        val btnGoToPurchase = dialogView.findViewById<MaterialButton>(R.id.btnGoToPurchase)
        val btnCloseDialog = dialogView.findViewById<MaterialButton>(R.id.btnCloseDialog)

        // 達成した商品/目標の画像を表示
        if (imageUrl.isNotEmpty()) {
            val imageSource = if (imageUrl.startsWith("/")) {
                File(imageUrl)  // ローカルファイル
            } else {
                imageUrl  // URL
            }
            ivAchievedProduct.load(imageSource) {
                crossfade(true)
                placeholder(android.R.drawable.ic_menu_gallery)
                error(android.R.drawable.ic_menu_gallery)
            }
        } else {
            ivAchievedProduct.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        tvAchievedGoalName.text = goalName

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        if (goalType == "rakuten" && itemUrl.isNotEmpty()) {
            btnGoToPurchase.visibility = View.VISIBLE
            btnGoToPurchase.setOnClickListener {
                val uri = android.net.Uri.parse(itemUrl.trim())
                val scheme = uri.scheme?.lowercase()
                if (scheme != "https" && scheme != "http") {
                    Toast.makeText(this, "購入URLが不正です", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val intent = Intent(Intent.ACTION_VIEW, uri)
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                    dialog.dismiss()
                } else {
                    Toast.makeText(this, "URLを開けるアプリがありません", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnCloseDialog.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}
