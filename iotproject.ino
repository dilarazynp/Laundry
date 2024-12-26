#include <ESP8266WiFi.h>
#include <FirebaseESP8266.h>
#include <NTPClient.h>  // NTP kütüphanesi
#include <WiFiUdp.h>    // UDP için kütüphane
#include <SD.h>
#include <Time.h>       // Saat işlemleri için kütüphane

// WiFi Ayarları
#define WIFI_SSID "Alaska"
#define WIFI_PASSWORD "88888888"

// Firebase Ayarları
#define FIREBASE_HOST "iotproject-6ace8-default-rtdb.firebaseio.com"
#define FIREBASE_AUTH "fd1lVT9g6GPThsAj7JnzbaWPKd8T8UDkV5LIo9Rv"

// Pin Tanımları
#define VIBRATION_PIN D1 // SW-420 titreşim sensörü
#define LM35_PIN A0      // LM35 sıcaklık sensörü

// Sensör Durumları
int sensorState = 0;
int lastSensorState = LOW;
unsigned long vibrationStoppedTime = 0;
bool isMachineStopped = false;

// Çalışma Süresi
unsigned long startTime = 0;
unsigned long totalRunTime = 0;
unsigned long stopTime = 0;  // Bitiş zamanı

// Firebase Nesneleri
FirebaseData firebaseData;
FirebaseConfig firebaseConfig;
FirebaseAuth firebaseAuth;

// Zamanlama
WiFiUDP udp;
NTPClient timeClient(udp, "pool.ntp.org", 0, 60000); // NTP Client, her dakika güncelleniyor

// Kimlik Değişkeni
String machineId = "";

// Fonksiyonlar
void setup() {
    pinMode(VIBRATION_PIN, INPUT);

    // Seri Haberleşme
    Serial.begin(115200);

    // WiFi Bağlantısı
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    while (WiFi.status() != WL_CONNECTED) {
        delay(500);
        Serial.print(".");
    }
    Serial.println("\nWiFi'ye bağlanıldı.");

    // Firebase Ayarları
    firebaseConfig.host = FIREBASE_HOST;
    firebaseConfig.signer.tokens.legacy_token = FIREBASE_AUTH;
    Firebase.begin(&firebaseConfig, &firebaseAuth);

    // Başlangıçta benzersiz bir kimlik oluştur
    machineId = String(millis());

    // NTP Client başlat
    timeClient.begin();
}

void loop() {
    // NTP Güncellemesi
    timeClient.update();

    // Sensör Durumunu Oku
    sensorState = digitalRead(VIBRATION_PIN);

    // Titreşim Algılama ve Durum Güncelleme
    if (sensorState == LOW) {
        if (vibrationStoppedTime == 0) {
            vibrationStoppedTime = millis();
        }

        if (millis() - vibrationStoppedTime >= 5000 && !isMachineStopped) {
            isMachineStopped = true;
            stopTime = millis(); // Bitiş zamanını kaydet
            updateFirebaseWithTimestamp("laundry_status", "Bitti", machineId);
            updateFirebaseWithTimestamp("stop_time", getCurrentDateTime(), machineId); // Firebase'e bitiş zamanını ekle
            updateRunTime();
        }
    } else {
        vibrationStoppedTime = 0;
        if (isMachineStopped) {
            isMachineStopped = false;
            machineId = String(millis()); // Yeni bir kimlik oluştur
            startTime = millis(); // Başlangıç zamanını kaydet
            updateFirebaseWithTimestamp("laundry_status", "Çalışıyor", machineId);
            updateFirebaseWithTimestamp("start_time", getCurrentDateTime(), machineId); // Firebase'e başlangıç zamanını ekle
        }
    }

    // Çalışma Süresi Güncelleme
    if (!isMachineStopped && startTime > 0) {
        totalRunTime = (millis() - startTime) / 1000; // Saniye cinsine çevir
    }

    // Firebase Güncelleme
    float temperature = readTemperature();
    
    // Firebase'e sıcaklık, çalışma süresi ve durumu verisini zaman damgası ile ekle
    updateFirebaseWithTimestamp("temperature", temperature, machineId);
    updateFirebaseWithTimestamp("total_runtime", totalRunTime, machineId);

    delay(50);
}

// Sıcaklık Okuma
float readTemperature() {
    int sensorValue = analogRead(LM35_PIN);
    float voltage = sensorValue * (3.3 / 1024.0);
    float temperature = voltage * 100; // LM35 için dönüşüm
    Serial.print("Sıcaklık: ");
    Serial.println(temperature);
    return temperature;
}

// Firebase Güncelleme Fonksiyonu
void updateFirebaseWithTimestamp(String key, float value, String id) {
    String path = "/MakineKullanimVerisi/Kullanici1/" + id + "/" + key;
    
    if (Firebase.setFloat(firebaseData, path, value)) {
        Serial.println("Firebase güncellemesi başarılı: " + key + " -> " + String(value));
    } else {
        Serial.print("Firebase güncelleme hatası: ");
        Serial.println(firebaseData.errorReason());
    }
}

// Firebase Güncelleme (Durum için)
void updateFirebaseWithTimestamp(String key, String value, String id) {
    String path = "/MakineKullanimVerisi/Kullanici1/" + id + "/" + key;
    
    if (Firebase.setString(firebaseData, path, value)) {
        Serial.println("Firebase güncellemesi başarılı: " + key + " -> " + value);
    } else {
        Serial.print("Firebase güncelleme hatası: ");
        Serial.println(firebaseData.errorReason());
    }
}

// Çalışma Süresi Güncelleme
void updateRunTime() {
    if (startTime > 0 && stopTime > 0) {
        // Başlangıç ve bitiş zamanı arasındaki farkı hesapla
        totalRunTime = (stopTime - startTime) / 1000;  // Farkı saniye cinsinden al
        
        // Titreşim gelmediği için geçen 5 saniyeyi çıkar
        if (vibrationStoppedTime > 0) {
            unsigned long noVibrationTime = (millis() - vibrationStoppedTime) / 1000;
            if (noVibrationTime >= 5) {
                totalRunTime -= 5;  // 5 saniyeyi çıkar
            }
        }

        // Çalışma süresi verisini Firebase'e zaman damgası ile ekleyelim
        updateFirebaseWithTimestamp("total_runtime", totalRunTime, machineId);
        
        Serial.println("Toplam Çalışma Süresi: " + String(totalRunTime) + " saniye");
        
        // Çalışma bittiğinde startTime ve stopTime'ı sıfırla
        startTime = 0;
        stopTime = 0;
    }
}


String getCurrentDateTime() {
    time_t rawTime = timeClient.getEpochTime();  // Epoch zamanını al
    rawTime += 3 * 3600;  // UTC'den 3 saat ekleyerek Türkiye saatine geçiş yap (UTC+3)

    struct tm* timeInfo = localtime(&rawTime);  // Epoch zamanını tm yapısına dönüştür
    
    String dateTime = String(1900 + timeInfo->tm_year) + "-" +
                      (timeInfo->tm_mon + 1 < 10 ? "0" : "") + String(timeInfo->tm_mon + 1) + "-" +
                      (timeInfo->tm_mday < 10 ? "0" : "") + String(timeInfo->tm_mday) + " " +
                      (timeInfo->tm_hour < 10 ? "0" : "") + String(timeInfo->tm_hour) + ":" +
                      (timeInfo->tm_min < 10 ? "0" : "") + String(timeInfo->tm_min) + ":" +
                      (timeInfo->tm_sec < 10 ? "0" : "") + String(timeInfo->tm_sec);
    return dateTime;
}