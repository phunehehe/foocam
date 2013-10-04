package net.phunehehe.foocam;

import android.app.Activity;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.media.ExifInterface;
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

public class MainActivity extends Activity implements PictureCallback {

    private Camera camera;
    private Camera.Parameters parameters;
    private int totalStops;
    private int midExposureValue;
    private float exposureCompensationStep;
    private float currentEv;
    private List<Integer> numberOfStopsList;
    private List<Camera.Size> resolutions;
    private List<String> resolutionDescriptions;
    private Deque<Integer> exposureCompensationLevels;
    private Button captureButton;
    private FrameLayout preview;
    private View.OnClickListener captureButtonListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            captureButton.setClickable(false);
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
    private AdapterView.OnItemSelectedListener numberOfStopsListener = new AdapterView.OnItemSelectedListener() {

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            int numberOfStops = numberOfStopsList.get(position);
            // Minus one for the 0
            int stepsBetweenStops = (totalStops - 1) / (numberOfStops - 1);
            exposureCompensationLevels = new LinkedList<Integer>();
            exposureCompensationLevels.addLast(midExposureValue);
            for (int offset = stepsBetweenStops; exposureCompensationLevels.size() < numberOfStops;
                 offset += stepsBetweenStops) {
                // FIXME: This breaks even numberOfStops
                exposureCompensationLevels.addFirst(midExposureValue - offset);
                exposureCompensationLevels.addLast((midExposureValue + offset));
            }
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
        Integer exposureCompensation = exposureCompensationLevels.pollFirst();
        if (exposureCompensation == null) {
            return false;
        }
        currentEv = exposureCompensation * exposureCompensationStep;
        captureButton.setText(format(R.string.capturing, currentEv));
        parameters.setExposureCompensation(exposureCompensation);
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
            // FIXME: Somehow this breaks EV detection in Luminance HDR
            //ExifInterface exif = new ExifInterface(pictureFile.getAbsolutePath());
            //String apertureString = exif.getAttribute(ExifInterface.TAG_APERTURE);
            //float aperture = Float.parseFloat(apertureString);
            //double exposureTime = Math.pow(aperture, 2) / Math.pow(2, currentEv);
            //exif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, String.valueOf(exposureTime));
            //double shutterSpeedValue = Math.log(1 / exposureTime) / Math.log(2);
            //exif.setAttribute("ShutterSpeedValue", String.valueOf(shutterSpeedValue));
            //exif.saveAttributes();
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
        exposureCompensationStep = parameters.getExposureCompensationStep();

        resolutions = parameters.getSupportedPictureSizes();
        resolutionDescriptions = new ArrayList<String>(resolutions.size());
        for (Camera.Size size : resolutions) {
            resolutionDescriptions.add(size.width + " x " + size.height);
        }

        numberOfStopsList = new LinkedList<Integer>();
        int minStop = parameters.getMinExposureCompensation();
        int maxStop = parameters.getMaxExposureCompensation();
        midExposureValue = (maxStop + minStop) / 2;
        // Plus one for 0
        totalStops = maxStop - minStop + 1;
        if (totalStops >= 2) {
            for (int stops = 2; stops <= totalStops; stops++) {
                numberOfStopsList.add(stops);
            }
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

        Spinner numberOfStopsSpinner = (Spinner) findViewById(R.id.number_of_stops_spinner);
        ArrayAdapter<Integer> numberOfStopsAdapter = new ArrayAdapter<Integer>(
                this, android.R.layout.simple_spinner_item, numberOfStopsList);
        numberOfStopsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        numberOfStopsSpinner.setAdapter(numberOfStopsAdapter);
        numberOfStopsSpinner.setOnItemSelectedListener(numberOfStopsListener);

        Spinner resolutionSpinner = (Spinner) findViewById(R.id.resolution_spinner);
        ArrayAdapter<String> resolutionAdapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, resolutionDescriptions);
        resolutionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        resolutionSpinner.setAdapter(resolutionAdapter);
        resolutionSpinner.setOnItemSelectedListener(resolutionSpinnerListener);
    }
}
