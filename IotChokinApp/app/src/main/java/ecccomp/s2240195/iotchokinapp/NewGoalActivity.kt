package ecccomp.s2240195.iotchokinapp

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout

class NewGoalActivity : AppCompatActivity() {
    private lateinit var tabLayout: TabLayout
    private lateinit var rakutenSearchCard: MaterialCardView
    private lateinit var customGoalCard: MaterialCardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_goal)

        // ビューの初期化
        tabLayout = findViewById(R.id.tabLayout)
        rakutenSearchCard = findViewById(R.id.rakutenSearchCard)
        customGoalCard = findViewById(R.id.customGoalCard)

        // 戻るボタンの処理
        findViewById<android.widget.ImageButton>(R.id.btnBack)?.setOnClickListener {
            finish()
        }

        // タブの切り替え処理
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        // 楽天検索タブ
                        rakutenSearchCard.visibility = View.VISIBLE
                        customGoalCard.visibility = View.GONE
                    }
                    1 -> {
                        // カスタム目標タブ
                        rakutenSearchCard.visibility = View.GONE
                        customGoalCard.visibility = View.VISIBLE
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                // 必要に応じて処理
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
                // 必要に応じて処理
            }
        })

        // 検索ボタンの処理（後で実装）
        // 保存ボタンの処理（後で実装）
    }
}
