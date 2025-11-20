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

        findViewById<android.widget.Button>(R.id.btnDebugDeposit)?.setOnClickListener {
            addDebugDeposit()
        }
    }

    private var currentGoalDepositsListener: ListenerRegistration? = null

    override fun onDestroy() {
        super.onDestroy()
        selectedGoalListener?.remove()
        allGoalsListener?.remove()
        currentGoalDepositsListener?.remove()
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
                
                // 目標が変わった場合、または初めての場合、預金リスナーを再設定
                if (currentGoalId != newGoalId) {
                    currentGoalId = newGoalId
                    setupCurrentGoalDepositsListener(newGoalId)
                }

                val itemName = document.getString("itemName") ?: ""
                val imageUrl = document.getString("imageUrl") ?: ""
                val targetAmountValue = document.getLong("targetAmount")?.toInt() ?: 0
                val currentAmountValue = document.getLong("currentAmount")?.toInt() ?: 0
                val achievedAt = document.getTimestamp("achievedAt")

                // 達成してるか確認
                if (currentAmountValue >= targetAmountValue && achievedAt == null) {
                    // Firestore更新
                    firestore.collection("goals")
                        .document(currentGoalId!!)
                        .update("achievedAt", Timestamp.now())
                        .addOnSuccessListener {
                            // 達成ダイアログを表示
                            val itemUrl = document.getString("itemUrl") ?: ""
                            val goalType = document.getString("goalType") ?: "custom"
                            showAchievementDialog(itemName, itemUrl, goalType)
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
        loadStatistics()
    }

    // 選択中の目標に対する貯金履歴を監視し、合計額を計算して目標ドキュメントを更新する
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

                    // Firestoreのgoalsコレクションを更新
                    // 注意: これにより selectedGoalListener が発火し、UIが更新される
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

        // Calculate start of this month
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val startOfMonth = Timestamp(calendar.time)

        // get() ではなく addSnapshotListener を使用してリアルタイム更新
        firestore.collection("deposits")
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

                // Update UI
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

                        val btnMenu = cardView.findViewById<ImageButton>(R.id.btnGoalMenu)

                        btnMenu.setOnClickListener { view ->
                            showGoalMenu(view, goalId, itemName, targetAmountValue)
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

    private fun showGoalMenu(
        view: View,
        goalId: String,
        goalName: String,
        targetAmount: Int
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
                    showDeleteConfirmDialog(goalId)
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

    private fun showDeleteConfirmDialog(goalId: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_confirm_delete_goal, null)

        val cbAlsoDeleteHistory = dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(
            R.id.cbAlsoDeleteHistory
        )
        val btnConfirmDelete = dialogView.findViewById<MaterialButton>(R.id.btnConfirmDelete)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        btnConfirmDelete.isEnabled = false
        btnConfirmDelete.alpha = 0.5f

        cbAlsoDeleteHistory.setOnCheckedChangeListener { _, isChecked ->
            btnConfirmDelete.isEnabled = isChecked
            btnConfirmDelete.alpha = if (isChecked) 1.0f else 0.5f
        }

        dialogView.findViewById<MaterialButton>(R.id.btnCancelDelete).setOnClickListener {
            dialog.dismiss()
        }

        btnConfirmDelete.setOnClickListener {
            firestore.collection("deposits")
                .whereEqualTo("goalId", goalId)
                .get()
                .addOnSuccessListener { snapshot ->
                    val batch = firestore.batch()

                    snapshot.documents.forEach { doc ->
                        batch.delete(doc.reference)
                    }

                    batch.delete(firestore.collection("goals").document(goalId))

                    batch.commit()
                        .addOnSuccessListener {
                            Toast.makeText(this, "目標と履歴を削除しました", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "削除失敗: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "削除失敗: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        dialog.show()
    }

    private fun showAchievementDialog(goalName: String, itemUrl: String, goalType: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_achievement, null)

        val tvAchievedGoalName = dialogView.findViewById<TextView>(R.id.tvAchievedGoalName)
        val btnGoToPurchase = dialogView.findViewById<MaterialButton>(R.id.btnGoToPurchase)
        val btnCloseDialog = dialogView.findViewById<MaterialButton>(R.id.btnCloseDialog)

        tvAchievedGoalName.text = goalName

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        if (goalType == "rakuten" && itemUrl.isNotEmpty()) {
            btnGoToPurchase.visibility = View.VISIBLE
            btnGoToPurchase.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(itemUrl))
                startActivity(intent)
                dialog.dismiss()
            }
        }

        btnCloseDialog.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun addDebugDeposit() {
        if (currentGoalId == null) {
            Toast.makeText(this, "目標が選択されていません", Toast.LENGTH_SHORT).show()
            return
        }

        val deposit = hashMapOf(
            "amount" to 100,
            "goalId" to currentGoalId,
            "goalName" to goalTitle.text.toString(),
            "timestamp" to Timestamp.now()
        )

        firestore.collection("deposits")
            .add(deposit)
            .addOnSuccessListener {
                Toast.makeText(this, "デバッグ: 100円投入しました", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "デバッグエラー: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
