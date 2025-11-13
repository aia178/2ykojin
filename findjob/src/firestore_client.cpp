#include "firestore_client.h"
#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include <time.h>  // ← getTimestampで使う

/**
 * ISO8601形式のタイムスタンプ文字列を取得（UTC）
 */
String getTimestamp() {
    time_t now;
    time(&now);
    struct tm* timeinfo = gmtime(&now);

    char buffer[30];
    strftime(buffer, sizeof(buffer), "%Y-%m-%dT%H:%M:%SZ", timeinfo);

    return String(buffer);
}

/**
 * Firestoreのdepositsコレクションにデータを送信
 * 
 * @param amount 貯金額（円）
 * @param goalId 目標ID
 * @param goalName 目標名
 * @return 成功したらtrue、失敗したらfalse
 */
bool sendDeposit(int amount, const String& goalId, const String& goalName) {
    // --- HTTPSクライアント初期化 ---
    WiFiClientSecure client;
    client.setInsecure();  // 証明書検証スキップ（開発用）

    HTTPClient http;

    // --- FirestoreのURLを組み立てる ---
    String url = String(FIRESTORE_BASE_URL) + "/deposits?key=" + FIREBASE_API_KEY;
    Serial.println("----- Firestore URL -----");
    Serial.println(url);
    Serial.println("-------------------------");

    if (!http.begin(client, url)) {
        Serial.println("http.begin() に失敗しました");
        return false;
    }

    http.addHeader("Content-Type", "application/json");

    // --- JSONボディを作成 ---
    String timestamp = getTimestamp();

    String jsonBody =
        "{"
          "\"fields\": {"
            "\"amount\": {\"integerValue\": \"" + String(amount) + "\"},"
            "\"goalId\": {\"stringValue\": \"" + goalId + "\"},"
            "\"goalName\": {\"stringValue\": \"" + goalName + "\"},"
            "\"timestamp\": {\"timestampValue\": \"" + timestamp + "\"}"
          "}"
        "}";

    Serial.println("----- JSON Body -----");
    Serial.println(jsonBody);
    Serial.println("---------------------");

    // --- POST送信 ---
    int httpCode = http.POST(jsonBody);

    // --- レスポンス確認 ---
    if (httpCode > 0) {
        Serial.printf("✅ HTTPレスポンスコード: %d\n", httpCode);
        String response = http.getString();
        Serial.println("レスポンスボディ:");
        Serial.println(response);

        // Firestoreのcreateは 200 or 201 を返すことが多い
        if (httpCode == HTTP_CODE_OK || httpCode == 200 || httpCode == 201) {
            http.end();
            return true;
        }
    } else {
        Serial.printf("POST失敗: %s\n", http.errorToString(httpCode).c_str());
    }

    http.end();
    return false;
}
