package ecccomp.s2240195.iotchokinapp

import com.google.firebase.Timestamp

data class Deposit(
    val amount: Int = 0,
    val timestamp: Timestamp = Timestamp.now(),
    val goalId: String = "",
    val goalName: String = ""
)