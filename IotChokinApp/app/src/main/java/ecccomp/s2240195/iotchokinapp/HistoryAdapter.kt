package ecccomp.s2240195.iotchokinapp

import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Locale
import com.google.firebase.Timestamp

class HistoryAdapter(private val deposits: List<Deposit>) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    // 1アイテム分のViewを保持
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvHistoryDate: TextView = view.findViewById(R.id.tvHistoryDate)
        val tvHistoryAmount: TextView = view.findViewById(R.id.tvHistoryAmount)
        val tvHistoryGoalName: TextView = view.findViewById(R.id.tvHistoryGoalName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val deposit = deposits[position]

        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN)
        holder.tvHistoryDate.text = sdf.format(deposit.timestamp.toDate())

        holder.tvHistoryAmount.text = "+¥${String.format("%,d", deposit.amount)}"
        holder.tvHistoryGoalName.text = deposit.goalName
    }

    override fun getItemCount(): Int {
        return deposits.size
    }
}
