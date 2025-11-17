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

        findViewById<MaterialButton>(R.id.btnHistory) ?.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        selectedGoalListener?.remove()
        allGoalsListener?.remove()
    }

    @SuppressLint("SetTextI18n", "DefaultLocale")
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
                val goalId = document.id
                val itemName = document.getString("itemName") ?: ""
                val imageUrl = document.getString("imageUrl") ?: ""
                val targetAmountValue = document.getLong("targetAmount")?.toInt() ?: 0
                val currentAmountValue = document.getLong("currentAmount")?.toInt() ?: 0
                val achievedAt = document.getTimestamp("achievedAt")


                // 達成してるか確認
                if (currentAmountValue >= targetAmountValue && achievedAt == null) {
                    // Firestore更新
                    firestore.collection("goals")
                        .document(goalId)
                        .update("achievedAt", Timestamp.now())  // ← com.google.firebase. は不要
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

                        // 修正: allGoalsContainerを親として指定し、attachToRoot=falseを明示
                        val cardView = LayoutInflater.from(this).inflate(
                            R.layout.item_goal_card,
                            allGoalsContainer,  // 親を指定
                            false  // すぐにattachしない
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
            // 目標名編集ダイアログの実装
            // レイアウトをinflateして変数に保存
            val dialogView = layoutInflater.inflate(R.layout.dialog_edit_display_name, null)

            // inflate したView の中から EditText を探す
            val etDisplayName = dialogView.findViewById<TextInputEditText>(R.id.etDisplayName)

            // 現在の名前をセット
            etDisplayName.setText(currentName)
            val safePosition = currentName.length.coerceAtMost(40)
            etDisplayName.setSelection(safePosition)

            // ダイアログを作成
            val dialog = AlertDialog.Builder(this)
                .setView(dialogView) // inflate したView全体をセット
                .setCancelable(true) // 外タップで閉じる
                .create()

            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            // ⑤ボタンのクリックイベント
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
                    // Firestore更新
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

            // ダイアログ表示
            dialog.show()
        }


    private fun showEditAmountDialog(goalId: String, currentAmount: Int) {
        // 正しいレイアウトをinflate
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_amount, null)

        // EditTextを取得
        val etAmount = dialogView.findViewById<TextInputEditText>(R.id.etAmount)

        // 現在の金額をセット（IntをStringに変換）
        etAmount.setText(currentAmount.toString())
        etAmount.setSelection(currentAmount.toString().length) // カーソルを末尾に

        // ダイアログを作成
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        // キャンセルボタン
        dialogView.findViewById<MaterialButton>(R.id.btnCancelAmount).setOnClickListener {
            dialog.dismiss()
        }

        // 保存ボタン
        dialogView.findViewById<MaterialButton>(R.id.btnSaveAmount).setOnClickListener {
            val newAmountStr = etAmount.text.toString().trim()

            // 空チェック
            if (newAmountStr.isEmpty()) {
                Toast.makeText(this, "金額を入力してください", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 数値に変換できるかチェック
            val newAmount = newAmountStr.toIntOrNull()
            if (newAmount == null) {
                Toast.makeText(this, "正しい数値を入力してください", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 範囲チェック（1〜10,000,000円）
            if (newAmount < 1 || newAmount > 10_000_000) {
                Toast.makeText(this, "1〜10,000,000円の範囲で設定してください", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Firestore更新（Intで保存）
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

        // ⑦ダイアログ表示
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

        // 最初は削除ボタンを無効化
        btnConfirmDelete.isEnabled = false
        btnConfirmDelete.alpha = 0.5f

        // チェックボックスの状態で削除ボタンを有効/無効化
        cbAlsoDeleteHistory.setOnCheckedChangeListener { _, isChecked ->
            btnConfirmDelete.isEnabled = isChecked
            btnConfirmDelete.alpha = if (isChecked) 1.0f else 0.5f
        }

        dialogView.findViewById<MaterialButton>(R.id.btnCancelDelete).setOnClickListener {
            dialog.dismiss()
        }

        btnConfirmDelete.setOnClickListener {
            // 履歴を取得して一括削除
            firestore.collection("deposits")
                .whereEqualTo("goalId", goalId)
                .get()
                .addOnSuccessListener { snapshot ->
                    val batch = firestore.batch()

                    // 履歴を削除
                    snapshot.documents.forEach { doc ->
                        batch.delete(doc.reference)
                    }

                    // 目標を削除
                    batch.delete(firestore.collection("goals").document(goalId))

                    // 一括実行
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

        // 楽天商品の場合は「購入ページへ」ボタンを表示
        if (goalType == "rakuten" && itemUrl.isNotEmpty()) {
            btnGoToPurchase.visibility = View.VISIBLE
            btnGoToPurchase.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(itemUrl))
                startActivity(intent)
                dialog.dismiss()
            }
        }

        // 閉じるボタン（カスタム目標も楽天商品も共通）
        btnCloseDialog.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
// 目標ごとの currentAmount を deposits から再計算して更新
private fun updateCurrentAmount(goalId: String) {
    
    val deposit = firestore.collection("deposits")
        .whereEqualTo("goalId", goalId)
        .whereEqualTo("active", true)

    // TODO: get() でクエリを実行して結果(snapshot)を受け取る
    deposit.get().addOnSuccessListener { snapshot ->
        var totalAmount = 0
        for (document in snapshot.documents) {
            val amount = document.getLong("amount")?.toInt() ?: 0
            totalAmount += amount
        }
        firestore.collection("goals")
        .document(goalId)
        .update("currentAmount", totalAmount)
        .addOnSuccessListener {
            Log.d("MainActivity", "currentAmount updated: $totalAmount")
        }
        .addOnFailureListener { e ->
            Log.e("MainActivity", "currentAmount update failed: ${e.message}")
        } 
    }
    .addOnFailureListener { e ->
        Log.e("MainActivity", "Deposits retrieval failed: ${e.message}")
    }
    
}

// 選択中の目標（selected=true & active=true）を読み込んで画面に反映する
@SuppressLint("DefaultLocale")
private fun loadSelectedGoal() {
    val query = firestore.collection("goals")
        .whereEqualTo("selected", true)
        .whereEqualTo("active", true)
        .limit(1)

    query.addSnapshotListener { snapshots, error ->
        // --- エラー処理 ---
        if (error != null) {
            Log.e("MainActivity", "目標の取得に失敗: ${error.message}")
            showNoGoalState()
            return@addSnapshotListener
        }

        if (snapshots == null || snapshots.isEmpty) {
            showNoGoalState()
            return@addSnapshotListener
        }

        // --- ドキュメントを取得 ---
        val document = snapshots.documents[0]
        val goalId = document.id
        val wish = document.toObject(Wish::class.java)

        if (wish == null) {
            showNoGoalState()
            return@addSnapshotListener
        }

        goalTitle.text = wish.itemName
        targetAmount.text = "¥${String.format("%,d", wish.targetAmount)}"
        currentAmount.text = "¥${String.format("%,d", wish.currentAmount)}"

        // 画像読み込み
        if (wish.imageUrl.isNotEmpty()) {
            productImage.load(wish.imageUrl) {
                crossfade(true)
                placeholder(android.R.drawable.ic_menu_gallery)
                error(android.R.drawable.ic_menu_gallery)
            }
        } else {
            productImage.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        // --- currentAmount の再計算（非同期） ---
        updateCurrentAmount(goalId)
    }
}
}
