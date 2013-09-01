package net.phunehehe.foocam;

import android.app.Activity;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Spinner;

import java.io.File;
import java.io.FileNotFoundException;
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

    public static final String TAG = "HDR";
    private Button captureButton;
    private Camera camera;
    private Camera.Parameters parameters;
    private Queue<Integer> exposureValues;
    private FrameLayout preview;
    private List<String> choices;
    private List<List<Integer>> evLevels;
    private int evPosition;
    private View.OnClickListener captureButtonListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            captureButton.setClickable(false);
            parameters.setJpegQuality(100);
            exposureValues = (Queue) evLevels.get(evPosition);
            processQueue();
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
        captureButton.setText("" + exposureValue);
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

        int min = parameters.getMinExposureCompensation();
        int max = parameters.getMaxExposureCompensation();
        int mid = (max + min) / 2;
        int maxSteps = max - mid;
        float stepValue = parameters.getExposureCompensationStep();

        evLevels = new ArrayList<List<Integer>>(maxSteps);
        choices = new ArrayList<String>(maxSteps);

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

            evLevels.add((List) exposureIndexes);
            choices.add(description.insert(0, "EVs: ").toString());
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

        Spinner spinner = (Spinner) findViewById(R.id.ev_spinner);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, choices);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(evSpinnerListener);
    }
}