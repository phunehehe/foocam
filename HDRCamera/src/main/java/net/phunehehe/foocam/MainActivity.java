package net.phunehehe.foocam;

import android.app.Activity;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.Queue;

public class MainActivity extends Activity implements PictureCallback {

    public static final String TAG = "HDR";
    private Camera camera;
    private Camera.Parameters parameters;
    private Queue<Integer> exposureValues;
    private FrameLayout preview;
    private View.OnClickListener captureButtonListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            parameters = camera.getParameters();
            exposureValues = new LinkedList<Integer>(Arrays.asList(
                    parameters.getMinExposureCompensation(),
                    0,
                    parameters.getMaxExposureCompensation()));
            processQueue();
        }
    };

    /**
     * Create a File for saving an image
     */
    private static File getOutputMediaFile() {

        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            throw new RuntimeException("External storage not available");
        }

        File mediaStorageDir = getMediaStorageDir();

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                throw new RuntimeException("Failed to create directory: " + mediaStorageDir);
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        return new File(mediaStorageDir.getPath() + File.separator + "IMG_" + timeStamp + ".jpg");
    }

    private static File getMediaStorageDir() {
        return new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "MyCameraApp");
    }

    private boolean processQueue() {
        Integer exposureValue = exposureValues.poll();
        if (exposureValue == null) {
            return false;
        }
        parameters.setExposureCompensation(exposureValue);
        camera.setParameters(parameters);
        camera.takePicture(null, null, MainActivity.this);
        return true;
    }

    @Override
    public void onPictureTaken(byte[] data, Camera camera) {
        try {
            File pictureFile = getOutputMediaFile();
            FileOutputStream fos = new FileOutputStream(pictureFile);
            fos.write(data);
            fos.close();
        } catch (FileNotFoundException e) {
            Log.d(TAG, "File not found: " + e.getMessage());
        } catch (IOException e) {
            Log.d(TAG, "Error accessing file: " + e.getMessage());
        } catch (RuntimeException e) {
            Log.d(TAG, "Error creating media file: " + e.getMessage());
        }
        MainActivity.this.camera.startPreview();
        if (!processQueue()) {
            sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED,
                    Uri.fromFile(getMediaStorageDir())));
        }
    }

    private void releaseCamera() {
        if (camera != null) {
            camera.release();
            camera = null;
        }
        preview.removeAllViews();
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseCamera();
    }

    @Override
    protected void onResume() {
        super.onResume();
        camera = Camera.open();
        CameraPreview cameraPreview = new CameraPreview(this, camera);
        preview.addView(cameraPreview);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        preview = (FrameLayout) findViewById(R.id.camera_preview);

        Button captureButton = (Button) findViewById(R.id.button_capture);
        captureButton.setOnClickListener(captureButtonListener);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
}
