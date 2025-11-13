#include "firestore_client.h"
#include <WiFiClientSecure.h>
#include <HTTPClient.h>

/**
 * Firestoreのdepositsコレクションにデータを送信
 * 
 * @param amount 貯金額（円）
 * @param goalId 目標ID
 * @param goalName 目標名
 * @return 成功したらtrue、失敗したらfalse
 */
bool sendDeposit(int amount, const String& goalId, const String& goalName) {
    // TODO: HTTPClientの初期化
    
    // TODO: FirestoreのURLを組み立てる
    // 例: https://firestore.googleapis.com/v1/projects/{PROJECT_ID}/databases/(default)/documents/deposits?key={API_KEY}
    
    // TODO: JSONボディを作成
    // Firestoreのフォーマット:
    // {
    //   "fields": {
    //     "amount": { "integerValue": "100" },
    //     "goalId": { "stringValue": "xxx" },
    //     "goalName": { "stringValue": "Nintendo Switch" },
    //     "timestamp": { "timestampValue": "2025-11-13T04:00:00Z" }
    //   }
    // }
    
    // TODO: HTTPリクエストを送信
    
    // TODO: レスポンスを確認
    
    Serial.println("sendDeposit: 未実装");
    return false;
}