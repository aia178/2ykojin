#include <Arduino.h>  
#include <WiFi.h>
#include "config.h"
#include "firestore_client.h"
#include <HX711.h>

#define DATA 32
#define SCK 25

HX711 scale;

const float calibration = 2280.0F;  

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

    scale.begin(DATA, SCK);

    scale.set_scale(calibration);
    scale.tare();
    Serial.println("HX711 initialized.");
    
}

void loop()
{
    float weight = getWeight();
    int amount = returnAmount(weight);

    if (amount > 0)
    {
        Serial.print("判定金額: ");
        Serial.print(amount);
        Serial.println(" 円");
    }else {
        Serial.println("該当しない硬貨です！");
    }
    


    sendFirestore(amount);
  
    delay(100);
}

// Firestoreに送金データを送る
bool sendFirestore(int amount){
    
    
    Serial.print("送金金額: ");
    Serial.print(amount);
    Serial.println(" 円");

    bool success = sendDeposit(amount, "test-goal-id", "テスト目標");
        
    if (success) {
        Serial.println("送信成功！");
    } else {
        Serial.println("送信失敗");
    }

    return success;
    
}


// 重さ取ってきて表示する
float getWeight(){
    float weight = scale.get_units();
    Serial.print("Weight: ");
    Serial.print(weight);
    Serial.println(" g");

    return weight;
}

int returnAmount(float weight) {
    
    if (weight >= 0.6f && weight < 1.3f) {
        return 1;   // 1円
    }

    
    if (weight >= 3.0f && weight < 3.6f) {
        return 5;   // 5円
    }

    
    if (weight >= 3.6f && weight < 3.9f) {
        return 50;
    }

    
    if (weight >= 3.9f && weight < 4.35f) {
        return 10;
    }

    
    if (weight >= 4.35f && weight < 4.9f) {
        return 100;
    }

    
    if (weight >= 6.6f && weight < 7.6f) {
        return 500; // 500円
    }

    //該当なし
    return 0;
}
