package pl.edu.pwr.lab3.i242571;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;

import android.graphics.Rect;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;
import com.google.mlkit.vision.objects.DetectedObject;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;


import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity implements ImageAnalysis.Analyzer {
    static int pickPhotoRequestCode = 500;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    ProcessCameraProvider cameraProvider;
    Button imgButton;
    Button cameraButton;
    ImageView imageView;
    Bitmap lastImage = null;
    PreviewView previewView;
    Preview preview;
    TextView tagsTv;
    ImageProxy imageProxy;
    boolean isTagging = false;
    boolean isObjectDetecting = false;
    boolean isTextDetecting = false;

    LinkedList<View> viewList;
    boolean tagging = true;
    boolean cameraFeed = true;
    private ImageAnalysis imageAnalysis;
    private ConstraintLayout constraintLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //make app ask for permissions
        viewList = new LinkedList<View>();
        imgButton = findViewById(R.id.imgButton);
        cameraButton = findViewById(R.id.cameraButton);
        constraintLayout = findViewById(R.id.parent_layout);
        imageView = findViewById(R.id.imageView);
        previewView = findViewById(R.id.previewView);
        tagsTv = findViewById(R.id.tagsTv);
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try{
                cameraProvider = cameraProviderFuture.get();
                startCameraX();
            }catch (ExecutionException e){
                Log.wtf("LAB", e);
            }catch (InterruptedException e){
                Log.wtf("LAB", e);
            }
        }, getExecutor());

        imgButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switchView(false);
                pickImage();
            }
        });
        cameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switchView(true);
            }
        });
    }

    private Executor getExecutor() {
        return ContextCompat.getMainExecutor(this);
    }

    private void startCameraX(){
        cameraProvider.unbindAll();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        preview = new Preview.Builder().build();

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(getExecutor(), this);

        cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageAnalysis);
    }

    private void stopCameraX(){
        cameraProvider.unbindAll();
        isTextDetecting = false;
        isTagging = false;
        isObjectDetecting = false;
        imageProxy.close();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    private void switchView(boolean camera){
        stopCameraX();
        cameraFeed = camera;
        if(camera){
            imageView.setVisibility(View.INVISIBLE);
            previewView.setVisibility(View.VISIBLE);
            startCameraX();
        }else{
            previewView.setVisibility(View.INVISIBLE);
            imageView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()){
            case R.id.text_menu:
                tagging = false;
                tagsTv.setText("");
                if(!cameraFeed){
                    if(lastImage != null){
                        setFieldsFromImage(lastImage);
                    }
                }
                return true;
            case R.id.object_menu:
                tagging = true;
                tagsTv.setText("");
                if(!cameraFeed){
                    if(lastImage != null){
                        setFieldsFromImage(lastImage);
                    }
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }


    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(intent, pickPhotoRequestCode);
    }

    public void setFieldsFromImage(Bitmap bitmap) {
        cleanPreviewsInput();
        imageView.setImageBitmap(bitmap);
        String log = String.valueOf(bitmap.getHeight()) + " " +  String.valueOf(bitmap.getWidth()) + " " + String.valueOf(imageView.getHeight()) + " " +  String.valueOf(imageView.getWidth());
        Log.wtf("LAB", log);
        lastImage = bitmap;
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        if(tagging){
            processImageObjectDetection(image);
            processImageTagging(image);
        }
        else{
            processImageText(image);
        }
    }

    public void setFieldsFromData(Intent data) {
        Uri imageSelected = data.getData();
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageSelected);
            setFieldsFromImage(bitmap);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if(resultCode == MainActivity.RESULT_OK){
            if(requestCode == pickPhotoRequestCode){
                setFieldsFromData(data);
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void cleanPreviewsInput(){
        tagsTv.setText("");
        cleanPreviewsBoxes();
    }

    private void cleanPreviewsBoxes(){
        for (View v : viewList){
            constraintLayout.removeView(v);
        }
    }

    public Rect scaleRectangle(Rect rect, int height, int width){
        float scaleHeight;
        float scaleWidth;
        int left;
        int top;
        if (cameraFeed){
            scaleHeight = (float) height / previewView.getHeight();
            scaleWidth = (float) width / previewView.getWidth();
            left = (int) previewView.getLeft();
            top = (int) previewView.getTop();
        }else{
            scaleHeight = (float) height/ imageView.getHeight();
            scaleWidth = (float) width / imageView.getWidth();
            left = (int) imageView.getLeft();
            top = (int) imageView.getTop();
        }
        rect.set((int) (rect.left / scaleWidth) + left, (int) (rect.top / scaleHeight) + top, (int) (rect.right / scaleWidth) + (int) (left), (int) (rect.bottom / scaleHeight) + (int) (top));
        return rect;
    }

    public void processImageObjectDetection(InputImage image){
        ObjectDetectorOptions options;
        if(cameraFeed){
            options = new ObjectDetectorOptions.Builder()
                            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                            .enableMultipleObjects()
                            .enableClassification()  // Optional
                            .build();
        }else{
            options =
                new ObjectDetectorOptions.Builder()
                        .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                        .enableMultipleObjects()
                        .enableClassification()  // Optional
                        .build();
        }
        ObjectDetector objectDetector = ObjectDetection.getClient(options);

        objectDetector.process(image)
                .addOnSuccessListener(
                        new OnSuccessListener<List<DetectedObject>>() {
                            @Override
                            public void onSuccess(List<DetectedObject> detectedObjects) {
                                cleanPreviewsBoxes();
                                for (DetectedObject obj : detectedObjects) {
                                    Rect rect = obj.getBoundingBox();
                                    if(image.getRotationDegrees() % 180 == 90){
                                        scaleRectangle(rect, image.getWidth(), image.getHeight());
                                    }else{
                                        scaleRectangle(rect, image.getHeight(), image.getWidth());
                                    }

                                    Draw draw = new Draw(MainActivity.this, rect, "");
                                    viewList.add(draw);
                                    MainActivity.this.constraintLayout.addView(draw);
                                }
                                isObjectDetecting = false;
                                closeProxy();
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.wtf("LAB", e);
                                isObjectDetecting = false;
                                closeProxy();
                            }
                        });
    }

    public void processImageTagging(InputImage image){
        ImageLabeler labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS);
        labeler.process(image)
                .addOnSuccessListener(new OnSuccessListener<List<ImageLabel>>() {
                    @Override
                    public void onSuccess(List<ImageLabel> labels) {
                        StringBuilder sb = new StringBuilder();
                        for (ImageLabel label : labels){
                            if(label.getConfidence() > 0.7){
                                sb.append(label.getText());
                                sb.append(" ");
                            }
                        }
                        tagsTv.setText(sb.toString());
                        isTagging = false;
                        closeProxy();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.wtf("LAB", e);
                        isTagging = false;
                        closeProxy();
                    }
                });

    }

    public void processImageText(InputImage image){
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        Task<Text> result =
                recognizer.process(image)
                        .addOnSuccessListener(new OnSuccessListener<Text>() {
                            @Override
                            public void onSuccess(Text visionText) {
                                tagsTv.setText(visionText.getText());
                                isTagging = false;
                                closeProxy();
                            }
                        })
                        .addOnFailureListener(
                                new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        Log.wtf("LAB", e);
                                        isTagging = false;
                                        closeProxy();
                                    }
                                });
    }

    public void closeProxy(){
        if(!isTagging && !isObjectDetecting && !isTextDetecting){
            imageProxy.close();
        }
    }

    @Override
    public void analyze(@NonNull ImageProxy imageProxy) {
        this.imageProxy = imageProxy;
        Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            InputImage image =
                    InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
            if(tagging){
                isObjectDetecting = true;
                processImageObjectDetection(image);
                isTagging = true;
                processImageTagging(image);
            }else{
                isTextDetecting = true;
                processImageText(image);
            }
        }
    }
}