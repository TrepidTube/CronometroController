package com.example.cronometro;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.EditText;
import android.widget.Button;
import android.widget.Toast;
import java.io.PrintWriter;
import java.net.Socket;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.LinearLayout;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.EditorInfo;
import android.content.Context;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;

public class MainActivity extends AppCompatActivity {
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private boolean isRunning = false;
    private int currentSeconds = 0;
    private int maxSeconds = 0;
    private boolean isAscending = true; // Para controlar el modo
    private View minuteDigit1, minuteDigit2, secondDigit1, secondDigit2;
    private TextView[] timeDigits = new TextView[4]; // Para minutos y segundos
    private int presetSeconds = 0;
    private TextView[] presetDigits = new TextView[4]; // Para los dígitos de preset
    private TextView[] periodDigits = new TextView[2]; // Para los dígitos de periodo
    private TextView tvIpAddress;
    private TextView tvConnected;
    private TextView tvDisconnected;
    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;
    private String serverIp = "0.0.0.0"; // IP por defecto para conexión a la computadora
    private static final int PUERTO = 8080;
    private boolean isConnected = false;

    @Override
    protected void onCreate(Bundle savedInstancNeeState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

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
            updatePresetTime(); // Actualizar el tiempo de preset antes de iniciar
            updateMaxTime();
            startTimer();
            setTimeSpinnersEnabled(false);
        });

        btnPause.setOnClickListener(v -> {
            pauseTimer();
            // Mantener los botones deshabilitados en pausa
        });

        btnStop.setOnClickListener(v -> {
            stopTimer();
            // Rehabilitar los botones de los spinners
            setTimeSpinnersEnabled(true);
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
                
                // Configurar la animación de rotación
                ivModeArrow.animate()
                    .rotation(isAscending ? 0 : 180)
                    .setDuration(300)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();

                // Si hay un timer en curso, detenerlo y reiniciar
                if (isRunning) {
                    stopTimer();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // No es necesario manejar este caso
            }
        });

        // Configurar la conexión IP
        tvIpAddress = findViewById(R.id.tvIpAddress);
        tvConnected = findViewById(R.id.tvConnected);
        tvDisconnected = findViewById(R.id.tvDisconnected);

        tvIpAddress.setText("IP: " + serverIp);
        
        // Configurar el TextView de IP como botón
        tvIpAddress.setOnClickListener(v -> {
            if (!isConnected) {
                mostrarDialogoIP();
            } else {
                desconectarDelServidor();
            }
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

        // Periodos spinners (se deshabilitan/habilitan junto con el reloj)
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

        // Presets spinners (estos siempre están habilitados)
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
        
        // Establecer valor inicial en 0
        tvDigit.setText("0");

        // Cargar las animaciones
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
            // Solo inicializar los segundos si es un inicio nuevo (después de stop)
            if (currentSeconds == 0 && maxSeconds == 0) {
                if (isAscending) {
                    currentSeconds = 0; // En modo ascendente empezamos desde 0
                } else {
                    updateMaxTime(); // Actualizar maxSeconds con los valores actuales
                    currentSeconds = maxSeconds; // En modo descendente empezamos desde el máximo
                }
            }
            
            isRunning = true;
            updateDisplayTime();
            timerHandler.post(timerRunnable);
            setTimeSpinnersEnabled(false); // Deshabilitar spinners al iniciar
        }
    }

    private void pauseTimer() {
        isRunning = false;
        timerHandler.removeCallbacks(timerRunnable);
        // Habilitar los spinners cuando se pausa
        setTimeSpinnersEnabled(true);
    }

    private void stopTimer() {
        isRunning = false;
        timerHandler.removeCallbacks(timerRunnable);
        
        // Reiniciar el contador a 0 independientemente del modo
        currentSeconds = 0;
        
        // Reiniciar el periodo a 0
        periodDigits[0].setText("0");
        periodDigits[1].setText("0");
        
        // Reiniciar todos los dígitos del reloj a 0
        for (TextView digit : timeDigits) {
            digit.setText("0");
        }
        
        // Actualizar el maxSeconds a 0
        maxSeconds = 0;
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
            if (isRunning) {
                if (isAscending) {
                    currentSeconds++;
                    
                    if (presetSeconds > 0 && currentSeconds % presetSeconds == 0) {
                        incrementPeriod();
                    }
                    updateDisplayTime();
                    
                    timerHandler.postDelayed(this, 1000);
                } else {
                    // Modo temporizador (descendente)
                    if (currentSeconds > 0) {
                        currentSeconds--;
                        updateDisplayTime();
                        
                        timerHandler.postDelayed(this, 1000);
                    } else {
                        incrementPeriod();
                        // Reiniciar al tiempo máximo para el siguiente ciclo
                        currentSeconds = maxSeconds;
                        updateDisplayTime();
                        if (maxSeconds > 0) {
                            isRunning = true;
                            timerHandler.postDelayed(this, 1000);
                        } else {
                            isRunning = false;
                        }
                    }
                }
            }
        }
    };

    private void updateDisplayTime() {
        int minutes = currentSeconds / 60;
        int seconds = currentSeconds % 60;
        
        timeDigits[0].setText(String.valueOf(minutes / 10));
        timeDigits[1].setText(String.valueOf(minutes % 10));
        timeDigits[2].setText(String.valueOf(seconds / 10));
        timeDigits[3].setText(String.valueOf(seconds % 10));
        
        enviarDatosAlServidor();
    }

    private void setupDigitSpinner(int spinnerId) {
        View spinnerView = findViewById(spinnerId);
        ImageButton btnUp = spinnerView.findViewById(R.id.btnUp);
        ImageButton btnDown = spinnerView.findViewById(R.id.btnDown);
        TextView tvDigit = spinnerView.findViewById(R.id.tvDigit);
        
        // Establecer valor inicial en 0
        tvDigit.setText("0");

        // Cargar las animaciones
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
                }

                @Override
                public void onAnimationRepeat(Animation animation) {}
            });
            
            tvDigit.startAnimation(slideDownOut);
        });
    }

    private void conectarAlServidor() {
        // Mostrar mensaje de intento de conexión
        runOnUiThread(() -> {
            Toast.makeText(this, "Intentando conectar a " + serverIp + ":" + PUERTO, Toast.LENGTH_SHORT).show();
            tvConnected.setVisibility(View.GONE);
            tvDisconnected.setVisibility(View.VISIBLE);
        });
        
        // Realizar la conexión en un hilo separado
        new Thread(() -> {
            try {
                runOnUiThread(() -> Toast.makeText(this, "Creando socket...", Toast.LENGTH_SHORT).show());
                
                // Crear socket con timeout
                socket = new Socket();
                socket.setSoTimeout(5000); // Timeout de 5 segundos para operaciones de lectura
                
                runOnUiThread(() -> Toast.makeText(this, "Intentando conectar socket...", Toast.LENGTH_SHORT).show());
                
                // Intentar conectar con timeout de 5 segundos
                socket.connect(new InetSocketAddress(serverIp, PUERTO), 5000);
                
                runOnUiThread(() -> Toast.makeText(this, "Socket conectado, configurando streams...", Toast.LENGTH_SHORT).show());
                
                writer = new PrintWriter(socket.getOutputStream(), true);
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                
                // Enviar mensaje de prueba
                runOnUiThread(() -> Toast.makeText(this, "Enviando test de conexión...", Toast.LENGTH_SHORT).show());
                writer.println("TEST_CONNECTION");
                
                // Esperar respuesta del servidor
                String response = reader.readLine();
                
                runOnUiThread(() -> Toast.makeText(this, "Respuesta recibida: " + (response != null ? response : "null"), Toast.LENGTH_SHORT).show());
                
                if (response != null && response.equals("CONNECTION_OK")) {
                    isConnected = true;
                    runOnUiThread(() -> {
                        Toast.makeText(this, "¡Conectado al servidor!", Toast.LENGTH_SHORT).show();
                        tvConnected.setVisibility(View.VISIBLE);
                        tvDisconnected.setVisibility(View.GONE);
                    });

                    // Iniciar hilo para mantener la conexión viva y recibir respuestas
                    new Thread(this::manejarRespuestasServidor).start();
                } else {
                    throw new Exception("No se recibió confirmación del servidor. Respuesta: " + response);
                }
            } catch (Exception e) {
                final String errorMsg = e.getMessage();
                e.printStackTrace(); // Imprimir stack trace para debugging
                desconectarDelServidor();
                runOnUiThread(() -> {
                    String mensaje = "Error al conectar: " + errorMsg;
                    Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show();
                    tvConnected.setVisibility(View.GONE);
                    tvDisconnected.setVisibility(View.VISIBLE);
                });
            }
        }).start();
    }

    private void manejarRespuestasServidor() {
        try {
            runOnUiThread(() -> Toast.makeText(this, "Iniciando manejo de respuestas del servidor", Toast.LENGTH_SHORT).show());
            
            String response;
            while (isConnected && (response = reader.readLine()) != null) {
                final String respuestaFinal = response;
                runOnUiThread(() -> Toast.makeText(this, "Respuesta del servidor: " + respuestaFinal, Toast.LENGTH_SHORT).show());
                
                if (response.equals("DATA_OK")) {
                    // Datos recibidos correctamente
                    continue;
                } else if (response.equals("DATA_ERROR")) {
                    // Error en los datos
                    runOnUiThread(() -> Toast.makeText(this, "Error en los datos enviados", Toast.LENGTH_SHORT).show());
                    desconectarDelServidor();
                    break;
                }
            }
            
            if (isConnected) {
                runOnUiThread(() -> Toast.makeText(this, "Conexión cerrada por el servidor", Toast.LENGTH_SHORT).show());
                desconectarDelServidor();
            }
        } catch (Exception e) {
            e.printStackTrace();
            final String errorMsg = e.getMessage();
            runOnUiThread(() -> Toast.makeText(this, "Error en comunicación: " + errorMsg, Toast.LENGTH_SHORT).show());
            desconectarDelServidor();
        }
    }

    private void desconectarDelServidor() {
        try {
            if (writer != null) {
                // Enviar mensaje de desconexión
                if (isConnected) {
                    writer.println("DISCONNECT");
                }
                writer.close();
            }
            if (reader != null) {
                reader.close();
            }
            if (socket != null) {
                socket.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        isConnected = false;
        socket = null;
        writer = null;
        reader = null;
        
        runOnUiThread(() -> {
            Toast.makeText(this, "Desconectado del servidor", Toast.LENGTH_SHORT).show();
            tvConnected.setVisibility(View.GONE);
            tvDisconnected.setVisibility(View.VISIBLE);
        });
    }

    private void enviarDatosAlServidor() {
        if (isConnected && writer != null) {
            try {
                String periodo = periodDigits[0].getText().toString() + periodDigits[1].getText().toString();
                String minutos = timeDigits[0].getText().toString() + timeDigits[1].getText().toString();
                String segundos = timeDigits[2].getText().toString() + timeDigits[3].getText().toString();
                String reloj = minutos + ":" + segundos;
                
                writer.println(periodo + "|" + reloj);
            } catch (Exception e) {
                e.printStackTrace();
                desconectarDelServidor();
            }
        }
    }

    private void mostrarDialogoIP() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_ip);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        EditText editTextIp = dialog.findViewById(R.id.editTextIp);
        Button btnAceptar = dialog.findViewById(R.id.btnAceptar);
        Button btnCancelar = dialog.findViewById(R.id.btnCancelar);

        // Mostrar la IP actual
        editTextIp.setText(serverIp);

        btnAceptar.setOnClickListener(v -> {
            String newIp = editTextIp.getText().toString().trim();
            if (!newIp.isEmpty()) {
                serverIp = newIp;
                tvIpAddress.setText("IP: " + serverIp);
                new Thread(() -> conectarAlServidor()).start();
            }
            dialog.dismiss();
        });

        btnCancelar.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        desconectarDelServidor();
    }
}