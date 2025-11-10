package ecccomp.s2240195.iotchokinapp

import com.google.firebase.Timestamp

class Deposit {
    var amount: Int  = 0
    var timestamp : Timestamp = Timestamp.now()
    var goalId : String = ""
    var goalName : String = ""
}