package com.thermoguard.ot

import android.Manifest
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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

private object TG {
    val Fondo = Color(0xFF0D1014)
    val Superficie = Color(0xFF151A1F)
    val SuperficieAlta = Color(0xFF1D242B)
    val Borde = Color(0xFF2A333C)
    val TextoPrimario = Color(0xFFE8ECEF)
    val TextoSecundario = Color(0xFF8E979E)
    val TextoTenue = Color(0xFF5C666E)
    val Azul = Color(0xFF1E88E5)
    val Cian = Color(0xFF22C7E8)
    val Rojo = Color(0xFFE62117)
    val RojoOscuro = Color(0xFF3A1412)
    val Verde = Color(0xFF2ECC71)
}

private val FuenteDatos = FontFamily.Monospace
private enum class TipoBoton { PRIMARIO, SECUNDARIO, PELIGRO, FANTASMA }

@Composable
private fun BotonIndustrial(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tipo: TipoBoton = TipoBoton.SECUNDARIO,
    alto: Dp = 54.dp
) {
    val (fondo, contenido, borde) = when (tipo) {
        TipoBoton.PRIMARIO -> Triple(TG.Azul, Color.White, TG.Cian.copy(alpha = 0.5f))
        TipoBoton.PELIGRO -> Triple(TG.Rojo, Color.White, Color(0xFFFF5A4F).copy(alpha = 0.5f))
        TipoBoton.SECUNDARIO -> Triple(TG.SuperficieAlta, TG.TextoPrimario, TG.Borde)
        TipoBoton.FANTASMA -> Triple(Color.Transparent, TG.TextoSecundario, TG.Borde)
    }
    Button(
        onClick = onClick,
        modifier = modifier.height(alto),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = fondo,
            contentColor = contenido,
            disabledContainerColor = TG.Superficie,
            disabledContentColor = TG.TextoTenue
        ),
        border = BorderStroke(1.dp, borde),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (tipo == TipoBoton.FANTASMA) 0.dp else 4.dp,
            pressedElevation = 0.dp
        )
    ) {
        Text(text = texto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PanelTarjeta(
    modifier: Modifier = Modifier,
    colorBorde: Color = TG.Borde,
    contenido: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = TG.Superficie),
        border = BorderStroke(1.dp, colorBorde),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        contenido()
    }
}

@Composable
private fun EncabezadoPantalla(titulo: String, subtitulo: String, estado: String, colorEstado: Color) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.width(4.dp).height(40.dp).clip(RoundedCornerShape(2.dp)).background(TG.Rojo))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = titulo, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TG.TextoPrimario)
            Text(text = subtitulo, fontSize = 12.sp, color = TG.TextoSecundario)
        }
        ChipEstado(texto = estado, color = colorEstado)
    }
}

@Composable
private fun ChipEstado(texto: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = texto, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = color)
    }
}

@Composable
private fun TarjetaRol(titulo: String, descripcion: String, colorAcento: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TG.Superficie)
            .border(1.dp, TG.Borde, RoundedCornerShape(14.dp))
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(6.dp).height(84.dp).background(colorAcento))
        Column(modifier = Modifier.weight(1f).padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(text = titulo, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TG.TextoPrimario)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = descripcion, fontSize = 12.sp, color = TG.TextoSecundario)
        }
        Text(text = "›", fontSize = 28.sp, color = colorAcento, modifier = Modifier.padding(end = 20.dp))
    }
}

class MainActivity : ComponentActivity() {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var socketConexion: BluetoothSocket? = null
    private val uuidProyecto = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val listaDispositivos = mutableStateListOf<BluetoothDevice>()

    private var temperaturaRecibida = mutableStateOf("--")
    private var estadoAlarma = mutableStateOf(false)
    private var sonidoAlarma: Ringtone? = null

    private val solicitarPermiso = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permisos ->
        val concedidoConnect = permisos[Manifest.permission.BLUETOOTH_CONNECT] ?: false
        val concedidoScan = permisos[Manifest.permission.BLUETOOTH_SCAN] ?: false

        if (concedidoConnect || concedidoScan || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            encenderAntena()
        } else {
            Toast.makeText(this, "Debes aceptar los permisos de Bluetooth para continuar.", Toast.LENGTH_LONG).show()
        }
    }
    private val solicitarBluetooth = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        val uri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        sonidoAlarma = RingtoneManager.getRingtone(applicationContext, uri)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            sonidoAlarma?.isLooping = true
        }

        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = TG.Fondo) {
                PantallaPrincipal()
            }
        }
    }

    fun evaluarYGuardarTemperatura(temperatura: Int) {
        val db = FirebaseFirestore.getInstance()
        val estadoActual = when {
            temperatura > 10 -> "ALERTA_CALOR_ALTO"
            temperatura < -5 -> "ALERTA_FRIO_EXTREMO"
            else -> "NORMAL"
        }

        val datosAlerta = hashMapOf(
            "temperatura" to temperatura,
            "estado" to estadoActual,
            "hora" to System.currentTimeMillis().toString()
        )

        db.collection("alertas").add(datosAlerta)
            .addOnSuccessListener {
                android.util.Log.d("DEBUG_FIREBASE", "Temperatura $temperatura registrada: $estadoActual")
            }
            .addOnFailureListener { e ->
                android.util.Log.e("DEBUG_FIREBASE", "Error al guardar: ${e.message}")
            }
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
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(colors = listOf(Color(0xFF151B21), TG.Fondo)))
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, TG.Borde, RoundedCornerShape(16.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo_thermoguard),
                    contentDescription = "Logo ThermoGuard OT",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(170.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(text = "Monitoreo de cadena de frío", fontSize = 13.sp, color = TG.TextoSecundario, letterSpacing = 1.sp, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(32.dp))
            Text(text = "Elige tu rol en esta unidad", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TG.TextoPrimario, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))

            TarjetaRol(titulo = "Sensor", descripcion = "Carnicería · envía la temperatura por Bluetooth", colorAcento = TG.Cian, onClick = onSensorClick)
            Spacer(modifier = Modifier.height(14.dp))
            TarjetaRol(titulo = "Supervisor", descripcion = "Recepción · recibe lecturas y alarmas", colorAcento = TG.Rojo, onClick = onSupervisorClick)
            Spacer(modifier = Modifier.height(32.dp))
            Text(text = "Rango seguro: 0 °C a 4 °C", fontSize = 12.sp, fontFamily = FuenteDatos, color = TG.TextoTenue)
        }
    }

    @SuppressLint("MissingPermission")
    @Composable
    fun PantallaEmisorSensor(onVolverClick: () -> Unit) {
        val temperaturaSimulada = remember { mutableStateOf(2) }
        val fueraDeRango = temperaturaSimulada.value !in 0..4
        val colorLectura = if (fueraDeRango) TG.Rojo else TG.Cian

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(TG.Fondo)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            EncabezadoPantalla(
                titulo = "Sensor",
                subtitulo = "Emisor de temperatura",
                estado = if (fueraDeRango) "FUERA DE RANGO" else "EN RANGO",
                colorEstado = if (fueraDeRango) TG.Rojo else TG.Verde
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BotonIndustrial(
                    texto = "Activar red",
                    onClick = { verificarSeguridadYEncender() },
                    modifier = Modifier.weight(1f),
                    tipo = TipoBoton.PELIGRO,
                    alto = 50.dp
                )
                BotonIndustrial(
                    texto = "Listar dispositivos",
                    onClick = { buscarDispositivosVinculados() },
                    modifier = Modifier.weight(1f),
                    tipo = TipoBoton.SECUNDARIO,
                    alto = 50.dp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            PanelTarjeta(modifier = Modifier.fillMaxWidth(), colorBorde = colorLectura.copy(alpha = 0.6f)) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Simulación de temperatura", fontSize = 12.sp, color = TG.TextoSecundario, letterSpacing = 0.5.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "${temperaturaSimulada.value}°C", fontSize = 56.sp, fontFamily = FuenteDatos, fontWeight = FontWeight.Bold, color = colorLectura)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                if (temperaturaSimulada.value > -10) {
                                    temperaturaSimulada.value--
                                    evaluarYGuardarTemperatura(temperaturaSimulada.value)
                                }
                            },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, TG.Azul.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F1A2A))
                        ) {
                            Text("− Bajar", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TG.Cian)
                        }

                        Button(
                            onClick = {
                                if (temperaturaSimulada.value < 15) {
                                    temperaturaSimulada.value++
                                    evaluarYGuardarTemperatura(temperaturaSimulada.value)
                                }
                            },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, TG.Rojo.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.buttonColors(containerColor = TG.RojoOscuro)
                        ) {
                            Text("+ Subir", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TG.Rojo)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            BotonIndustrial(
                texto = "Enviar señal",
                onClick = { enviarTemperaturaPorBluetooth(temperaturaSimulada.value) },
                modifier = Modifier.fillMaxWidth(),
                tipo = TipoBoton.PRIMARIO,
                alto = 58.dp
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(text = "Dispositivos vinculados", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TG.TextoSecundario, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(listaDispositivos) { dispositivo ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(TG.Superficie)
                            .border(1.dp, TG.Borde, RoundedCornerShape(10.dp))
                            .clickable { conectarComoEmisor(dispositivo) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = dispositivo.name ?: "UNIDAD DESCONOCIDA", fontWeight = FontWeight.Bold, color = TG.TextoPrimario)
                            Text(text = "ID: ${dispositivo.address}", fontSize = 11.sp, fontFamily = FuenteDatos, color = TG.TextoSecundario)
                        }
                        Text(text = "Conectar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TG.Cian)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            BotonIndustrial(texto = "Regresar", onClick = onVolverClick, modifier = Modifier.fillMaxWidth(), tipo = TipoBoton.FANTASMA, alto = 48.dp)
        }
    }

    @Composable
    fun PantallaReceptorSupervisor(onVolverClick: () -> Unit) {
        val enAlarma = estadoAlarma.value
        val sinSenal = temperaturaRecibida.value == "--"

        val pulso = rememberInfiniteTransition(label = "pulsoAlarma")
        val alfaPulso = pulso.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "alfaPulso"
        )

        val colorFondo = if (enAlarma) Color(0xFF16100F) else TG.Fondo
        val colorBordeCard = if (enAlarma) TG.Rojo.copy(alpha = alfaPulso.value) else TG.Azul
        val colorLectura = if (enAlarma) TG.Rojo else TG.Cian

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorFondo)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                EncabezadoPantalla(
                    titulo = "Panel de control",
                    subtitulo = "Supervisor · recepción",
                    estado = when {
                        enAlarma -> "ALARMA"
                        sinSenal -> "SIN SEÑAL"
                        else -> "NORMAL"
                    },
                    colorEstado = when {
                        enAlarma -> TG.Rojo
                        sinSenal -> TG.TextoSecundario
                        else -> TG.Verde
                    }
                )

                Spacer(modifier = Modifier.height(28.dp))

                BotonIndustrial(
                    texto = "Esperar señal (modo receptor)",
                    onClick = { iniciarModoSupervisor() },
                    modifier = Modifier.fillMaxWidth(),
                    tipo = TipoBoton.PRIMARIO,
                    alto = 58.dp
                )

                Spacer(modifier = Modifier.height(28.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = TG.Superficie),
                    border = BorderStroke(2.dp, colorBordeCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Datos recibidos", fontSize = 12.sp, color = TG.TextoSecundario, letterSpacing = 0.5.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = "${temperaturaRecibida.value} °C", fontSize = 64.sp, fontFamily = FuenteDatos, fontWeight = FontWeight.Bold, color = colorLectura)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Rango seguro: 0 °C a 4 °C", fontSize = 11.sp, fontFamily = FuenteDatos, color = TG.TextoTenue)
                    }
                }

                if (enAlarma) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = TG.RojoOscuro),
                        border = BorderStroke(1.dp, TG.Rojo)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "⚠️ ANOMALÍA DETECTADA", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF6B60), textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "Lectura fuera de rango de seguridad.", fontSize = 13.sp, color = TG.TextoPrimario, textAlign = TextAlign.Center)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    BotonIndustrial(
                        texto = "Restablecer sistema",
                        onClick = {
                            estadoAlarma.value = false
                            temperaturaRecibida.value = "2"
                            sonidoAlarma?.stop()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        tipo = TipoBoton.SECUNDARIO,
                        alto = 58.dp
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
                BotonIndustrial(texto = "Regresar", onClick = onVolverClick, modifier = Modifier.fillMaxWidth(), tipo = TipoBoton.FANTASMA, alto = 48.dp)
            }
        }
    }

    private fun cerrarConexiones() {
        try { socketConexion?.close() } catch (_: Exception) {}
        socketConexion = null
        temperaturaRecibida.value = "--"
        estadoAlarma.value = false
        sonidoAlarma?.stop()
    }

    @SuppressLint("MissingPermission")
    private fun iniciarModoSupervisor() {
        Toast.makeText(this, "Esperando conexión...", Toast.LENGTH_LONG).show()
        Thread {
            try {
                val serverSocket = bluetoothAdapter?.listenUsingInsecureRfcommWithServiceRecord("ThermoGuard", uuidProyecto)
                val socket = serverSocket?.accept()
                serverSocket?.close()

                if (socket != null) {
                    socketConexion = socket
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
                                if (temperaturaNumerica !in 0..4) {
                                    estadoAlarma.value = true
                                    if (sonidoAlarma?.isPlaying == false) sonidoAlarma?.play()

                                    val db = FirebaseFirestore.getInstance()
                                    db.collection("alertas").add(mapOf(
                                        "temperatura" to temperaturaNumerica,
                                        "estado" to "ALERTA_TEMPERATURA",
                                        "hora" to System.currentTimeMillis().toString()
                                    ))
                                } else {
                                    estadoAlarma.value = false
                                    if (sonidoAlarma?.isPlaying == true) sonidoAlarma?.stop()
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Error del Supervisor: ${e.message}", Toast.LENGTH_LONG).show() }
            }
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
                    runOnUiThread { Toast.makeText(this, "Error al enviar: ${e.message}", Toast.LENGTH_SHORT).show() }
                }
            }.start()
        } else {
            Toast.makeText(this, "Aún no estás conectado.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun verificarSeguridadYEncender() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permisoConnect = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            val permisoScan = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)

            if (permisoConnect == PackageManager.PERMISSION_GRANTED && permisoScan == PackageManager.PERMISSION_GRANTED) {
                encenderAntena()
            } else {
                solicitarPermiso.launch(arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                ))
            }
        } else {
            encenderAntena()
        }
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
            if (!dispositivos.isNullOrEmpty()) listaDispositilsSafe(dispositivos)
            else Toast.makeText(this, "No hay dispositivos vinculados.", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun listaDispositilsSafe(dispositivos: Set<BluetoothDevice>) {
        listaDispositivos.clear()
        listaDispositivos.addAll(dispositivos)
    }

    @SuppressLint("MissingPermission")
    private fun conectarComoEmisor(dispositivoDestino: BluetoothDevice) {
        Toast.makeText(this, "Conectando con ${dispositivoDestino.name}...", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                bluetoothAdapter?.cancelDiscovery()
                try { socketConexion?.close() } catch (_: Exception) {}

                Thread.sleep(500)

                socketConexion = dispositivoDestino.createInsecureRfcommSocketToServiceRecord(uuidProyecto)
                socketConexion?.connect()

                runOnUiThread { Toast.makeText(this@MainActivity, "¡CONECTADO! Ya puedes enviar señales.", Toast.LENGTH_LONG).show() }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this@MainActivity, "Fallo al conectar: ${e.message}", Toast.LENGTH_LONG).show() }
                try { socketConexion?.close() } catch (_: Exception) {}
                socketConexion = null
            }
        }.start()
    }
}