#include <Arduino.h>  
#include <WiFi.h>
#include <cmath>
#include "config.h"
#include "firestore_client.h"
#include <HX711.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>

#define DATA 32
#define SCK 25

HX711 scale;

const float calibration = 2280.0F;

String currentGoalId   = "";
String currentGoalName = "";
bool   hasCurrentGoal  = false;

enum State {
    WAITING,
    DETECTING,
    COOLDOWN
};

State currentState = WAITING;
float lastWeight = 0.0f;
unsigned long lastChangeTime = 0;
unsigned long lastSendTime   = 0;
unsigned long lastFetchTime  = 0;

const unsigned long STABLE_TIME    = 1000;
const unsigned long COOLDOWN_TIME  = 3000;
const unsigned long FETCH_INTERVAL = 60000;
const float WEIGHT_THRESHOLD       = 0.5f;
const float STABLE_DELTA_THRESHOLD = 0.1f;

void checkWiFi() {
    if (WiFi.status() != WL_CONNECTED) {
        Serial.println("WiFi切断、再接続中...");
        WiFi.begin(ssid, password);
        while (WiFi.status() != WL_CONNECTED) {
            delay(500);
            Serial.print(".");
        }
        Serial.println("\nWiFi再接続成功");
    }
}

bool fetchSelectedGoal() {
    if (WiFi.status() != WL_CONNECTED) {
        Serial.println("WiFi未接続のため、目標を取得できません");
        return false;
    }

    WiFiClientSecure client;
    client.setInsecure();
    HTTPClient http;

    String url = String(FIRESTORE_BASE_URL) + ":runQuery?key=" + FIREBASE_API_KEY;
    
    if (!http.begin(client, url)) {
        Serial.println("http.begin() に失敗しました (fetchSelectedGoal)");
        return false;
    }

    http.addHeader("Content-Type", "application/json");

    String jsonBody =
        "{"
          "\"structuredQuery\": {"
            "\"from\": [{\"collectionId\": \"goals\"}],"
            "\"where\": {"
              "\"compositeFilter\": {"
                "\"op\": \"AND\","
                "\"filters\": ["
                  "{"
                    "\"fieldFilter\": {"
                      "\"field\": {\"fieldPath\": \"selected\"},"
                      "\"op\": \"EQUAL\","
                      "\"value\": {\"booleanValue\": true}"
                    "}"
                  "},"
                  "{"
                    "\"fieldFilter\": {"
                      "\"field\": {\"fieldPath\": \"active\"},"
                      "\"op\": \"EQUAL\","
                      "\"value\": {\"booleanValue\": true}"
                    "}"
                  "}"
                "]"
              "}"
            "},"
            "\"limit\": 1"
          "}"
        "}";

    int httpCode = http.POST(jsonBody);

    if (httpCode <= 0) {
        Serial.printf("fetchSelectedGoal POST失敗: %s\n", http.errorToString(httpCode).c_str());
        http.end();
        return false;
    }

    Serial.printf("HTTPレスポンスコード: %d\n", httpCode);
    String response = http.getString();
    
    http.end();

    int namePos = response.indexOf("\"name\":\"");
    if (namePos < 0) {
        Serial.println("document.name が見つかりませんでした");
        return false;
    }
    namePos += String("\"name\":\"").length();
    int nameEnd = response.indexOf("\"", namePos);
    if (nameEnd < 0) {
        Serial.println("document.name の終端が見つかりませんでした");
        return false;
    }

    String fullName = response.substring(namePos, nameEnd);
    int lastSlash = fullName.lastIndexOf('/');
    if (lastSlash < 0 || lastSlash == (int)fullName.length() - 1) {
        Serial.println("goalId の抽出に失敗しました");
        return false;
    }

    String goalId = fullName.substring(lastSlash + 1);

    int itemNamePos = response.indexOf("\"itemName\"");
    String goalName = "";

    if (itemNamePos >= 0) {
        int stringValuePos = response.indexOf("\"stringValue\":\"", itemNamePos);
        if (stringValuePos >= 0) {
            stringValuePos += String("\"stringValue\":\"").length();
            int stringValueEnd = response.indexOf("\"", stringValuePos);
            if (stringValueEnd > stringValuePos) {
                goalName = response.substring(stringValuePos, stringValueEnd);
            }
        }
    }

    if (goalName.length() == 0) {
        goalName = "NoNameGoal";
    }

    currentGoalId   = goalId;
    currentGoalName = goalName;
    hasCurrentGoal  = true;

    Serial.println("===== 選択中の目標 =====");
    Serial.print("ID: ");
    Serial.println(currentGoalId);
    Serial.print("名前: ");
    Serial.println(currentGoalName);
    Serial.println("========================");

    return true;
}

bool sendFirestore(int amount){
    if (!hasCurrentGoal) {
        Serial.println("currentGoal が設定されていないため送信しません");
        return false;
    }

    checkWiFi();

    Serial.print("送金: ");
    Serial.print(amount);
    Serial.print(" 円 → ");
    Serial.println(currentGoalName);

    bool success = sendDeposit(amount, currentGoalId, currentGoalName);
        
    if (success) {
        Serial.println("送信成功");
    } else {
        Serial.println("送信失敗");
    }

    return success;
}

float getWeight(){
    float weight = scale.get_units();
    return weight;
}

int returnAmount(float weight) {
    if (weight >= 0.7f && weight < 1.5f) return 1;
    if (weight >= 3.3f && weight < 3.8f) return 5;
    if (weight >= 3.8f && weight < 4.2f) return 50;
    if (weight >= 4.2f && weight < 4.65f) return 10;
    if (weight >= 4.65f && weight < 5.5f) return 100;
    if (weight >= 6.5f && weight < 7.5f) return 500;
    
    return 0;
}

void setup()
{
    Serial.begin(115200);
    delay(10);

    Serial.println("\n\n=== IoT貯金箱 起動 ===");
    Serial.print("接続先: ");
    Serial.println(ssid);

    WiFi.begin(ssid, password);

    while (WiFi.status() != WL_CONNECTED) {
        delay(500);
        Serial.print(".");
    }

    Serial.println("\nWiFi接続成功");
    Serial.print("IP: ");
    Serial.println(WiFi.localIP());

    if (!fetchSelectedGoal()) {
        Serial.println("目標を取得できませんでした");
    }

    scale.begin(DATA, SCK);
    scale.set_scale(calibration);
    scale.tare();
    Serial.println("HX711 初期化完了\n");

    currentState = WAITING;
    lastWeight   = 0.0f;
    lastChangeTime = millis();
    lastSendTime   = 0;
    lastFetchTime  = millis();
}

void loop()
{
    unsigned long now = millis();

    if (now - lastFetchTime > FETCH_INTERVAL) {
        Serial.println("定期的な目標再取得...");
        fetchSelectedGoal();
        lastFetchTime = now;
    }

    float weight = getWeight();

    switch (currentState) {
        case WAITING:
            if (std::abs(weight - lastWeight) > WEIGHT_THRESHOLD) {
                currentState = DETECTING;
                lastChangeTime = now;
                Serial.println("コイン検知中...");
            }
            lastWeight = weight;
            break;

        case DETECTING: {
            float delta = std::abs(weight - lastWeight);

            if (delta < STABLE_DELTA_THRESHOLD) {
                if (now - lastChangeTime > STABLE_TIME) {
                    int amount = returnAmount(weight);
                    if (amount > 0) {
                        Serial.print("判定: ");
                        Serial.print(amount);
                        Serial.println(" 円");

                        sendFirestore(amount);

                        currentState = COOLDOWN;
                        lastSendTime = now;

                        scale.tare();
                        lastWeight = 0.0f;
                    } else {
                        Serial.println("判定失敗");
                        currentState = WAITING;
                        scale.tare();
                        lastWeight = 0.0f;
                    }
                }
            } else {
                lastChangeTime = now;
            }

            lastWeight = weight;
            break;
        }

        case COOLDOWN:
            if (now - lastSendTime > COOLDOWN_TIME) {
                Serial.println("クールダウン終了\n");
                currentState = WAITING;
                scale.tare();
                lastWeight = 0.0f;
            }
            break;
    }

    delay(100);
}