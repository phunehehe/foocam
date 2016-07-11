package net.phunehehe.foocam;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.Toast;

import com.joshdholtz.sentry.Sentry;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements PictureCallback {

    private Button captureButton;
    private Camera camera;
    private Deque<Integer> exposureLevels;
    private FrameLayout preview;
    private float exposureCompensationStep;
    private float currentEv;
    private int numberOfStops;
    private int totalStops;
    private int midExposureLevel;
    private List<Integer> numberOfStopsList;
    private final OnItemSelectedListener numberOfStopsListener = new OnItemSelectedListener() {

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            numberOfStops = numberOfStopsList.get(position);
        }
    };
    private List<Camera.Size> resolutions;
    private final OnItemSelectedListener resolutionSpinnerListener = new OnItemSelectedListener() {

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            Camera.Size resolution = resolutions.get(position);
            Camera.Parameters parameters = camera.getParameters();
            parameters.setPictureSize(resolution.width, resolution.height);
            camera.setParameters(parameters);
        }
    };
    private List<String> focusModes;
    private final OnItemSelectedListener focusSpinnerListener = new OnItemSelectedListener() {

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            String focusMode = focusModes.get(position);
            Camera.Parameters parameters = camera.getParameters();
            parameters.setFocusMode(focusMode);
            camera.setParameters(parameters);
            if (focusMode.equals(Camera.Parameters.FOCUS_MODE_AUTO) ||
                    focusMode.equals(Camera.Parameters.FOCUS_MODE_MACRO)) {
                camera.autoFocus(null);
            }
        }
    };
    private List<String> resolutionDescriptions;
    private String timestamp;
    private final View.OnClickListener captureButtonListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            captureButton.setClickable(false);
            timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_", Locale.US).format(new Date());
            calculateExposureLevels(numberOfStops);
            processQueue();
        }
    };

    private void calculateExposureLevels(int stops) {
        // Minus one for the 0
        int step = (totalStops - 1) / (stops - 1);
        exposureLevels = new LinkedList<>();
        exposureLevels.addLast(midExposureLevel);
        for (int offset = step; exposureLevels.size() < stops; offset += step) {
            exposureLevels.addFirst(midExposureLevel - offset);
            exposureLevels.addLast((midExposureLevel + offset));
        }
    }

    /**
     * Create a File for saving an image
     */
    private File getOutputMediaFile(float ev) throws IOException {

        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            throw new IOException(getString(R.string.media_not_mounted));
        }

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DCIM), getString(R.string.app_name));

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                throw new IOException(format(R.string.cannot_create_dir, mediaStorageDir));
            }
        }

        // Create a media file name
        return new File(String.format(Locale.US, "%s%s%s%.1f.jpg", mediaStorageDir.getPath(), File.separator, timestamp, ev));
    }

    private String format(int resId, Object... args) {
        return String.format(getString(resId), args);
    }

    private boolean processQueue() {
        Integer exposureLevel = exposureLevels.pollFirst();
        if (exposureLevel == null) {
            return false;
        }
        currentEv = exposureLevel * exposureCompensationStep;
        captureButton.setText(format(R.string.capturing, currentEv));
        Camera.Parameters parameters = camera.getParameters();
        parameters.setExposureCompensation(exposureLevel);
        camera.setParameters(parameters);

        // This ugly sleep is needed to wait for the new parameters to take
        // effect. On a Xiaomi Mi 4 129ms seems to be the magic number.
        SystemClock.sleep(150);

        camera.takePicture(null, null, MainActivity.this);
        return true;
    }

    @Override
    public void onPictureTaken(byte[] data, Camera camera) {
        try {
            File pictureFile = getOutputMediaFile(currentEv);
            FileOutputStream fos = new FileOutputStream(pictureFile);
            fos.write(data);
            fos.close();
            MediaScannerConnection.scanFile(this, new String[]{pictureFile.toString()}, null, null);
        } catch (IOException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        MainActivity.this.camera.startPreview();
        if (!processQueue()) {
            captureButton.setText(R.string.capture);
            captureButton.setClickable(true);
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
        if (camera == null) {
            initializeCamera();
        }
        CameraPreview cameraPreview = new CameraPreview(this, camera);
        preview.addView(cameraPreview);
    }

    private void calculateCameraParameters() {

        Camera.Parameters parameters = camera.getParameters();
        focusModes = parameters.getSupportedFocusModes();
        exposureCompensationStep = parameters.getExposureCompensationStep();

        parameters.setJpegQuality(100);

        Point displaySize = new Point();
        getWindowManager().getDefaultDisplay().getSize(displaySize);
        float targetAspectRatio = (float) displaySize.x / displaySize.y;
        for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
            float aspectRatio = (float) size.width / size.height;
            if (Math.abs(aspectRatio - targetAspectRatio) < 0.1) {
                parameters.setPreviewSize(size.width, size.height);
                break;
            }
        }

        numberOfStopsList = new LinkedList<>();
        int minStop = parameters.getMinExposureCompensation();
        int maxStop = parameters.getMaxExposureCompensation();
        midExposureLevel = (maxStop + minStop) / 2;
        // Plus one for 0
        totalStops = maxStop - minStop + 1;
        if (totalStops >= 3) {
            for (int stops = 3; stops <= totalStops; stops += 2) {
                numberOfStopsList.add(stops);
            }
        }

        resolutions = parameters.getSupportedPictureSizes();
        resolutionDescriptions = new ArrayList<>(resolutions.size());
        for (Camera.Size size : resolutions) {
            resolutionDescriptions.add(size.width + " x " + size.height);
        }

        camera.setParameters(parameters);

        Spinner numberOfStopsSpinner = (Spinner) findViewById(R.id.number_of_stops_spinner);
        ArrayAdapter<Integer> numberOfStopsAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, numberOfStopsList);
        numberOfStopsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        numberOfStopsSpinner.setAdapter(numberOfStopsAdapter);
        numberOfStopsSpinner.setOnItemSelectedListener(numberOfStopsListener);

        Spinner resolutionSpinner = (Spinner) findViewById(R.id.resolution_spinner);
        ArrayAdapter<String> resolutionAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, resolutionDescriptions);
        resolutionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        resolutionSpinner.setAdapter(resolutionAdapter);
        resolutionSpinner.setOnItemSelectedListener(resolutionSpinnerListener);

        Spinner focusModeSpinner = (Spinner) findViewById(R.id.focus_mode_spinner);
        ArrayAdapter<String> focusModeAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, focusModes);
        focusModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        focusModeSpinner.setAdapter(focusModeAdapter);
        focusModeSpinner.setOnItemSelectedListener(focusSpinnerListener);
    }

    private void initializeCamera() {
        // TODO: This gets the first camera, not necessarily the best.
        // Maybe the app should let the user choose the camera.
        camera = Camera.open(0);
        calculateCameraParameters();
    }

    private boolean checkSelfPermissions(String[] permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestPermissions() {
        String[] permissions = new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
        };
        while (!checkSelfPermissions(permissions)) {
            ActivityCompat.requestPermissions(this, permissions, 0);
            SystemClock.sleep(100);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Sentry will look for uncaught exceptions from previous runs and send them
        Sentry.init(this, "https://6fed2ef75f5b4a3d839a2ea9c25c6c27:e5074392a02748c88cf23b97b8fbe036@app.getsentry.com/19878");

        setContentView(R.layout.activity_main);
        preview = (FrameLayout) findViewById(R.id.camera_preview);

        captureButton = (Button) findViewById(R.id.capture_button);
        captureButton.setOnClickListener(captureButtonListener);

        requestPermissions();
        initializeCamera();
    }
}
