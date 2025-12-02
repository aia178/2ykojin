#include <Arduino.h>  
#include <WiFi.h>
#include <cmath>
#include "config.h"
#include "firestore_client.h"
#include <HX711.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include <ArduinoJson.h>

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
    if (WiFi.status() != WL_CONNECTED) return false;

    WiFiClientSecure client;
    client.setInsecure();
    HTTPClient http;

    String url = String(FIRESTORE_BASE_URL) + ":runQuery?key=" + FIREBASE_API_KEY;

    String queryJson =
        "{"
        "  \"structuredQuery\": {"
        "    \"from\": [{\"collectionId\": \"goals\"}],"
        "    \"where\": {"
        "      \"fieldFilter\": {"
        "        \"field\": {\"fieldPath\": \"selected\"},"
        "        \"op\": \"EQUAL\","
        "        \"value\": {\"booleanValue\": true}"
        "      }"
        "    },"
        "    \"limit\": 1"
        "  }"
        "}";

    if (!http.begin(client, url)) {
        Serial.println("Connection failed");
        return false;
    }

    http.addHeader("Content-Type", "application/json");
    int httpCode = http.POST(queryJson);

    if (httpCode == HTTP_CODE_OK) {
        String payload = http.getString();
        DynamicJsonDocument doc(4096);
        DeserializationError err = deserializeJson(doc, payload);

        if (err) {
            Serial.print("JSON parse failed: ");
            Serial.println(err.c_str());
            http.end();
            return false;
        }

        if (!doc.is<JsonArray>() || doc.size() == 0) {
            Serial.println("No selected goal found (empty result).");
            hasCurrentGoal = false;
            http.end();
            return false;
        }

        JsonObject document = doc[0]["document"];
        if (document.isNull()) {
            Serial.println("document field missing.");
            hasCurrentGoal = false;
            http.end();
            return false;
        }

        String fullPath = document["name"] | "";
        if (fullPath.isEmpty()) {
            Serial.println("document.name missing.");
            hasCurrentGoal = false;
            http.end();
            return false;
        }

        int lastSlash = fullPath.lastIndexOf('/');
        currentGoalId = fullPath.substring(lastSlash + 1);

        JsonObject fields = document["fields"];
        currentGoalName = fields["itemName"]["stringValue"] | "Unknown Goal";

        hasCurrentGoal = true;
        Serial.println("=== Goal Updated ===");
        Serial.println("ID: " + currentGoalId);
        Serial.println("Name: " + currentGoalName);

        http.end();
        return true;
    } else {
        Serial.printf("Fetch failed, error: %d\n", httpCode);
        Serial.println(http.getString());
    }

    http.end();
    return false;
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