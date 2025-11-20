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
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GraphActivity : AppCompatActivity() {

    private lateinit var chart: LineChart
    private lateinit var firestore: FirebaseFirestore
    private var currentGoalId: String? = null
    private var targetAmount: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_graph)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarGraph)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        chart = findViewById(R.id.chartSavings)
        firestore = FirebaseFirestore.getInstance()

        setupChart()
        loadSelectedGoal()
    }

    private fun setupChart() {
        chart.description.isEnabled = false
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(true)
        chart.setPinchZoom(true)
        chart.setDrawGridBackground(false)

        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.valueFormatter = object : ValueFormatter() {
            private val sdf = SimpleDateFormat("MM/dd", Locale.JAPAN)
            override fun getFormattedValue(value: Float): String {
                return sdf.format(Date(value.toLong()))
            }
        }

        val leftAxis = chart.axisLeft
        leftAxis.axisMinimum = 0f
        
        chart.axisRight.isEnabled = false
    }

    private fun loadSelectedGoal() {
        firestore.collection("goals")
            .whereEqualTo("selected", true)
            .whereEqualTo("active", true)
            .limit(1)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val doc = documents.documents[0]
                    currentGoalId = doc.id
                    targetAmount = doc.getLong("targetAmount")?.toInt() ?: 0
                    val goalName = doc.getString("itemName") ?: "目標"
                    
                    supportActionBar?.title = "$goalName の推移"

                    addGoalLine(targetAmount.toFloat())
                    loadDeposits(doc.id)
                } else {
                    Toast.makeText(this, "選択中の目標がありません", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e("GraphActivity", "Error loading goal", e)
                Toast.makeText(this, "目標の読み込みに失敗しました", Toast.LENGTH_SHORT).show()
            }
    }

    private fun addGoalLine(amount: Float) {
        val leftAxis = chart.axisLeft
        leftAxis.removeAllLimitLines()
        
        val limitLine = LimitLine(amount, "目標: ¥${amount.toInt()}")
        limitLine.lineWidth = 2f
        limitLine.lineColor = Color.RED
        limitLine.enableDashedLine(10f, 10f, 0f)
        limitLine.labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
        limitLine.textSize = 12f
        
        leftAxis.addLimitLine(limitLine)
    }

    private fun loadDeposits(goalId: String) {
        firestore.collection("deposits")
            .whereEqualTo("goalId", goalId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { documents ->
                val entries = ArrayList<Entry>()
                var cumulativeAmount = 0f

                // Add initial point (0, 0) if needed, or just start from first deposit
                // Let's start from the first deposit's time but with 0 amount? 
                // Or just plot the points.
                
                // To make it look like a cumulative graph starting from 0
                // We might want to find the goal creation date, but for now let's just plot deposits.

                for (doc in documents) {
                    val amount = doc.getLong("amount")?.toFloat() ?: 0f
                    val timestamp = doc.getTimestamp("timestamp") ?: Timestamp.now()
                    
                    cumulativeAmount += amount
                    entries.add(Entry(timestamp.toDate().time.toFloat(), cumulativeAmount))
                }

                if (entries.isNotEmpty()) {
                    // Add a start point (0 amount) slightly before the first deposit if possible, 
                    // but for simplicity just plotting data points.
                    
                    val dataSet = LineDataSet(entries, "貯金額")
                    dataSet.color = Color.BLUE
                    dataSet.valueTextColor = Color.BLACK
                    dataSet.lineWidth = 2f
                    dataSet.setDrawCircles(true)
                    dataSet.setDrawValues(false)
                    dataSet.mode = LineDataSet.Mode.STEPPED // Stepped looks good for savings
                    
                    // Fill
                    dataSet.setDrawFilled(true)
                    dataSet.fillColor = Color.CYAN
                    dataSet.fillAlpha = 50

                    val lineData = LineData(dataSet)
                    chart.data = lineData
                    chart.invalidate()
                    chart.animateX(1000)
                } else {
                    chart.clear()
                }
            }
            .addOnFailureListener { e ->
                Log.e("GraphActivity", "Error loading deposits", e)
                Toast.makeText(this, "履歴の読み込みに失敗しました", Toast.LENGTH_SHORT).show()
            }
    }
}
