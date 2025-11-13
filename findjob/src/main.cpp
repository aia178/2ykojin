#include <Arduino.h>  // 追加
#include <WiFi.h>
#include "config.h"
#include "firestore_client.h"

void setup()
{
    Serial.begin(115200);
    delay(10);

    Serial.println();
    Serial.println();
    Serial.print("Connecting to ");
    Serial.println(ssid);

    WiFi.begin(ssid, password);

    while (WiFi.status() != WL_CONNECTED) {
        delay(500);
        Serial.print(".");
    }

    Serial.println("");
    Serial.println("WiFi connected");
    Serial.println("IP address: ");
    Serial.println(WiFi.localIP());
}

void loop()
{
  // シリアルから入力があったら100円送信テスト
    if (Serial.available() > 0) {
        Serial.read(); // 入力を消費
        
        Serial.println("--- 送金テスト開始 ---");
        bool success = sendDeposit(100, "test-goal-id", "テスト目標");
        
        if (success) {
            Serial.println("✅ 送信成功！");
        } else {
            Serial.println("❌ 送信失敗");
        }
        Serial.println("--- テスト終了 ---");
    }
    
    delay(100);
}