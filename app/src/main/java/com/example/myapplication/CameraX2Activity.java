package com.example.myapplication;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.view.video.OnVideoSavedCallback;
import androidx.camera.view.video.OutputFileResults;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.myapplication.camera.CameraXView;
import com.example.myapplication.camera.QRCodeUtil;
import com.example.myapplication.decode.DecodeImgThread;
import com.example.myapplication.widget.CameraButton;

import java.io.File;
import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by dushu on 2021/2/2 .
 */
public class CameraX2Activity extends AppCompatActivity {
    private static final String TAG = "CameraX2Activity";

    private static final String FILENAME_FORMATE = "yyyy-MM-DD-HH-mm-ss";

    CameraXView cameraXView;
    CameraButton recordButton;
    private Button selectImage;
    private final int DEVICE_PHOTO_REQUEST = 3;

    ImageAnalysis myAnalyzer;

    private ExecutorService cameraExecutor;
    private String outputFilePath;

    @SuppressLint({"MissingPermission", "UnsafeExperimentalUsageError"})
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camerax2);

        cameraExecutor = Executors.newSingleThreadExecutor();
        cameraXView = findViewById(R.id.camera_x_view);
        myAnalyzer = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        myAnalyzer.setAnalyzer(cameraExecutor, new QRcodeAnalyzer());
        cameraXView.bindToLifecycle(this, myAnalyzer);

        recordButton = findViewById(R.id.record_button);// 拍照按钮
        selectImage = findViewById(R.id.selectImage);
        selectImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(intent, DEVICE_PHOTO_REQUEST);
            }
        });

        cameraXView.setCaptureMode(CameraXView.CaptureMode.IMAGE);

        recordButton.setOnClickListener(new CameraButton.OnClickListener() {
            @Override
            public void onClick() {
                takephoto();
            }
        });

        recordButton.setOnLongClickListener(new CameraButton.OnLongClickListener() {
            @Override
            public void onLongClick() {
                if (allPermissionGranted()
                        && (cameraXView.getCaptureMode() == CameraXView.CaptureMode.VIDEO
                        || cameraXView.getCaptureMode() == CameraXView.CaptureMode.MIXED)) {

                    takeVideo();
                } else {
                    ActivityCompat.requestPermissions(CameraX2Activity.this,
                            new String[]{Manifest.permission.RECORD_AUDIO}, 11);
                }
            }

            @Override
            public void onNoMinRecord(int currentTime) {

            }

            @Override
            public void onRecordFinishedListener() {
                if (cameraXView.isRecording()) {
                    cameraXView.stopRecording();
                }
            }
        });
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    private void takeVideo() {
        //创建视频保存的文件地址
        File file = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES).getAbsolutePath(),
                System.currentTimeMillis() + ".mp4");
        cameraXView.startRecording(file, cameraExecutor, new OnVideoSavedCallback() {


            @Override
            public void onVideoSaved(@NonNull OutputFileResults outputFileResults) {
                outputFilePath = file.getAbsolutePath();
                Log.e(TAG, "Video outputFilePath = " + outputFilePath);
                ContentValues values = new ContentValues();
                values.put(MediaStore.Video.Media.DATA, outputFilePath);
                getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
            }

            @Override
            public void onError(int videoCaptureError, @NonNull String message, @Nullable Throwable cause) {
                Log.i(TAG, message);
            }
        });

    }

    private void takephoto() {
        //1 设置要保存的路径和文件名字
        File photoFile = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "image-" + new SimpleDateFormat(FILENAME_FORMATE, Locale.CHINA).format(System.currentTimeMillis()) + ".jpg");
        //2 定义 拍摄imageCapture实例
        ImageCapture.OutputFileOptions outputFileOptions =
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();
        cameraXView.takePicture(outputFileOptions, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                //保存成功
                Uri savedUri = outputFileResults.getSavedUri();
                if (savedUri == null) {
                    savedUri = Uri.fromFile(photoFile);
                }
                outputFilePath = photoFile.getAbsolutePath();
                Log.e(TAG, "Photo outputFilePath = " + outputFilePath);
                try {
                    MediaStore.Images.Media.insertImage(getContentResolver(),
                            outputFilePath, photoFile.getName(), null);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://" + photoFile.getPath())));
//                onFileSaved(savedUri);
                Log.e(TAG, "Photo capture success");
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                //"保存失败"+
                Log.e(TAG, "Photo capture failed: " + exception.getMessage(), exception);
            }
        });

    }

    //将前面保存的文件添加到媒体中
    private void onFileSaved(Uri savedUri) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            sendBroadcast(new Intent(android.hardware.Camera.ACTION_NEW_PICTURE, savedUri));
        }
        String mimeTypeFromExtension = MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap
                .getFileExtensionFromUrl(savedUri.getPath()));
        MediaScannerConnection.scanFile(getApplicationContext(),
                new String[]{new File(savedUri.getPath()).getAbsolutePath()},
                new String[]{mimeTypeFromExtension}, new MediaScannerConnection.OnScanCompletedListener() {
                    @Override
                    public void onScanCompleted(String path, Uri uri) {
                        Log.d(TAG, "Image capture scanned into media store: $uri" + uri);
                    }
                });


//        PreviewActivity.start(this, outputFilePath, !takingPicture);启动预览页面？
    }


    private boolean allPermissionGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        //用户没有进行有效的设置操作，返回
        if (requestCode == AppCompatActivity.RESULT_CANCELED ){
            return;
        }
        if (DEVICE_PHOTO_REQUEST == requestCode){
            if (null != data) {
                Uri selectedImage = data.getData();
                String[] filePathColumns = {MediaStore.Images.Media.DATA};
                String imagePath;
                Cursor c = this.getContentResolver().query(selectedImage, filePathColumns, null, null, null);
                if (c != null) {
                    c.moveToFirst();
                    int columnIndex = c.getColumnIndex(filePathColumns[0]);
                    imagePath = c.getString(columnIndex);
                    c.close();

                    Toast.makeText(CameraX2Activity.this, "imagePath:" + imagePath, Toast.LENGTH_LONG).show();
//                    Bitmap bm = BitmapFactory.decodeFile(imagePath);
                    Bitmap bm = DecodeImgThread.getBitmap(imagePath, 400, 400);

                    if (bm != null) {
                        QRCodeUtil.parseQrCode(bm);
                    }
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}
