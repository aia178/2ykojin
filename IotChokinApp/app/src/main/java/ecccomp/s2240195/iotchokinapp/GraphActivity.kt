package ecccomp.s2240195.iotchokinapp

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import coil.load
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GraphActivity : AppCompatActivity() {

    private lateinit var chart: LineChart
    private lateinit var firestore: FirebaseFirestore
    private var currentGoalId: String? = null
    private var targetAmount: Int = 0

    private lateinit var imgGoal: android.widget.ImageView
    private lateinit var tvGoalTitle: android.widget.TextView
    private lateinit var tvGoalDate: android.widget.TextView
    private lateinit var tvCurrentAmount: android.widget.TextView
    private lateinit var tvTargetAmount: android.widget.TextView
    private lateinit var tvRemainingAmount: android.widget.TextView

    private var goalListener: ListenerRegistration? = null
    private var depositsListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_graph)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarGraph)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        chart = findViewById(R.id.chartSavings)
        
        // Initialize Views
        imgGoal = findViewById(R.id.imgGoal)
        tvGoalTitle = findViewById(R.id.tvGoalTitle)
        tvGoalDate = findViewById(R.id.tvGoalDate)
        tvCurrentAmount = findViewById(R.id.tvCurrentAmount)
        tvTargetAmount = findViewById(R.id.tvTargetAmount)
        tvRemainingAmount = findViewById(R.id.tvRemainingAmount)

        firestore = FirebaseFirestore.getInstance()

        setupChart()
        loadSelectedGoal()
    }

    override fun onDestroy() {
        super.onDestroy()
        goalListener?.remove()
        depositsListener?.remove()
    }

    private fun setupChart() {
        chart.description.isEnabled = false
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(true)
        chart.setPinchZoom(true)
        chart.setDrawGridBackground(false)
        
        // Remove right axis
        chart.axisRight.isEnabled = false
        
        // X Axis styling
        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.textColor = Color.parseColor("#757575")
        xAxis.valueFormatter = object : ValueFormatter() {
            private val sdf = SimpleDateFormat("MM/dd", Locale.JAPAN)
            override fun getFormattedValue(value: Float): String {
                return sdf.format(Date(value.toLong()))
            }
        }

        // Left Axis styling
        val leftAxis = chart.axisLeft
        leftAxis.axisMinimum = 0f
        leftAxis.textColor = Color.parseColor("#757575")
        leftAxis.gridColor = Color.parseColor("#EEEEEE")
        
        // Animation
        chart.animateX(1000)
    }

    private fun loadSelectedGoal() {
        goalListener = firestore.collection("goals")
            .whereEqualTo("selected", true)
            .whereEqualTo("active", true)
            .limit(1)
            .addSnapshotListener { documents, error ->
                if (error != null) {
                    Log.e("GraphActivity", "Error loading goal", error)
                    Toast.makeText(this, "目標の読み込みに失敗しました", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (documents != null && !documents.isEmpty) {
                    val doc = documents.documents[0]
                    val newGoalId = doc.id
                    
                    // 目標が変わったら履歴リスナーを再設定
                    if (currentGoalId != newGoalId) {
                        currentGoalId = newGoalId
                        setupDepositsListener(newGoalId)
                    }

                    targetAmount = doc.getLong("targetAmount")?.toInt() ?: 0
                    val currentAmount = doc.getLong("currentAmount")?.toInt() ?: 0
                    val goalName = doc.getString("itemName") ?: "目標"
                    val imageUrl = doc.getString("imageUrl") ?: ""
                    val createdAt = doc.getTimestamp("createdAt")?.toDate()

                    // Update UI
                    supportActionBar?.title = "貯金推移"
                    tvGoalTitle.text = goalName
                    tvCurrentAmount.text = "¥${String.format("%,d", currentAmount)}"
                    tvTargetAmount.text = "¥${String.format("%,d", targetAmount)}"
                    
                    val remaining = targetAmount - currentAmount
                    tvRemainingAmount.text = "¥${String.format("%,d", if (remaining > 0) remaining else 0)}"

                    if (createdAt != null) {
                        val sdf = SimpleDateFormat("yyyy/MM/dd 作成", Locale.JAPAN)
                        tvGoalDate.text = sdf.format(createdAt)
                    }

                    // Load Image with Coil
                    if (imageUrl.isNotEmpty()) {
                        val imageSource = if (imageUrl.startsWith("/")) {
                            File(imageUrl)
                        } else {
                            imageUrl
                        }
                        imgGoal.load(imageSource) {
                            crossfade(true)
                            placeholder(android.R.drawable.ic_menu_gallery)
                            error(android.R.drawable.ic_menu_gallery)
                        }
                    } else {
                        imgGoal.setImageResource(android.R.drawable.ic_menu_gallery)
                    }

                    addGoalLine(targetAmount.toFloat())
                } else {
                    Toast.makeText(this, "選択中の目標がありません", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun addGoalLine(amount: Float) {
        val leftAxis = chart.axisLeft
        leftAxis.removeAllLimitLines()
        
        val limitLine = LimitLine(amount, "目標")
        limitLine.lineWidth = 1f
        limitLine.lineColor = Color.parseColor("#FFC107") // Primary Gold
        limitLine.enableDashedLine(10f, 10f, 0f)
        limitLine.labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
        limitLine.textSize = 10f
        limitLine.textColor = Color.parseColor("#FFC107")
        
        leftAxis.addLimitLine(limitLine)
    }

    private fun setupDepositsListener(goalId: String) {
        depositsListener?.remove()

        depositsListener = firestore.collection("deposits")
            .whereEqualTo("goalId", goalId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { documents, error ->
                if (error != null) {
                    Log.e("GraphActivity", "Error loading deposits", error)
                    return@addSnapshotListener
                }

                if (documents != null) {
                    val entries = ArrayList<Entry>()
                    var cumulativeAmount = 0f

                    for (doc in documents) {
                        val amount = doc.getLong("amount")?.toFloat() ?: 0f
                        val timestamp = doc.getTimestamp("timestamp") ?: Timestamp.now()
                        
                        cumulativeAmount += amount
                        entries.add(Entry(timestamp.toDate().time.toFloat(), cumulativeAmount))
                    }

                    if (entries.isNotEmpty()) {
                        val primaryColor = Color.parseColor("#FFC107")

                        val dataSet = LineDataSet(entries, "貯金額")
                        dataSet.color = primaryColor
                        dataSet.setCircleColor(primaryColor)
                        dataSet.valueTextColor = Color.BLACK
                        dataSet.lineWidth = 3f
                        dataSet.circleRadius = 4f
                        dataSet.setDrawCircleHole(true)
                        dataSet.circleHoleColor = Color.WHITE
                        
                        dataSet.setDrawValues(false)
                        
                        // Bezier Curve for smooth line
                        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
                        
                        // Fill
                        dataSet.setDrawFilled(true)
                        dataSet.fillColor = primaryColor
                        dataSet.fillAlpha = 30

                        val lineData = LineData(dataSet)
                        chart.data = lineData
                        
                        // データの更新を通知
                        chart.notifyDataSetChanged()
                        chart.invalidate()
                    } else {
                        chart.clear()
                    }
                }
            }
    }
}
