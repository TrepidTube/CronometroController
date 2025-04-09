package com.example.cronometro;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.EditText;
import android.widget.Button;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.os.SystemClock;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "Cronometro";
    private static final int PUERTO = 8080;
    private static final String PREFS_NAME = "CronometroPrefs";
    private static final String LAST_IP_KEY = "lastIP";
    private static String serverIp = "0.0.0.0";
    private TextView tvIpAddress;
    private TextView tvConnected;
    private TextView tvDisconnected;
    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;
    private boolean isConnected = false;
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private boolean isRunning = false;
    private int currentSeconds = 0;
    private int maxSeconds = 0;
    private boolean isAscending = true;
    private View minuteDigit1, minuteDigit2, secondDigit1, secondDigit2;
    private static TextView[] timeDigits = new TextView[4];
    private int presetSeconds = 0;
    private TextView[] presetDigits = new TextView[4];
    private TextView[] periodDigits = new TextView[2];
    private Handler handler = new Handler(Looper.getMainLooper());
    private long startTime = 0;
    private boolean isUpdateMode = true; // Variable para controlar el modo del botón Play

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Recuperar la última IP utilizada
        serverIp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .getString(LAST_IP_KEY, "0.0.0.0");

        // Configurar la conexión IP
        tvIpAddress = findViewById(R.id.tvIpAddress);
        tvConnected = findViewById(R.id.tvConnected);
        tvDisconnected = findViewById(R.id.tvDisconnected);
        tvIpAddress.setText("IP: " + serverIp);
        
        // Configurar estado inicial de conexión
        actualizarEstadoConexion(false);
        
        // Configurar el TextView de IP como botón
        tvIpAddress.setOnClickListener(v -> {
            if (!isConnected) {
                mostrarDialogoIP();
            } else {
                desconectarDelServidor();
            }
        });

        // Intentar conexión automática si hay una IP válida guardada
        if (isValidIpFormat(serverIp) && !serverIp.equals("0.0.0.0")) {
            new Thread(this::conectarAlServidor).start();
        }

        // Configurar los spinners de dígitos para periodos
        setupDigitSpinner(R.id.periodDigit1);
        setupDigitSpinner(R.id.periodDigit2);
        
        // Configurar los dígitos del tiempo con spinners
        minuteDigit1 = findViewById(R.id.minuteDigit1);
        minuteDigit2 = findViewById(R.id.minuteDigit2);
        secondDigit1 = findViewById(R.id.secondDigit1);
        secondDigit2 = findViewById(R.id.secondDigit2);
        
        timeDigits[0] = minuteDigit1.findViewById(R.id.tvDigit);
        timeDigits[1] = minuteDigit2.findViewById(R.id.tvDigit);
        timeDigits[2] = secondDigit1.findViewById(R.id.tvDigit);
        timeDigits[3] = secondDigit2.findViewById(R.id.tvDigit);

        // Configurar los spinners del tiempo
        setupTimeSpinner(minuteDigit1);
        setupTimeSpinner(minuteDigit2);
        setupTimeSpinner(secondDigit1);
        setupTimeSpinner(secondDigit2);

        // Configurar los spinners de presets
        setupTimeSpinner(findViewById(R.id.presetMinuteDigit1));
        setupTimeSpinner(findViewById(R.id.presetMinuteDigit2));
        setupTimeSpinner(findViewById(R.id.presetSecondDigit1));
        setupTimeSpinner(findViewById(R.id.presetSecondDigit2));

        // Configurar referencias a los dígitos de preset
        View presetMinuteDigit1 = findViewById(R.id.presetMinuteDigit1);
        View presetMinuteDigit2 = findViewById(R.id.presetMinuteDigit2);
        View presetSecondDigit1 = findViewById(R.id.presetSecondDigit1);
        View presetSecondDigit2 = findViewById(R.id.presetSecondDigit2);
        
        presetDigits[0] = presetMinuteDigit1.findViewById(R.id.tvDigit);
        presetDigits[1] = presetMinuteDigit2.findViewById(R.id.tvDigit);
        presetDigits[2] = presetSecondDigit1.findViewById(R.id.tvDigit);
        presetDigits[3] = presetSecondDigit2.findViewById(R.id.tvDigit);

        // Configurar referencias a los dígitos de periodo
        View periodDigit1View = findViewById(R.id.periodDigit1);
        View periodDigit2View = findViewById(R.id.periodDigit2);
        periodDigits[0] = periodDigit1View.findViewById(R.id.tvDigit);
        periodDigits[1] = periodDigit2View.findViewById(R.id.tvDigit);

        // Configurar botones de control
        ImageButton btnPlay = findViewById(R.id.btnPlay);
        ImageButton btnPause = findViewById(R.id.btnPause);
        ImageButton btnStop = findViewById(R.id.btnStop);

        btnPlay.setOnClickListener(v -> {
            if (isUpdateMode) {
                // Modo actualización: envía los valores de Periodo, Reloj y Preset a Pantalla
                updatePresetTime();
                
                // Enviar comando TIME con los valores del reloj
                String[] timeValues = new String[4];
                for (int i = 0; i < 4; i++) {
                    timeValues[i] = timeDigits[i].getText().toString();
                }
                envioDatos("TIME", timeValues);
                
                // Enviar comando PERIOD con los valores del periodo
                String[] periodValues = new String[2];
                periodValues[0] = periodDigits[0].getText().toString();
                periodValues[1] = periodDigits[1].getText().toString();
                envioDatos("PERIOD", periodValues);

                // Enviar comando PRESET con los valores del preset
                String[] presetValues = new String[4];
                for (int i = 0; i < 4; i++) {
                    presetValues[i] = presetDigits[i].getText().toString();
                }
                envioDatos("PRESET", presetValues);
                
                // Cambiar a modo inicio
                isUpdateMode = false;
                btnPlay.setBackgroundResource(R.drawable.play_button_green_selector);
            } else {
                // Modo inicio: inicia el cronómetro en ambas apps
                updatePresetTime();
                updateMaxTime();
                startTimer();
                setTimeSpinnersEnabled(false);
                
                // Enviar comando PLAY sin datos adicionales
                envioDatos("PLAY");
            }
        });

        btnPause.setOnClickListener(v -> {
            pauseTimer();
            envioDatos("PAUSE");
        });

        btnStop.setOnClickListener(v -> {
            stopTimer();
            setTimeSpinnersEnabled(true);
            envioDatos("STOP");
            
            // Volver a modo actualización
            isUpdateMode = true;
            btnPlay.setBackgroundResource(R.drawable.play_button_blue_selector);
        });

        // Configurar el Spinner de modos
        Spinner spinnerMode = findViewById(R.id.spinnerMode);
        ImageView ivModeArrow = findViewById(R.id.ivModeArrow);
        
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"Ascendente", "Descendente"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMode.setAdapter(adapter);

        spinnerMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                isAscending = position == 0;
                
                ivModeArrow.animate()
                    .rotation(isAscending ? 0 : 180)
                    .setDuration(300)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();

                if (isRunning) {
                    stopTimer();
                }
                
                // Enviar modo actual
                envioDatos("MODE", isAscending ? "ASC" : "DESC");
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setTimeSpinnersEnabled(boolean enabled) {
        float alpha = enabled ? 1.0f : 0.5f;
        
        // Minutos dígito 1
        ImageButton btnUp1 = minuteDigit1.findViewById(R.id.btnUp);
        ImageButton btnDown1 = minuteDigit1.findViewById(R.id.btnDown);
        btnUp1.setEnabled(enabled);
        btnDown1.setEnabled(enabled);
        btnUp1.setAlpha(alpha);
        btnDown1.setAlpha(alpha);

        // Minutos dígito 2
        ImageButton btnUp2 = minuteDigit2.findViewById(R.id.btnUp);
        ImageButton btnDown2 = minuteDigit2.findViewById(R.id.btnDown);
        btnUp2.setEnabled(enabled);
        btnDown2.setEnabled(enabled);
        btnUp2.setAlpha(alpha);
        btnDown2.setAlpha(alpha);

        // Segundos dígito 1
        ImageButton btnUp3 = secondDigit1.findViewById(R.id.btnUp);
        ImageButton btnDown3 = secondDigit1.findViewById(R.id.btnDown);
        btnUp3.setEnabled(enabled);
        btnDown3.setEnabled(enabled);
        btnUp3.setAlpha(alpha);
        btnDown3.setAlpha(alpha);

        // Segundos dígito 2
        ImageButton btnUp4 = secondDigit2.findViewById(R.id.btnUp);
        ImageButton btnDown4 = secondDigit2.findViewById(R.id.btnDown);
        btnUp4.setEnabled(enabled);
        btnDown4.setEnabled(enabled);
        btnUp4.setAlpha(alpha);
        btnDown4.setAlpha(alpha);

        // Periodos spinners
        View periodDigit1View = findViewById(R.id.periodDigit1);
        View periodDigit2View = findViewById(R.id.periodDigit2);
        
        ImageButton btnUpPeriod1 = periodDigit1View.findViewById(R.id.btnUp);
        ImageButton btnDownPeriod1 = periodDigit1View.findViewById(R.id.btnDown);
        ImageButton btnUpPeriod2 = periodDigit2View.findViewById(R.id.btnUp);
        ImageButton btnDownPeriod2 = periodDigit2View.findViewById(R.id.btnDown);
        
        btnUpPeriod1.setEnabled(enabled);
        btnDownPeriod1.setEnabled(enabled);
        btnUpPeriod2.setEnabled(enabled);
        btnDownPeriod2.setEnabled(enabled);
        
        btnUpPeriod1.setAlpha(alpha);
        btnDownPeriod1.setAlpha(alpha);
        btnUpPeriod2.setAlpha(alpha);
        btnDownPeriod2.setAlpha(alpha);

        // Presets spinners (siempre habilitados)
        View[] presetViews = {
            findViewById(R.id.presetMinuteDigit1),
            findViewById(R.id.presetMinuteDigit2),
            findViewById(R.id.presetSecondDigit1),
            findViewById(R.id.presetSecondDigit2)
        };

        for (View view : presetViews) {
            ImageButton btnUp = view.findViewById(R.id.btnUp);
            ImageButton btnDown = view.findViewById(R.id.btnDown);
            btnUp.setEnabled(true);
            btnDown.setEnabled(true);
            btnUp.setAlpha(1.0f);
            btnDown.setAlpha(1.0f);
        }
    }

    private void setupTimeSpinner(View spinnerView) {
        ImageButton btnUp = spinnerView.findViewById(R.id.btnUp);
        ImageButton btnDown = spinnerView.findViewById(R.id.btnDown);
        TextView tvDigit = spinnerView.findViewById(R.id.tvDigit);
        
        tvDigit.setText("0");

        Animation slideUpOut = AnimationUtils.loadAnimation(this, R.anim.slide_up_out);
        Animation slideUpIn = AnimationUtils.loadAnimation(this, R.anim.slide_up_in);
        Animation slideDownOut = AnimationUtils.loadAnimation(this, R.anim.slide_down_out);
        Animation slideDownIn = AnimationUtils.loadAnimation(this, R.anim.slide_down_in);

        btnUp.setOnClickListener(v -> {
            int currentValue = Integer.parseInt(tvDigit.getText().toString());
            int newValue = (currentValue + 1) % 10;
            
            slideUpOut.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {}

                @Override
                public void onAnimationEnd(Animation animation) {
                    tvDigit.setText(String.valueOf(newValue));
                    tvDigit.startAnimation(slideUpIn);
                    
                    // Ya no enviamos actualización a Pantalla
                    // Solo actualizamos el tiempo si estamos en modo descendente y es parte del preset
                    if (!isAscending && (spinnerView.getId() == R.id.presetMinuteDigit1 || 
                                         spinnerView.getId() == R.id.presetMinuteDigit2 || 
                                         spinnerView.getId() == R.id.presetSecondDigit1 || 
                                         spinnerView.getId() == R.id.presetSecondDigit2)) {
                        updateDisplayTimeFromPreset();
                    }
                }

                @Override
                public void onAnimationRepeat(Animation animation) {}
            });
            
            tvDigit.startAnimation(slideUpOut);
        });

        btnDown.setOnClickListener(v -> {
            int currentValue = Integer.parseInt(tvDigit.getText().toString());
            int newValue = (currentValue - 1 + 10) % 10;
            
            slideDownOut.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {}

                @Override
                public void onAnimationEnd(Animation animation) {
                    tvDigit.setText(String.valueOf(newValue));
                    tvDigit.startAnimation(slideDownIn);
                    
                    // Ya no enviamos actualización a Pantalla
                    // Solo actualizamos el tiempo si estamos en modo descendente y es parte del preset
                    if (!isAscending && (spinnerView.getId() == R.id.presetMinuteDigit1 || 
                                         spinnerView.getId() == R.id.presetMinuteDigit2 || 
                                         spinnerView.getId() == R.id.presetSecondDigit1 || 
                                         spinnerView.getId() == R.id.presetSecondDigit2)) {
                        updateDisplayTimeFromPreset();
                    }
                }

                @Override
                public void onAnimationRepeat(Animation animation) {}
            });
            
            tvDigit.startAnimation(slideDownOut);
        });
    }

    private void updateMaxTime() {
        int minutes = Integer.parseInt(timeDigits[0].getText().toString()) * 10 +
                     Integer.parseInt(timeDigits[1].getText().toString());
        int seconds = Integer.parseInt(timeDigits[2].getText().toString()) * 10 +
                     Integer.parseInt(timeDigits[3].getText().toString());
        maxSeconds = minutes * 60 + seconds;
        currentSeconds = maxSeconds;
        updateDisplayTime();
    }

    private void startTimer() {
        if (!isRunning) {
            isRunning = true;
            if (isAscending) {
                // En modo ascendente, comenzar desde el valor establecido en el reloj
                int minutes = Integer.parseInt(timeDigits[0].getText().toString()) * 10 +
                             Integer.parseInt(timeDigits[1].getText().toString());
                int seconds = Integer.parseInt(timeDigits[2].getText().toString()) * 10 +
                             Integer.parseInt(timeDigits[3].getText().toString());
                currentSeconds = minutes * 60 + seconds;
                startTime = SystemClock.elapsedRealtime() - (currentSeconds * 1000L);
            } else {
                // En modo descendente, continuamos desde el tiempo restante
                startTime = SystemClock.elapsedRealtime() - ((maxSeconds - currentSeconds) * 1000L);
            }
            timerHandler.postDelayed(timerRunnable, 100);
        }
    }

    private void pauseTimer() {
        if (isRunning) {
            isRunning = false;
            timerHandler.removeCallbacks(timerRunnable);
        }
    }

    private void stopTimer() {
        isRunning = false;
        timerHandler.removeCallbacks(timerRunnable);
        currentSeconds = 0;
        periodDigits[0].setText("0");
        periodDigits[1].setText("0");
        for (TextView digit : timeDigits) {
            digit.setText("0");
        }
        maxSeconds = 0;
        updateDisplayTime();
    }

    private void updatePresetTime() {
        int minutes = Integer.parseInt(presetDigits[0].getText().toString()) * 10 +
                     Integer.parseInt(presetDigits[1].getText().toString());
        int seconds = Integer.parseInt(presetDigits[2].getText().toString()) * 10 +
                     Integer.parseInt(presetDigits[3].getText().toString());
        presetSeconds = minutes * 60 + seconds;
    }

    private void incrementPeriod() {
        int currentPeriod = Integer.parseInt(periodDigits[0].getText().toString()) * 10 +
                           Integer.parseInt(periodDigits[1].getText().toString());
        currentPeriod++;
        if (currentPeriod > 99) currentPeriod = 0;

        periodDigits[0].setText(String.valueOf(currentPeriod / 10));
        periodDigits[1].setText(String.valueOf(currentPeriod % 10));
    }

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) return;

            long currentTime = SystemClock.elapsedRealtime();
            long deltaTime = currentTime - startTime;
            
            // Convertir milisegundos a segundos
            int secondsElapsed = (int) (deltaTime / 1000);
            
            if (isAscending) {
                int previousSeconds = currentSeconds;
                currentSeconds = secondsElapsed;
                
                // Solo incrementar cuando pasamos por un múltiplo del preset
                if (presetSeconds > 0 && 
                    previousSeconds / presetSeconds != currentSeconds / presetSeconds && 
                    currentSeconds > 0) {
                    incrementPeriod();
                }
            } else {
                // En modo descendente, calcular el tiempo restante
                if (maxSeconds > 0) {
                    currentSeconds = maxSeconds - secondsElapsed;
                    
                    if (currentSeconds <= 0) {
                        // Incrementar periodo cuando llega a 0
                        incrementPeriod();
                        // Reiniciar el temporizador para el siguiente ciclo
                        startTime = SystemClock.elapsedRealtime();
                        currentSeconds = maxSeconds;
                    }
                }
            }

            // Actualizar UI
            handler.post(() -> {
                updateDisplayTime();
            });

            if (isRunning) {
                // Programar la próxima actualización con un intervalo fijo
                timerHandler.postDelayed(this, 100); // Actualizar cada 100ms para mayor suavidad
            }
        }
    };

    private void envioDatos(String comando, String... parametros) {
        if (!isConnected) return;
        
        new Thread(() -> {
            try {
                if (socket != null && socket.isConnected() && writer != null) {
                    StringBuilder mensaje = new StringBuilder(comando);
                    if (parametros != null && parametros.length > 0) {
                        mensaje.append("|").append(String.join(",", parametros));
                    }
                    writer.println(mensaje.toString());
                    writer.flush();
                    Log.d(TAG, "Comando enviado: " + mensaje);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error al enviar comando: " + e.getMessage());
            }
        }).start();
    }

    private void actualizarEstadoConexion(boolean conectado) {
        Log.d(TAG, "Actualizando estado de conexión a: " + (conectado ? "Conectado" : "Desconectado"));
        isConnected = conectado;

        // Detener cualquier animación en curso
        tvConnected.clearAnimation();
        tvDisconnected.clearAnimation();

        // Cargar la animación de fade
        Animation fadeAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        fadeAnimation.setDuration(300); // Duración más corta para que sea más responsivo

        // Establecer los estados inmediatamente
        if (conectado) {
            tvConnected.setBackgroundResource(R.drawable.blue_gradient_background);
            tvConnected.setTextColor(getResources().getColor(android.R.color.white));
            tvDisconnected.setBackgroundColor(getResources().getColor(android.R.color.white));
            tvDisconnected.setTextColor(getResources().getColor(android.R.color.black));
        } else {
            tvConnected.setBackgroundColor(getResources().getColor(android.R.color.white));
            tvConnected.setTextColor(getResources().getColor(android.R.color.black));
            tvDisconnected.setBackgroundResource(R.drawable.blue_gradient_background);
            tvDisconnected.setTextColor(getResources().getColor(android.R.color.white));
        }

        // Aplicar la animación
        tvConnected.startAnimation(fadeAnimation);
        tvDisconnected.startAnimation(fadeAnimation);
    }

    private void conectarAlServidor() {
        try {
            handler.post(() -> {
                //Toast.makeText(this, "Intentando conectar al servidor...", Toast.LENGTH_SHORT).show();
                actualizarEstadoConexion(false);
            });
            
            if (socket == null || !socket.isConnected()) {
                socket = new Socket();
                socket.connect(new InetSocketAddress(serverIp, PUERTO), 5000);
                writer = new PrintWriter(socket.getOutputStream(), true);
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                
                // Si llegamos aquí, la conexión fue exitosa
                handler.post(() -> {
                    //Toast.makeText(this, "¡Conexión exitosa!", Toast.LENGTH_SHORT).show();
                    actualizarEstadoConexion(true);
                });
                Log.d(TAG, "Conexión establecida correctamente");
                
                // Enviar mensaje inicial
                writer.println("Hello from Cronometro");
                writer.flush();
                Log.d(TAG, "Mensaje inicial enviado");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error de conexión: " + e.getMessage());
            handler.post(() -> {
                //Toast.makeText(this, "Error de conexión: " + e.getMessage(), Toast.LENGTH_LONG).show();
                actualizarEstadoConexion(false);
            });
            desconectarDelServidor();
        }
    }

    private void desconectarDelServidor() {
        Log.d(TAG, "Iniciando desconexión");
        try {
            if (writer != null) {
                writer.close();
                writer = null;
                Log.d(TAG, "Writer cerrado");
            }
            if (reader != null) {
                reader.close();
                reader = null;
                Log.d(TAG, "Reader cerrado");
            }
            if (socket != null) {
                socket.close();
                socket = null;
                Log.d(TAG, "Socket cerrado");
            }
            handler.post(() -> {
                //Toast.makeText(this, "Desconectado del servidor", Toast.LENGTH_SHORT).show();
                actualizarEstadoConexion(false);
            });
        } catch (Exception e) {
            Log.e(TAG, "Error al desconectar: " + e.getMessage());
        }
    }

    private boolean isValidIpFormat(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;
        
        try {
            for (String part : parts) {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }
        
        return true;
    }

    private void mostrarDialogoIP() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_ip);
        
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setWindowAnimations(android.R.style.Animation_Dialog);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        }

        EditText editTextIp = dialog.findViewById(R.id.editTextIp);
        Button btnAceptar = dialog.findViewById(R.id.btnAceptar);
        Button btnCancelar = dialog.findViewById(R.id.btnCancelar);

        editTextIp.setText(serverIp);
        editTextIp.setSelectAllOnFocus(true);

        // Configurar el manejo de la tecla "enter" y el botón aceptar
        View.OnClickListener procesarIP = v -> {
            String newIp = editTextIp.getText().toString().trim();
            if (!newIp.isEmpty()) {
                if (isValidIpFormat(newIp)) {
                    // Ocultar el teclado
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null && editTextIp.getWindowToken() != null) {
                        imm.hideSoftInputFromWindow(editTextIp.getWindowToken(), 0);
                    }
                    
                    serverIp = newIp;
                    // Guardar la nueva IP
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit()
                        .putString(LAST_IP_KEY, serverIp)
                        .apply();
                        
                    tvIpAddress.setText("IP: " + serverIp);
                    
                    // Cerrar el diálogo y conectar
                    dialog.dismiss();
                    new Thread(this::conectarAlServidor).start();
                } else {
                    Toast.makeText(MainActivity.this, "Por favor ingrese una IP válida (formato: 0.0.0.0)", Toast.LENGTH_SHORT).show();
                    editTextIp.setSelection(0, editTextIp.length());
                }
            } else {
                Toast.makeText(MainActivity.this, "Por favor ingrese una IP", Toast.LENGTH_SHORT).show();
            }
        };

        editTextIp.setOnEditorActionListener((v, actionId, event) -> {
            if (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER) {
                procesarIP.onClick(v);
                return true;
            }
            return false;
        });

        btnAceptar.setOnClickListener(procesarIP);

        btnCancelar.setOnClickListener(v -> {
            // Ocultar el teclado antes de cerrar el diálogo
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && editTextIp.getWindowToken() != null) {
                imm.hideSoftInputFromWindow(editTextIp.getWindowToken(), 0);
            }
            dialog.dismiss();
        });

        // Mostrar el teclado después de un breve retraso
        handler.postDelayed(() -> {
            if (dialog.isShowing() && editTextIp != null) {
                editTextIp.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(editTextIp, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        }, 100);

        dialog.setOnDismissListener(dialogInterface -> {
            // Asegurarse de que el teclado esté oculto cuando se cierre el diálogo
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && editTextIp.getWindowToken() != null) {
                imm.hideSoftInputFromWindow(editTextIp.getWindowToken(), 0);
            }
        });

        dialog.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        desconectarDelServidor();
    }

    private void updateDisplayTime() {
        int minutes = currentSeconds / 60;
        int seconds = currentSeconds % 60;
        
        timeDigits[0].setText(String.valueOf(minutes / 10));
        timeDigits[1].setText(String.valueOf(minutes % 10));
        timeDigits[2].setText(String.valueOf(seconds / 10));
        timeDigits[3].setText(String.valueOf(seconds % 10));
    }

    private void updateDisplayTimeFromPreset() {
        // Copiar los valores del preset al reloj
        timeDigits[0].setText(presetDigits[0].getText());
        timeDigits[1].setText(presetDigits[1].getText());
        timeDigits[2].setText(presetDigits[2].getText());
        timeDigits[3].setText(presetDigits[3].getText());
        
        // Actualizar el tiempo máximo
        updateMaxTime();
    }

    private void setupDigitSpinner(int spinnerId) {
        View spinnerView = findViewById(spinnerId);
        ImageButton btnUp = spinnerView.findViewById(R.id.btnUp);
        ImageButton btnDown = spinnerView.findViewById(R.id.btnDown);
        TextView tvDigit = spinnerView.findViewById(R.id.tvDigit);
        
        tvDigit.setText("0");

        Animation slideUpOut = AnimationUtils.loadAnimation(this, R.anim.slide_up_out);
        Animation slideUpIn = AnimationUtils.loadAnimation(this, R.anim.slide_up_in);
        Animation slideDownOut = AnimationUtils.loadAnimation(this, R.anim.slide_down_out);
        Animation slideDownIn = AnimationUtils.loadAnimation(this, R.anim.slide_down_in);

        btnUp.setOnClickListener(v -> {
            int currentValue = Integer.parseInt(tvDigit.getText().toString());
            int newValue = (currentValue + 1) % 10;
            
            slideUpOut.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {}

                @Override
                public void onAnimationEnd(Animation animation) {
                    tvDigit.setText(String.valueOf(newValue));
                    tvDigit.startAnimation(slideUpIn);
                    
                    // Ya no enviamos actualización a Pantalla
                }

                @Override
                public void onAnimationRepeat(Animation animation) {}
            });
            
            tvDigit.startAnimation(slideUpOut);
        });

        btnDown.setOnClickListener(v -> {
            int currentValue = Integer.parseInt(tvDigit.getText().toString());
            int newValue = (currentValue - 1 + 10) % 10;
            
            slideDownOut.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {}

                @Override
                public void onAnimationEnd(Animation animation) {
                    tvDigit.setText(String.valueOf(newValue));
                    tvDigit.startAnimation(slideDownIn);
                    
                    // Ya no enviamos actualización a Pantalla
                }

                @Override
                public void onAnimationRepeat(Animation animation) {}
            });
            
            tvDigit.startAnimation(slideDownOut);
        });
    }
}