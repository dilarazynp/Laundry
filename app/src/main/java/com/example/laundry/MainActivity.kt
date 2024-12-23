package com.example.laundry

import androidx.compose.ui.res.painterResource
import android.Manifest
import androidx.compose.material.icons.Icons
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.laundry.ui.theme.LaundryTheme
import com.google.firebase.database.*

class MainActivity : ComponentActivity() {

    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = FirebaseDatabase.getInstance().reference

        setContent {
            LaundryTheme {
                var laundryStatus by remember { mutableStateOf("Durum: Bekleniyor...") }
                var temperature by remember { mutableStateOf("Sıcaklık: Bekleniyor...") }
                var totalRuntime by remember { mutableStateOf("Çalışma Süresi: Bekleniyor...") }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        MainScreen(
                            laundryStatus = laundryStatus,
                            temperature = temperature,
                            totalRuntime = totalRuntime,
                            onButtonClick = {
                                getMachineData(
                                    onStatusReceived = { status ->
                                        laundryStatus = "$status"
                                    },
                                    onTemperatureReceived = { temp ->
                                        val calculatedTemp = temp.toDouble() * 100
                                        temperature = "${calculatedTemp.toInt()} °C"
                                    },
                                    onRuntimeReceived = { runtime ->
                                        totalRuntime = " $runtime saniye"
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }

        createNotificationChannel()
        listenToFirebaseDatabase()
        checkNotificationPermission()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "laundry_notifications"
            val channelName = "Çamaşır Bildirimleri"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = "Çamaşır makinesi durum bildirimleri"
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun listenToFirebaseDatabase() {
        val ref = database.child("laundry_status")

        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.getValue(String::class.java)
                if (status == "Bitti") {
                    showNotification("Çamaşır Makinesi", "Program tamamlandı!")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                error.toException().printStackTrace()
            }
        })
    }

    private fun getMachineData(
        onStatusReceived: (String) -> Unit,
        onTemperatureReceived: (String) -> Unit,
        onRuntimeReceived: (String) -> Unit
    ) {
        database.get().addOnSuccessListener { snapshot ->
            val status = snapshot.child("laundry_status").getValue(String::class.java)
            val temp = snapshot.child("temperature").getValue(Double::class.java)
            val runtime = snapshot.child("total_runtime").getValue(Int::class.java)

            status?.let { onStatusReceived(it) }
            temp?.let { onTemperatureReceived(it.toString()) }
            runtime?.let { onRuntimeReceived(it.toString()) }
        }.addOnFailureListener { exception ->
            Toast.makeText(this, "Veri alınamadı: ${exception.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showNotification(title: String, message: String) {
        val channelId = "laundry_notifications"
        val notificationId = 1

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            with(NotificationManagerCompat.from(this)) {
                notify(notificationId, builder.build())
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            Toast.makeText(this, "Bildirim izni gerekli!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
    }
}

@Composable
fun MainScreen(
    laundryStatus: String,
    temperature: String,
    totalRuntime: String,
    onButtonClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "👗 Çamaşır Makinesi Durumu",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Image(
                painter = painterResource(id = R.drawable.ic_laundry),
                contentDescription = "Laundry Icon",
                modifier = Modifier.size(120.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            StatusCard(label = "Durum", value = laundryStatus)
            StatusCard(label = "Sıcaklık", value = temperature)
            StatusCard(label = "Çalışma Süresi", value = totalRuntime)

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onButtonClick,
                colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Makineyi Kontrol Et",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun StatusCard(label: String, value: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.elevatedCardElevation(4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    LaundryTheme {
        MainScreen(
            laundryStatus = "Durum: Çalışıyor",
            temperature = "Sıcaklık: 30°C",
            totalRuntime = "Çalışma Süresi: 15 saniye",
            onButtonClick = {}
        )
    }
}
