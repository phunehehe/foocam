package net.phunehehe.foocam;

import android.app.Activity;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class MainActivity extends Activity implements PictureCallback {

    private Button captureButton;
    private FrameLayout preview;
    private Camera camera;
    private Camera.Parameters parameters;
    private List<Camera.Size> resolutions;
    private List<String> resolutionDescriptions;
    private Queue<Integer> exposureValues;
    private List<String> evDescriptions;
    private List<List<Integer>> evLevels;
    private int evPosition;
    private View.OnClickListener captureButtonListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            captureButton.setClickable(false);
            exposureValues = new LinkedList<Integer>(evLevels.get(evPosition));
            processQueue();
        }
    };
    private AdapterView.OnItemSelectedListener resolutionSpinnerListener = new AdapterView.OnItemSelectedListener() {

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            Camera.Size resolution = resolutions.get(position);
            parameters.setPictureSize(resolution.width, resolution.height);
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    };
    private AdapterView.OnItemSelectedListener evSpinnerListener = new AdapterView.OnItemSelectedListener() {

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            evPosition = position;
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    };

    /**
     * Create a File for saving an image
     */
    private File getOutputMediaFile() throws IOException {

        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            throw new IOException(getString(R.string.media_not_mounted));
        }

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), getString(R.string.app_name));

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                throw new IOException(format(R.string.cannot_create_dir, mediaStorageDir));
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        return new File(mediaStorageDir.getPath() + File.separator + "IMG_" + timeStamp + ".jpg");
    }

    private String format(int resId, Object... args) {
        return String.format(getString(resId), args);
    }

    private boolean processQueue() {
        Integer exposureValue = exposureValues.poll();
        if (exposureValue == null) {
            return false;
        }
        captureButton.setText(format(R.string.capturing, exposureValue));
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
            sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.fromFile(pictureFile)));
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
            camera = Camera.open();
        }
        CameraPreview cameraPreview = new CameraPreview(this, camera);
        preview.addView(cameraPreview);
    }

    private void calculateCameraParameters() {

        parameters.setJpegQuality(100);

        resolutions = parameters.getSupportedPictureSizes();
        resolutionDescriptions = new ArrayList<String>(resolutions.size());
        for (Camera.Size size : resolutions) {
            resolutionDescriptions.add(size.width + " x " + size.height);
        }

        int min = parameters.getMinExposureCompensation();
        int max = parameters.getMaxExposureCompensation();
        int mid = (max + min) / 2;
        int maxSteps = max - mid;
        float stepValue = parameters.getExposureCompensationStep();

        evLevels = new ArrayList<List<Integer>>(maxSteps);
        evDescriptions = new ArrayList<String>(maxSteps);

        int step;
        int offset;
        int lowValue;
        int highValue;
        Deque<Integer> exposureIndexes;
        StringBuilder description;

        for (step = maxSteps; step >= 1; step--) {

            exposureIndexes = new LinkedList<Integer>();
            description = new StringBuilder();
            exposureIndexes.add(mid);
            description.append(mid);

            for (offset = step; offset <= maxSteps; offset += step) {

                lowValue = mid - offset;
                exposureIndexes.addFirst(lowValue);
                description.insert(0, ", ");
                description.insert(0, lowValue * stepValue);

                highValue = mid + offset;
                exposureIndexes.addLast(highValue);
                description.append(", ");
                description.append(highValue * stepValue);
            }

            evLevels.add((List<Integer>) exposureIndexes);
            evDescriptions.add(description.insert(0, "EVs: ").toString());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preview = (FrameLayout) findViewById(R.id.camera_preview);

        captureButton = (Button) findViewById(R.id.capture_button);
        captureButton.setOnClickListener(captureButtonListener);

        camera = Camera.open();
        parameters = camera.getParameters();
        calculateCameraParameters();

        Spinner evSpinner = (Spinner) findViewById(R.id.ev_spinner);
        ArrayAdapter<String> evAdapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, evDescriptions);
        evAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        evSpinner.setAdapter(evAdapter);
        evSpinner.setOnItemSelectedListener(evSpinnerListener);

        Spinner resolutionSpinner = (Spinner) findViewById(R.id.resolution_spinner);
        ArrayAdapter<String> resolutionAdapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, resolutionDescriptions);
        evAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        resolutionSpinner.setAdapter(resolutionAdapter);
        resolutionSpinner.setOnItemSelectedListener(resolutionSpinnerListener);
    }
}