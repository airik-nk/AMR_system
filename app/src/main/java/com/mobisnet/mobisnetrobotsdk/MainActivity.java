package com.mobisnet.mobisnetrobotsdk;

import static android.service.controls.ControlsProviderService.TAG;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.media.MediaRecorder;
import android.widget.Toast;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;
import android.webkit.WebSettings;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.mobisnet.coreservice.AmrPosition;
import com.mobisnet.logsdk.LogFile;
import com.mobisnet.robotsdk.Robot;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.GET;

public class MainActivity extends AppCompatActivity {
    private Robot robot;
    private Button btnSlamMode, btnMapName, btnBattery;
    private Button btnGotoA, btnGotoB, btnGotoC, btnGotoHome, btnGotoCharge;
    private Button btnRunPython, btnApiCall, btnEmergencyStop, btnPoseRecognition, btnRecordAudio, btnUploadFile;
    private TextView txtPythonResult, txtApiResult, txtInspectionResult;
    private WebView webView;

    private MediaRecorder mediaRecorder;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static final int PICK_FILE_REQUEST = 300;
    private boolean permissionToRecordAccepted = false;
    private String[] permissions = {Manifest.permission.RECORD_AUDIO};

    private ExecutorService executorService;
    private AtomicBoolean isEmergencyStop = new AtomicBoolean(false);
    private AtomicBoolean isPoseRecognitionRunning = new AtomicBoolean(false);

    private File audioFile;

    @Override
    protected void onDestroy() {
        Robot.getInstance().stopService();
        stopRecording();
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // region 畫面
        btnSlamMode = findViewById(R.id.btn_slam_mode);
        btnSlamMode.setOnClickListener(btnSlamModeOnClick);

        btnMapName = findViewById(R.id.btn_map_name);
        btnMapName.setOnClickListener(btnMapNameOnClick);

        btnBattery = findViewById(R.id.btn_battery);
        btnBattery.setOnClickListener(btnBatteryOnClick);

        btnGotoA = findViewById(R.id.btn_goto_a);
        btnGotoA.setOnClickListener(btnGotoAOnClick);

        btnGotoB = findViewById(R.id.btn_goto_b);
        btnGotoB.setOnClickListener(btnGotoBOnClick);

        btnGotoC = findViewById(R.id.btn_goto_c);
        btnGotoC.setOnClickListener(btnGotoCOnClick);

        btnGotoHome = findViewById(R.id.btn_goto_home);
        btnGotoHome.setOnClickListener(btnGotoHomeOnClick);

        btnGotoCharge = findViewById(R.id.btn_goto_charge);
        btnGotoCharge.setOnClickListener(btnGotoChargeOnClick);

        btnRunPython = findViewById(R.id.btn_run_python);
        btnApiCall = findViewById(R.id.btn_api_call);
        btnEmergencyStop = findViewById(R.id.btn_emergency_stop);
        btnPoseRecognition = findViewById(R.id.btn_pose_recognition);
        btnRecordAudio = findViewById(R.id.btn_record_audio);
        btnUploadFile = findViewById(R.id.btn_upload_file);

        txtPythonResult = findViewById(R.id.txt_python_result);
        txtApiResult = findViewById(R.id.txt_api_result);
        txtInspectionResult = findViewById(R.id.txt_inspection_result);

        webView = findViewById(R.id.webview);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                Log.e("WebViewError", "Error: " + error.getDescription());
            }
        });
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);

        btnRunPython.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRunPythonDialog();
            }
        });

        btnApiCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new GradioApiRequestTask().execute("https://410f0ec089018ec1f9.gradio.live", "your_parameter"); // 替换为你的Gradio API URL和参数
            }
        });

        btnEmergencyStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isEmergencyStop.set(true);
                Log.d(TAG, "Emergency Stop Activated");
                // 模擬緊急停止，停止生成随机数
                isPoseRecognitionRunning.set(false);
            }
        });

        btnPoseRecognition.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                poseRecognition();
            }
        });

        btnRecordAudio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mediaRecorder == null) {
                    startRecording();
                } else {
                    stopRecording();
                }
            }
        });

        btnUploadFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                startActivityForResult(intent, PICK_FILE_REQUEST);
            }
        });

        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);

        executorService = Executors.newFixedThreadPool(2);

        // 運行機器人巡檢任務
        executorService.execute(new RobotInspectionTask());

        // 監聽緊急停止命令
        executorService.execute(new EmergencyStopListenerTask());

        // 建立log
        File fileLog = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS + "/logs/main"); //自動建立路徑
        LogFile mainLog = new LogFile(fileLog.getAbsolutePath());

        // region 初始化robot-sdk
        robot = Robot.getInstance();
        robot.init(this.getApplicationContext(), mainLog);
        robot.addOnRobotReadyListener(new Robot.OnRobotReadyListener() {
            @Override
            public void onReady() {
                Robot.getInstance().logInfo(TAG, "Robot is ready.");
                initListener(); // 定期上報監聽

                // region 設定定期上報(ms)
                Robot.getInstance().setNotify("GetRobotLocation", 2000); //開啟目前位置通知
                Robot.getInstance().setNotify("GetBatteryLevel", 5000); //開啟電池電量通知
                Robot.getInstance().setNotify("GetChargingStatus", 2000); //開啟充電狀態通知
                Robot.getInstance().setNotify("GetErrorSts", 2000); //開啟錯誤事件通知
                // endregion
            }

            @Override
            public void onFail() {
                Robot.getInstance().logDebug(TAG, "Robot is not ready.");
            }
        });
        // endregion
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_RECORD_AUDIO_PERMISSION:
                permissionToRecordAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
        }
        if (!permissionToRecordAccepted) finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            File selectedFile = new File(data.getData().getPath());
            uploadFile(selectedFile);
        }
    }

    private void startRecording() {
        if (mediaRecorder == null) {
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            audioFile = new File(getExternalCacheDir().getAbsolutePath(), "audiorecordtest.3gp");
            mediaRecorder.setOutputFile(audioFile.getAbsolutePath());
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

            try {
                mediaRecorder.prepare();
            } catch (IOException e) {
                Log.e(TAG, "prepare() failed");
            }

            try {
                mediaRecorder.start();
                Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "start() failed: " + e.getMessage());
                Toast.makeText(this, "Recording failed to start", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void stopRecording() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
                Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show();
                uploadAudio(audioFile);  // 在錄音結束後上傳音檔
            } catch (RuntimeException stopException) {
                Log.e(TAG, "stop() failed: " + stopException.getMessage());
                Toast.makeText(this, "Recording failed to stop", Toast.LENGTH_SHORT).show();
            } finally {
                mediaRecorder.release();
                mediaRecorder = null;
            }
        }
    }

    private void uploadAudio(File audioFile) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://192.168.1.81:5000")  // 替換成你的 API URL
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build();

        ApiService apiService = retrofit.create(ApiService.class);

        RequestBody requestFile = RequestBody.create(MediaType.parse("audio/3gp"), audioFile);
        MultipartBody.Part body = MultipartBody.Part.createFormData("audio", audioFile.getName(), requestFile);

        Call<ResponseBody> call = apiService.uploadAudio(body);
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, retrofit2.Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    try {
                        String result = response.body().string();
                        Toast.makeText(MainActivity.this, "Audio uploaded successfully", Toast.LENGTH_SHORT).show();
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Upload Result")
                                .setMessage(result)
                                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        // 按下 OK 按鈕後執行
                                        loadUrlInWebView("https://410f0ec089018ec1f9.gradio.live");
                                    }
                                })
                                .show();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "Audio upload failed", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Audio upload failed: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void uploadFile(File file) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://192.168.1.81:5000")  // 替換成你的 API URL
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build();

        ApiService apiService = retrofit.create(ApiService.class);

        RequestBody requestFile = RequestBody.create(MediaType.parse("application/octet-stream"), file);
        MultipartBody.Part body = MultipartBody.Part.createFormData("file", file.getName(), requestFile);

        Call<ResponseBody> call = apiService.uploadFile(body);
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, retrofit2.Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    try {
                        String result = response.body().string();
                        Toast.makeText(MainActivity.this, "File uploaded successfully", Toast.LENGTH_SHORT).show();
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Upload Result")
                                .setMessage(result)
                                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        // 按下 OK 按鈕後執行
                                        webView.setVisibility(View.VISIBLE);
                                        loadUrlInWebView("https://410f0ec089018ec1f9.gradio.live");
                                    }
                                })
                                .show();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "File upload failed", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(MainActivity.this, "File upload failed: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showRunPythonDialog() {
        new AlertDialog.Builder(this)
                .setTitle("執行 Python 腳本")
                .setMessage("確定要執行 Gradio 腳本嗎？")
                .setPositiveButton(android.R.string.ok, (dialog, which) -> runPythonScript())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void runPythonScript() {
        // 初始化 Python
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }

        // 獲取 Python 實例
        Python py = Python.getInstance();
        // 獲取 Python 模組，你的腳本名稱為 gradio
        PyObject pyObject = py.getModule("hello");

        // 啟動 Gradio 接口，假設你的 Gradio 啟動函數名稱為 launch
        pyObject.callAttr("hello");

        // 顯示已啟動 Gradio 的提示對話框
        new AlertDialog.Builder(this)
                .setTitle("Gradio 啟動")
                .setMessage("Gradio界面已在本地伺服器啟動。請訪問指定的URL以互動。")
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }


    private class GradioApiRequestTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            String response = "";
            try {
                URL url = new URL(params[0]);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; utf-8");
                connection.setRequestProperty("Accept", "application/json");
                connection.setDoOutput(true);

                JSONObject jsonInput = new JSONObject();
                jsonInput.put("data", new String[]{params[1]});

                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = jsonInput.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    String inputLine;
                    StringBuilder content = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
                        content.append(inputLine);
                    }
                    in.close();
                    response = content.toString();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            return response;
        }

        @Override
        protected void onPostExecute(String result) {
            txtApiResult.setText(result);
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle("API 結果")
                    .setMessage(result)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            // 按下 OK 按鈕後執行
                            loadUrlInWebView("https://410f0ec089018ec1f9.gradio.live");
                        }
                    });
            builder.create().show();
        }
    }

    private void poseRecognition() {
        // 停止隨機數生成
        isPoseRecognitionRunning.set(true);

        // 發送API呼叫進行姿態辨識
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://192.168.1.81:5000")  // 替換成你的 API URL
                .addConverterFactory(GsonConverterFactory.create())
                .client(new OkHttpClient())
                .build();

        ApiService apiService = retrofit.create(ApiService.class);

        Call<ResponseBody> call = apiService.getPoseRecognition();
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, retrofit2.Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    try {
                        String result = response.body().string();
                        if (result.equals("complete")) {
                            isPoseRecognitionRunning.set(false);
                            // 重新啟動隨機數生成
                            executorService.execute(new RobotInspectionTask());
                        }
                        txtApiResult.setText(result);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    txtApiResult.setText("Pose recognition failed");

                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("API 結果")
                            .setMessage("Pose recognition failed")
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                    isPoseRecognitionRunning.set(false);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                txtApiResult.setText("Pose recognition call failed: " + t.getMessage());

                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("API 結果")
                        .setMessage("Pose recognition call failed: " + t.getMessage())
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        });
    }

    private class RobotInspectionTask implements Runnable {
        @Override
        public void run() {
            Random random = new Random();
            while (!isEmergencyStop.get() && !isPoseRecognitionRunning.get()) {
                // 每秒生成一個包含 1-1000 隨機四個數字的陣列
                int[] randomNumbers = new int[4];
                for (int i = 0; i < 4; i++) {
                    randomNumbers[i] = random.nextInt(1000) + 1;
                }
                // 將隨機數顯示在 TextView 中
                runOnUiThread(() -> txtInspectionResult.setText("Random Numbers: " + randomNumbers[0] + ", " + randomNumbers[1] + ", " + randomNumbers[2] + ", " + randomNumbers[3]));
                Log.d(TAG, "Random Numbers: " + randomNumbers[0] + ", " + randomNumbers[1] + ", " + randomNumbers[2] + ", " + randomNumbers[3]);

                try {
                    Thread.sleep(1000); // 每秒生成一次
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.e(TAG, "RobotInspectionTask was interrupted", e);
                }
            }
            Log.d(TAG, "Robot inspection task has stopped.");
        }
    }

    private class EmergencyStopListenerTask implements Runnable {
        @Override
        public void run() {
            while (!isEmergencyStop.get()) {
                // 這裡可以放一些檢查邏輯，例如檢查某個標誌位
                try {
                    Thread.sleep(100); // 短暫休眠，避免佔用過多 CPU 資源
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.e(TAG, "EmergencyStopListenerTask was interrupted", e);
                }
            }
            // 處理緊急停止邏輯
            Log.d(TAG, "Emergency stop triggered.");
        }
    }

    public interface ApiService {
        @Multipart
        @POST("/upload_audio")
        Call<ResponseBody> uploadAudio(@Part MultipartBody.Part file);

        @Multipart
        @POST("/upload_file")
        Call<ResponseBody> uploadFile(@Part MultipartBody.Part file);

        @GET("/hello")
        Call<ResponseBody> getHelloMessage();

        @GET("/pose_recognition")
        Call<ResponseBody> getPoseRecognition();
    }

    private void loadUrlInWebView(String url) {
        runOnUiThread(() -> {
            webView.setVisibility(View.VISIBLE);
            webView.loadUrl(url);
        });
    }

    // region 畫面

    // region 質詢
    private final View.OnClickListener btnSlamModeOnClick = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Robot.getInstance().logInfo(TAG, "現在導航模式為" + Robot.getInstance().getSlamMode());
        }
    };

    private final View.OnClickListener btnMapNameOnClick = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Robot.getInstance().logInfo(TAG, "現在地圖名稱為" + Robot.getInstance().getMapName());
        }
    };

    private final View.OnClickListener btnBatteryOnClick = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Robot.getInstance().logInfo(TAG, "現在電量為" + Robot.getInstance().getBattery());
        }
    };
    // endregion

    // region 動作
    private final View.OnClickListener btnGotoAOnClick = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Robot.getInstance().goTo("A");
        }
    };

    private final View.OnClickListener btnGotoBOnClick = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Robot.getInstance().goTo("B");
        }
    };

    private final View.OnClickListener btnGotoCOnClick = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Robot.getInstance().goTo("C");
        }
    };

    private final View.OnClickListener btnGotoHomeOnClick = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Robot.getInstance().goToCharge("home");
        }
    };

    private final View.OnClickListener btnGotoChargeOnClick = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Robot.getInstance().goToCharge();
        }
    };
    // endregion

    // endregion

    private void initListener() {
        // 目前位置監聽器
        Robot.getInstance().addNotifyLocationRequestListener(new Robot.OnNotifyLocationRequestListener() {
            @Override
            public void onNotifyLocationRequest(AmrPosition amrPosition) {
                /*
                    機器人目前座標
                    X: amrPosition.getX()
                    Y: amrPosition.getY()
                    Orientation: amrPosition.getOrientation()
                */
            }
        });

        // 電池電量監聽器
        Robot.getInstance().addNotifyBatteryRequestListener(new Robot.OnNotifyBatteryRequestListener() {
            @Override
            public void onNotifyBatteryRequest(int level) {
                // 電池電量 level範圍: 0 ~ 20
            }
        });

        // 充電狀態監聽器
        Robot.getInstance().addNotifyChargeRequestListener(new Robot.OnNotifyChargeRequestListener() {
            @Override
            public void onNotifyChargeRequest(String chargeState) {
                if (chargeState.equals("True")) {
                    // 機器人充電中
                } else if (chargeState.equals("False")) {
                    // 機器人未充電
                }
            }
        });

        // 錯誤事件監聽器
        Robot.OnNotifyErrorRequestListener onNotifyErrorRequestListener = new Robot.OnNotifyErrorRequestListener() {
            @Override
            public void onNotifyErrorRequest(long code) {
                /*
                    錯誤類型代碼(code):
                    ----------------------------------------------
                    | 0x0000 | NoException         | 無錯誤       |
                    | 0x0001 | UltrasoundException | 超聲波錯誤    |
                    | 0x0002 | MotorException      | 馬達錯誤      |
                    | 0x0004 | ImuException        | IMU 錯誤     |
                    | 0x0008 | IrException         | IR 錯誤      |
                    | 0x0010 | LedException        | LED 錯誤     |
                    | 0x0020 | CommException       | SPI 通訊錯誤  |
                    | 0x0040 | BatteryException    | 電池錯誤      |
                    | 0x0080 | FallException       | 防墜錯誤      |
                    | 0x0100 | LidarException      | 雷達錯誤      |
                    | 0x0200 | Unusable            | 暫時保留的代碼 |
                    | 0x0400 | SlamException       | SLAM 錯誤    |
                    | 0x0800 | CamException        | 相機錯誤      |
                    ----------------------------------------------
                 */
            }
        };
    }
}
