package com.example.apppuredata

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.util.Locale
import java.util.UUID

sealed class Param(val name: String)
class SliderParam(name: String, val displayName: String, val min: Float, val max: Float) : Param(name)
class ToggleParam(name: String) : Param(name)

class Effect(val displayName: String, val params: List<Param>)

data class EffectState(
    val sliderValues: MutableMap<String, Int> = mutableMapOf(),
    val toggleValues: MutableMap<String, Boolean> = mutableMapOf()
)

data class Preset(
    var name: String,
    val effectStates: MutableMap<Int, EffectState> = mutableMapOf()
)

class MainActivity : AppCompatActivity() {

    private var socket: BluetoothSocket? = null
    private var output: OutputStream? = null

    private val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val PERMISSION_REQUEST = 100

    private val devicesList = mutableListOf<BluetoothDevice>()
    private val devicesNames = mutableListOf<String>()

    private val effectStates = mutableMapOf<Int, EffectState>()
    private var currentEffectIndex = -1
    private val presets = mutableListOf(
        Preset("PS1"), Preset("PS2"), Preset("PS3"), Preset("PS4")
    )
    private lateinit var efectos: List<Effect>
    private var btSwitch: androidx.appcompat.widget.SwitchCompat? = null

    private val PREFS_NAME = "AppPureDataPrefs"
    private val KEY_PRESETS = "presets"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayShowTitleEnabled(false)

        requestBluetoothPermission()

        findViewById<Button>(R.id.clearParamsBtn).setOnClickListener { showClearParamsDialog() }
        findViewById<Button>(R.id.clearEffectsBtn).setOnClickListener { clearAllEffects() }

        setupGridButtons()
        loadPresets()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            output?.write(("connected 0\n").toByteArray(Charsets.UTF_8))
            output?.flush()
            for (msg in listOf("dis 0", "del 0", "trem 0", "flan 0", "ring 0", "wah 0", "ps1 0", "ps2 0", "ps3 0", "ps4 0")) {
                output?.write(("$msg\n").toByteArray(Charsets.UTF_8))
                output?.flush()
            }
        } catch (_: Exception) {}
        try { output?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        val switchItem = menu.findItem(R.id.action_bt_switch)

        val actionLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 24, 0)
        }
        val btLabel = TextView(this).apply {
            text = "BT"
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(0, 0, 12, 0)
        }
        val sw = androidx.appcompat.widget.SwitchCompat(this)
        btSwitch = sw
        applyBtSwitchColors(sw, false)

        actionLayout.addView(btLabel)
        actionLayout.addView(sw)
        switchItem.actionView = actionLayout

        sw.setOnCheckedChangeListener { _, isChecked ->
            applyBtSwitchColors(sw, isChecked)
            if (isChecked) {
                showPairedDevices()
            } else {
                if (socket != null) disconnectBluetooth()
            }
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_presets -> { showPresetsDialog(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun applyBtSwitchColors(sw: androidx.appcompat.widget.SwitchCompat, isOn: Boolean) {
        sw.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        sw.trackTintList = ColorStateList.valueOf(
            if (isOn) Color.parseColor("#00C853") else Color.parseColor("#D50000")
        )
    }

    private fun applyEffectSwitchColors(sw: androidx.appcompat.widget.SwitchCompat, isOn: Boolean) {
        sw.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        sw.trackTintList = ColorStateList.valueOf(
            if (isOn) Color.parseColor("#00C853") else Color.parseColor("#D50000")
        )
    }

    private fun showPresetsDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }

        for (i in presets.indices) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 8, 0, 8) }
                gravity = Gravity.CENTER_VERTICAL
            }

            val nameEdit = EditText(this).apply {
                setText(presets[i].name)
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                background = null
                addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        presets[i].name = s.toString()
                        savePresets()
                    }
                    override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                    override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                })
            }

            val savedIndicator = TextView(this).apply {
                text = if (presets[i].effectStates.isNotEmpty()) "●" else "○"
                textSize = 14f
                setPadding(0, 0, 12, 0)
                gravity = Gravity.CENTER_VERTICAL
            }

            val saveBtn = Button(this).apply {
                text = "Guardar"
                textSize = 11f
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(6, 0, 6, 0) }
            }

            val applyBtn = Button(this).apply {
                text = "Aplicar"
                textSize = 11f
                isEnabled = presets[i].effectStates.isNotEmpty()
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            saveBtn.setOnClickListener {
                savePreset(i)
                savedIndicator.text = "●"
                applyBtn.isEnabled = true
                sendText("ps${i + 1} 1")
                showToast("${presets[i].name} guardado")
            }

            applyBtn.setOnClickListener {
                applyPreset(i)
                sendText("ps${i + 1} 1")
                showToast("${presets[i].name} aplicado")
            }

            row.addView(nameEdit)
            row.addView(savedIndicator)
            row.addView(saveBtn)
            row.addView(applyBtn)
            layout.addView(row)
        }

        AlertDialog.Builder(this)
            .setTitle("Presets")
            .setView(layout)
            .setNegativeButton("Cerrar") { d, _ -> d.dismiss() }
            .create().show()
    }

    private fun savePreset(index: Int) {
        val snapshot = mutableMapOf<Int, EffectState>()
        for ((effectIdx, state) in effectStates) {
            snapshot[effectIdx] = EffectState(
                sliderValues = state.sliderValues.toMutableMap(),
                toggleValues = state.toggleValues.toMutableMap()
            )
        }
        presets[index] = Preset(name = presets[index].name, effectStates = snapshot)
        savePresets()
    }

    private fun applyPreset(index: Int) {
        val preset = presets[index]
        if (preset.effectStates.isEmpty()) return

        effectStates.clear()
        for ((effectIdx, state) in preset.effectStates) {
            effectStates[effectIdx] = EffectState(
                sliderValues = state.sliderValues.toMutableMap(),
                toggleValues = state.toggleValues.toMutableMap()
            )
        }
        for ((effectIdx, state) in effectStates) {
            val effect = efectos[effectIdx]
            for (param in effect.params) {
                when (param) {
                    is SliderParam -> {
                        val progress = state.sliderValues[param.name] ?: 0
                        val ratio = progress.toFloat() / 1000f
                        val actualValue = param.min + (ratio * (param.max - param.min))
                        sendText("${param.name} ${String.format(Locale.US, "%.2f", actualValue)}")
                    }
                    is ToggleParam -> {
                        val isOn = state.toggleValues[param.name] ?: false
                        sendText("${param.name} ${if (isOn) "1" else "0"}")
                    }
                }
            }
        }
        if (currentEffectIndex >= 0) {
            buildDynamicControls(efectos[currentEffectIndex], currentEffectIndex)
        }
    }

    private fun savePresets() {
        val arr = JSONArray()
        for (preset in presets) {
            val obj = JSONObject()
            obj.put("name", preset.name)
            val statesObj = JSONObject()
            for ((idx, state) in preset.effectStates) {
                val stateObj = JSONObject()
                val slidersObj = JSONObject()
                for ((k, v) in state.sliderValues) slidersObj.put(k, v)
                val togglesObj = JSONObject()
                for ((k, v) in state.toggleValues) togglesObj.put(k, if (v) 1 else 0)
                stateObj.put("sliders", slidersObj)
                stateObj.put("toggles", togglesObj)
                statesObj.put(idx.toString(), stateObj)
            }
            obj.put("states", statesObj)
            arr.put(obj)
        }
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_PRESETS, arr.toString()).apply()
    }

    private fun loadPresets() {
        val json = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PRESETS, null) ?: return
        try {
            val arr = JSONArray(json)
            for (i in 0 until minOf(arr.length(), presets.size)) {
                val obj = arr.getJSONObject(i)
                val name = obj.optString("name", "PS${i + 1}")
                val statesObj = obj.optJSONObject("states") ?: continue
                val effectStatesMap = mutableMapOf<Int, EffectState>()
                val keys = statesObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val idx = key.toIntOrNull() ?: continue
                    val stateObj = statesObj.getJSONObject(key)
                    val sliders = mutableMapOf<String, Int>()
                    val slidersObj = stateObj.optJSONObject("sliders")
                    slidersObj?.keys()?.forEach { k -> sliders[k] = slidersObj.getInt(k) }
                    val toggles = mutableMapOf<String, Boolean>()
                    val togglesObj = stateObj.optJSONObject("toggles")
                    togglesObj?.keys()?.forEach { k -> toggles[k] = togglesObj.getInt(k) == 1 }
                    effectStatesMap[idx] = EffectState(sliders, toggles)
                }
                presets[i] = Preset(name = name, effectStates = effectStatesMap)
            }
        } catch (_: Exception) {}
    }

    private fun disconnectBluetooth() {
        try { sendText("connected 0") } catch (_: Exception) {}
        try {
            for (msg in listOf("dis 0", "del 0", "trem 0", "flan 0", "ring 0", "wah 0", "ps1 0", "ps2 0", "ps3 0", "ps4 0")) {
                sendText(msg)
            }
        } catch (_: Exception) {}
        try { output?.close(); socket?.close() } catch (e: Exception) { e.printStackTrace() }
        output = null
        socket = null
        showToast("Desconectado")
    }

    private fun showClearParamsDialog() {
        val nombres = efectos.map { it.displayName }.toTypedArray()
        val seleccionados = BooleanArray(nombres.size) { false }

        AlertDialog.Builder(this)
            .setTitle("Borrar parámetros de...")
            .setMultiChoiceItems(nombres, seleccionados) { _, which, isChecked ->
                seleccionados[which] = isChecked
            }
            .setPositiveButton("Borrar") { _, _ ->
                for (i in seleccionados.indices) {
                    if (seleccionados[i]) resetEffectState(i)
                }
                if (currentEffectIndex >= 0 && seleccionados[currentEffectIndex]) {
                    buildDynamicControls(efectos[currentEffectIndex], currentEffectIndex)
                }
                showToast("Parámetros borrados")
            }
            .setNegativeButton("Cancelar", null)
            .create().show()
    }

    private fun resetEffectState(effectIndex: Int) {
        val state = effectStates[effectIndex] ?: return
        val effect = efectos[effectIndex]
        for (param in effect.params) {
            when (param) {
                is SliderParam -> {
                    state.sliderValues[param.name] = 0
                    sendText("${param.name} ${String.format(Locale.US, "%.2f", param.min)}")
                }
                is ToggleParam -> {
                    state.toggleValues[param.name] = false
                    sendText("${param.name} 0")
                }
            }
        }
    }

    private fun clearAllEffects() {
        AlertDialog.Builder(this)
            .setTitle("¿Borrar todo?")
            .setMessage("Se borrarán los parámetros de todos los efectos.")
            .setPositiveButton("Borrar") { _, _ ->
                for (i in efectos.indices) resetEffectState(i)
                findViewById<LinearLayout>(R.id.dynamicContainer).removeAllViews()
                currentEffectIndex = -1
                showToast("Todos los efectos borrados")
            }
            .setNegativeButton("Cancelar", null)
            .create().show()
    }

    private fun setupGridButtons() {
        efectos = listOf(
            Effect("Distorsión", listOf(
                SliderParam("disvol", "Volumen", 0f, 1f),
                SliderParam("disgain", "Ganancia", 1f, 50f),
                SliderParam("disfilter", "Filtro", 1000f, 5000f),
                ToggleParam("dis")
            )),
            Effect("Delay", listOf(
                SliderParam("delgan", "Ganancia", 0f, 1f),
                SliderParam("deltemp", "Tiempo", 1f, 1000f),
                ToggleParam("del")
            )),
            Effect("Tremolo", listOf(
                SliderParam("tremvol", "Volúmen", 0f, 1f),
                SliderParam("tremdif", "Diferencia", 0f, 1f),
                SliderParam("tremvel", "Velocidad", 0f, 15f),
                ToggleParam("trem")
            )),
            Effect("Flanger", listOf(
                SliderParam("flanclar", "Claridad", 0f, 0.9f),
                SliderParam("flanalt", "Alternancia", 1f, 10f),
                SliderParam("flanvel", "Velocidad", 0.05f, 5f),
                ToggleParam("flan")
            )),
            Effect("Ring Modulator", listOf(
                SliderParam("ringvol", "Volumen Ring", 0f, 1f),
                SliderParam("ringfreq2", "Frecuencia 2", 50f, 1500f),
                SliderParam("ringfreq1", "Frecuencia 1", 50f, 1500f),
                ToggleParam("ring")
            )),
            Effect("Wah", listOf(
                ToggleParam("wah"),
                SliderParam("wahvol", "Volumen", 0f, 1f),
                SliderParam("wahfilter", "Filtro", 0f, 10f),
                SliderParam("wahvel", "Velocidad", 0f, 10f),
                SliderParam("wahfuerza", "Fuerza", 0f, 1500f),
                SliderParam("wahcaja", "Caja", 0f, 3000f)
            ))
        )

        for (i in efectos.indices) effectStates[i] = EffectState()

        val volContainer = findViewById<LinearLayout>(R.id.volLimpioContainer)
        val volLabel = TextView(this).apply {
            text = "Volumen Limpio: 0.00"
            textSize = 14f
            setPadding(0, 0, 0, 4)
        }
        val volSeekBar = SeekBar(this).apply {
            max = 1000
            progressDrawable = ContextCompat.getDrawable(this@MainActivity, R.drawable.seekbar_track)
            thumb = ContextCompat.getDrawable(this@MainActivity, R.drawable.seekbar_thumb)
            thumbOffset = (11 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (40 * resources.displayMetrics.density).toInt()
            )
        }
        volSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val value = progress.toFloat() / 1000f
                    volLabel.text = "Volumen Limpio: ${String.format(Locale.US, "%.2f", value)}"
                    sendText("volumenlimpio ${String.format(Locale.US, "%.2f", value)}")
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        volContainer.addView(volLabel)
        volContainer.addView(volSeekBar)

        val buttonsIds = listOf(R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4, R.id.btn5, R.id.btn6)
        for (i in buttonsIds.indices) {
            findViewById<Button>(buttonsIds[i]).setOnClickListener {
                currentEffectIndex = i
                buildDynamicControls(efectos[i], i)
            }
        }
    }

    private fun buildDynamicControls(effect: Effect, effectIndex: Int) {
        val container = findViewById<LinearLayout>(R.id.dynamicContainer)
        container.removeAllViews()

        val state = effectStates.getOrPut(effectIndex) { EffectState() }
        val toggleParam = effect.params.filterIsInstance<ToggleParam>().firstOrNull()

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 8, 0, 16) }
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleText = TextView(this).apply {
            text = effect.displayName.uppercase()
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerRow.addView(titleText)

        if (toggleParam != null) {
            val savedToggle = state.toggleValues[toggleParam.name] ?: false
            val effectSwitch = androidx.appcompat.widget.SwitchCompat(this).apply {
                isChecked = savedToggle
                applyEffectSwitchColors(this, savedToggle)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setOnCheckedChangeListener { _, isChecked ->
                    applyEffectSwitchColors(this, isChecked)
                    state.toggleValues[toggleParam.name] = isChecked
                    sendText("${toggleParam.name} ${if (isChecked) "1" else "0"}")
                }
            }
            headerRow.addView(effectSwitch)
        }

        container.addView(headerRow)

        for (param in effect.params) {
            if (param is ToggleParam) continue

            if (param is SliderParam) {
                val label = TextView(this).apply {
                    textSize = 14f
                    setPadding(0, 0, 0, 4)
                }

                val seekBar = SeekBar(this).apply {
                    max = 1000
                    progressDrawable = ContextCompat.getDrawable(this@MainActivity, R.drawable.seekbar_track)
                    thumb = ContextCompat.getDrawable(this@MainActivity, R.drawable.seekbar_thumb)
                    thumbOffset = (11 * resources.displayMetrics.density).toInt()
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (40 * resources.displayMetrics.density).toInt()
                    ).apply { setMargins(0, 0, 0, 20) }
                }

                fun updateLabel(progress: Int) {
                    val ratio = progress.toFloat() / 1000f
                    val actualValue = param.min + (ratio * (param.max - param.min))
                    label.text = "${param.displayName}: ${String.format(Locale.US, "%.2f", actualValue)}"
                }

                val savedProgress = state.sliderValues[param.name] ?: 0
                seekBar.progress = savedProgress
                updateLabel(savedProgress)

                seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            updateLabel(progress)
                            state.sliderValues[param.name] = progress
                            val ratio = progress.toFloat() / 1000f
                            val actualValue = param.min + (ratio * (param.max - param.min))
                            sendText("${param.name} ${String.format(Locale.US, "%.2f", actualValue)}")
                        }
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })

                container.addView(label)
                container.addView(seekBar)
            }
        }
    }

    private fun showToast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun requestBluetoothPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN),
                    PERMISSION_REQUEST
                )
            }
        }
    }

    private fun showPairedDevices() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) { btSwitch?.isChecked = false; return }

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        val adapterBT = bluetoothManager.adapter
        if (adapterBT == null) {
            showToast("Bluetooth no disponible")
            btSwitch?.isChecked = false
            return
        }
        devicesList.clear()
        devicesNames.clear()

        for (device in adapterBT.bondedDevices) {
            devicesList.add(device)
            devicesNames.add(device.name ?: device.address)
        }

        if (devicesNames.isEmpty()) {
            showToast("No se encontraron dispositivos vinculados")
            btSwitch?.isChecked = false
            return
        }

        val btAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, devicesNames)
        AlertDialog.Builder(this)
            .setTitle("Selecciona un dispositivo")
            .setAdapter(btAdapter) { dialog, position ->
                connectToDevice(devicesList[position])
                dialog.dismiss()
            }
            .setNegativeButton("Cerrar") { dialog, _ ->
                btSwitch?.isChecked = false
                btSwitch?.let { applyBtSwitchColors(it, false) }
                dialog.dismiss()
            }
            .setOnCancelListener {
                btSwitch?.isChecked = false
                btSwitch?.let { applyBtSwitchColors(it, false) }
            }
            .create().show()
    }

    private fun connectToDevice(device: BluetoothDevice) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) return

        Thread {
            try {
                socket = device.createRfcommSocketToServiceRecord(uuid)
                socket?.connect()
                output = socket?.outputStream
                runOnUiThread {
                    showToast("Conectado a ${device.name}")
                    btSwitch?.let { applyBtSwitchColors(it, true) }
                }
                sendText("connected 1")
                for (i in presets.indices) {
                    val hasData = presets[i].effectStates.isNotEmpty()
                    sendText("ps${i + 1} ${if (hasData) "1" else "0"}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                socket = null
                output = null
                runOnUiThread {
                    showToast("Error al conectar")
                    btSwitch?.isChecked = false
                    btSwitch?.let { applyBtSwitchColors(it, false) }
                }
            }
        }.start()
    }

    private fun sendText(message: String) {
        try {
            output?.write((message + "\n").toByteArray(Charsets.UTF_8))
            output?.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}