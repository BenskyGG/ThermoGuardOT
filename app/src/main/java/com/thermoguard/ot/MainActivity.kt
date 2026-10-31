package com.thermoguard.ot

import android.Manifest
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.UUID

class MainActivity : ComponentActivity() {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var socketConexion: BluetoothSocket? = null
    private val UUID_PROYECTO = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val listaDispositivos = mutableStateListOf<BluetoothDevice>()

    private var temperaturaRecibida = mutableStateOf("--")
    private var estadoAlarma = mutableStateOf(false)
    private var sonidoAlarma: Ringtone? = null

    private val solicitarPermiso = registerForActivityResult(ActivityResultContracts.RequestPermission()) { permiso ->
        if (permiso) encenderAntena()
    }
    private val solicitarBluetooth = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        val uri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        sonidoAlarma = RingtoneManager.getRingtone(applicationContext, uri)

        setContent { PantallaPrincipal() }
    }

    @Composable
    fun PantallaPrincipal() {
        val rolSeleccionado = remember { mutableStateOf("NINGUNO") }

        when (rolSeleccionado.value) {
            "NINGUNO" -> MenuSeleccionRol(
                onSensorClick = { rolSeleccionado.value = "SENSOR" },
                onSupervisorClick = { rolSeleccionado.value = "SUPERVISOR" }
            )
            "SENSOR" -> PantallaEmisorSensor(onVolverClick = {
                cerrarConexiones()
                rolSeleccionado.value = "NINGUNO"
            })
            "SUPERVISOR" -> PantallaReceptorSupervisor(onVolverClick = {
                cerrarConexiones()
                rolSeleccionado.value = "NINGUNO"
            })
        }
    }

    @Composable
    fun MenuSeleccionRol(onSensorClick: () -> Unit, onSupervisorClick: () -> Unit) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "ThermoGuard OT", fontWeight = FontWeight.Bold, fontSize = 28.sp)
            Text(text = "Selecciona el rol de este celular:", modifier = Modifier.padding(bottom = 30.dp))

            Button(onClick = onSensorClick, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) {
                Text("Entrar como SENSOR (Carnicería)", fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = onSupervisorClick, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006400))) {
                Text("Entrar como SUPERVISOR (Receptor)", fontSize = 16.sp)
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Composable
    fun PantallaEmisorSensor(onVolverClick: () -> Unit) {
        val temperaturaSimulada = remember { mutableStateOf(2) }

        Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Sensor ThermoGuard (Emisor)", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(20.dp))

            Button(onClick = { verificarSeguridadYEncender() }, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) { Text("1. Encender Bluetooth") }
            Spacer(modifier = Modifier.height(10.dp))
            Button(onClick = { buscarDispositivosVinculados() }, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) { Text("2. Ver Dispositivos") }
            Spacer(modifier = Modifier.height(20.dp))

            Text(text = "Temperatura Actual: ${temperaturaSimulada.value}°C", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.padding(10.dp)) {
                // Ahora permite bajar hasta -10°C para probar los -5°C
                Button(
                    onClick = { if (temperaturaSimulada.value > -10) temperaturaSimulada.value-- },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Blue)
                ) { Text("- Bajar") }

                Spacer(modifier = Modifier.width(20.dp))

                // Ahora permite subir hasta 15°C para probar los 10°C
                Button(
                    onClick = { if (temperaturaSimulada.value < 15) temperaturaSimulada.value++ },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("+ Subir") }
            }
            Button(onClick = { enviarTemperaturaPorBluetooth(temperaturaSimulada.value) }, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) { Text("ENVIAR SEÑAL DE TEMP") }
            Spacer(modifier = Modifier.height(20.dp))

            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(listaDispositivos) { dispositivo ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { conectarComoEmisor(dispositivo) }) {
                        Text(text = "${dispositivo.name}\nToca para conectar a MAC: ${dispositivo.address}", modifier = Modifier.padding(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Button(onClick = onVolverClick, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)) {
                Text("<- Volver al Menú Principal")
            }
        }
    }

    @Composable
    fun PantallaReceptorSupervisor(onVolverClick: () -> Unit) {
        val colorFondo = if (estadoAlarma.value) Color(0xFFFFCDD2) else Color.Transparent

        Column(
            modifier = Modifier.fillMaxSize().background(colorFondo).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "Panel de Supervisor", fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Spacer(modifier = Modifier.height(30.dp))

            Button(onClick = { iniciarModoSupervisor() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006400))) {
                Text("ABRIR COMPUERTA (Esperar Sensor)")
            }

            Spacer(modifier = Modifier.height(40.dp))
            Text(text = "Temperatura Recibida:", fontSize = 18.sp)
            Text(
                text = "${temperaturaRecibida.value} °C",
                fontSize = 60.sp,
                fontWeight = FontWeight.Bold,
                color = if (estadoAlarma.value) Color.Red else Color.Blue
            )

            // PANEL DE EMERGENCIA CUANDO HAY ALARMA
            if (estadoAlarma.value) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "¡ALERTA!\nTEMPERATURA FUERA DE RANGO",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Red,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        // El botón restablece el sistema a la temperatura segura de 2°C
                        estadoAlarma.value = false
                        temperaturaRecibida.value = "2"
                        sonidoAlarma?.stop()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                ) {
                    Text("NORMALIZAR A RANGO SEGURO (0°C - 4°C)", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
            Button(onClick = onVolverClick, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)) {
                Text("<- Volver al Menú Principal")
            }
        }
    }

    private fun cerrarConexiones() {
        try { socketConexion?.close() } catch (e: Exception) {}
        socketConexion = null
        temperaturaRecibida.value = "--"
        estadoAlarma.value = false
        sonidoAlarma?.stop()
    }

    @SuppressLint("MissingPermission")
    private fun iniciarModoSupervisor() {
        Toast.makeText(this, "Esperando conexión... (Abriendo puerta)", Toast.LENGTH_LONG).show()
        Thread {
            try {
                val serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord("ThermoGuard", UUID_PROYECTO)
                val socket = serverSocket?.accept()
                if (socket != null) {
                    runOnUiThread { Toast.makeText(this, "¡Sensor Conectado con Éxito!", Toast.LENGTH_LONG).show() }
                    val inputStream = socket.inputStream
                    val buffer = ByteArray(1024)

                    while (true) {
                        val bytes = inputStream.read(buffer)
                        val mensaje = String(buffer, 0, bytes)

                        if (mensaje.startsWith("TEMP:")) {
                            val tempLimpiada = mensaje.removePrefix("TEMP:").trim()
                            val temperaturaNumerica = tempLimpiada.toIntOrNull() ?: 2

                            runOnUiThread {
                                temperaturaRecibida.value = tempLimpiada

                                // LA LÓGICA ESTRELLA: Si baja de 0°C o sube de 4°C, dispara la alarma
                                if (temperaturaNumerica < 0 || temperaturaNumerica > 4) {
                                    estadoAlarma.value = true
                                    if (sonidoAlarma?.isPlaying == false) sonidoAlarma?.play()
                                    val db = Firebase.firestore
                                    val doc = db.collection("alertas").document()
                                    doc.set(mapOf(
                                        "temperatura" to tempLimpiada,
                                        "estado" to "ALERTA_TEMPERATURA",
                                        "hora" to System.currentTimeMillis().toString()
                                    ))
                                } else {
                                    // Si entra un número entre 0 y 4 normal, se apaga sola
                                    estadoAlarma.value = false
                                    if (sonidoAlarma?.isPlaying == true) sonidoAlarma?.stop()
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {}
        }.start()
    }

    private fun enviarTemperaturaPorBluetooth(tempActual: Int) {
        val socket = socketConexion
        if (socket != null && socket.isConnected) {
            Thread {
                try {
                    val mensaje = "TEMP:$tempActual\n"
                    socket.outputStream.write(mensaje.toByteArray())
                    runOnUiThread { Toast.makeText(this, "Señal enviada", Toast.LENGTH_SHORT).show() }
                } catch (e: Exception) {
                    runOnUiThread { Toast.makeText(this, "Error al enviar", Toast.LENGTH_SHORT).show() }
                }
            }.start()
        } else Toast.makeText(this, "Aún no estás conectado al otro celular.", Toast.LENGTH_SHORT).show()
    }

    private fun verificarSeguridadYEncender() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permiso = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            if (permiso == PackageManager.PERMISSION_GRANTED) encenderAntena()
            else solicitarPermiso.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else encenderAntena()
    }

    @SuppressLint("MissingPermission")
    private fun encenderAntena() {
        if (bluetoothAdapter?.isEnabled == false) solicitarBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }

    @SuppressLint("MissingPermission")
    private fun buscarDispositivosVinculados() {
        listaDispositivos.clear()
        val permisoOK = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else true

        if (permisoOK) {
            val dispositivos = bluetoothAdapter?.bondedDevices
            if (!dispositivos.isNullOrEmpty()) listaDispositivos.addAll(dispositivos)
            else Toast.makeText(this, "No hay dispositivos vinculados.", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun conectarComoEmisor(dispositivoDestino: BluetoothDevice) {
        Toast.makeText(this, "Intentando conectar con ${dispositivoDestino.name}...", Toast.LENGTH_SHORT).show()
        Thread {
            bluetoothAdapter?.cancelDiscovery()
            try {
                socketConexion = dispositivoDestino.createRfcommSocketToServiceRecord(UUID_PROYECTO)
                socketConexion?.connect()
                runOnUiThread { Toast.makeText(this@MainActivity, "¡CONECTADO! Ya puedes enviar señales.", Toast.LENGTH_LONG).show() }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this@MainActivity, "Rechazado: El otro celular debe presionar 'Abrir Compuerta' primero.", Toast.LENGTH_LONG).show() }
                try { socketConexion?.close() } catch (closeException: Exception) { }
            }
        }.start()
    }
}
