package net.phunehehe.foocam;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.Camera;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import java.io.IOException;


@SuppressLint("ViewConstructor")
public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {

    private final SurfaceHolder surfaceHolder;
    private final Camera camera;

    public CameraPreview(Context context, Camera camera) {
        super(context);
        this.camera = camera;
        surfaceHolder = getHolder();
        surfaceHolder.addCallback(this);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        startPreview();
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        if (surfaceHolder.getSurface() == null) {
            return;
        }
        // Stop preview before making changes
        try {
            camera.stopPreview();
        } catch (Exception e) {
            // Ignore: tried to stop a non-existent preview
        }
        // Start preview with new settings
        startPreview();
    }

    private void startPreview() {
        try {
            camera.setPreviewDisplay(surfaceHolder);
            camera.startPreview();
        } catch (IOException e) {
            Context context = getContext();
            Toast.makeText(context, context.getString(R.string.something_is_wrong),
                    Toast.LENGTH_SHORT).show();
        }
    }
}