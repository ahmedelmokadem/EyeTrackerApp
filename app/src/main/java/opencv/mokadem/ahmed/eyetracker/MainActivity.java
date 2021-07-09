package opencv.mokadem.ahmed.eyetracker;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.nfc.Tag;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class MainActivity extends Activity implements CameraBridgeViewBase.CvCameraViewListener2 {
    /* general Variables here */
    public static String TAG = "Moka";

    CameraBridgeViewBase mOpenCvCameraView;
    Mat mGray;
    Mat mRgba;
    Mat mZoomWindow;
    Mat mZoomWindow2;
    Rect ROIinEyeRect = null;
    boolean eyeClosed = true ;

    // files
    private File mCascadeFile;
    // Classifiers
    private CascadeClassifier mDetectorFace;
    private CascadeClassifier mDetectorEye;

    // variables
    private float mRelativeFaceSize = 0.2f;
    private int mAbsoluteFaceSize = 0;
    double xCenter = -1;
    double yCenter = -1;

    Rect faceToProcess;
    Rect eyeCompined = null;


    private static final int offset = 17;
    Rect accurate;
    Point EyeCenter;

    int countstop = 0 ;
    int countstart=0;
    int statue = 0;

    /* Bluetooth variables */
    private BluetoothAdapter btAdapter = null;
    private BluetoothSocket btSocket = null;
    private OutputStream outStream = null;


    // UUID service - This is the type of Bluetooth device that the BT module is
    // It is very likely yours will be the same, if not google UUID for your manufacturer
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    TextView tvBTStatus;


    /* creating the LoaderCallback */
    BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            /* check for the loader call back status */
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                    try {
                        /* load classifiers files */
                        /* load face Classifier */
                        InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
                        File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
                        mCascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
                        FileOutputStream os = new FileOutputStream(mCascadeFile);

                        byte[] buffer = new byte[4096]; // ntfs formate
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                        is.close();
                        os.close();

                        InputStream iser = getResources().openRawResource(
                                R.raw.haarcascade_lefteye_2splits);
                        File cascadeDirER = getDir("cascadeER",
                                Context.MODE_PRIVATE);
                        File cascadeFileER = new File(cascadeDirER,
                                "haarcascade_lefteye_2splits.xml");
                        FileOutputStream oser = new FileOutputStream(cascadeFileER);

                        byte[] bufferER = new byte[4096];
                        int bytesReadER;
                        while ((bytesReadER = iser.read(bufferER)) != -1) {
                            oser.write(bufferER, 0, bytesReadER);
                        }
                        iser.close();
                        oser.close();

                        mDetectorFace = new CascadeClassifier(mCascadeFile.getAbsolutePath());
                        if (mDetectorFace.empty()) {
                            Log.e(TAG, "Failed to load cascade classifier");
                            mDetectorFace = null; // o check on it later
                        } else {
                            Log.e(TAG, "Loaded cascade classifier from "
                                    + mCascadeFile.getAbsolutePath());
                        }


                        mDetectorEye = new CascadeClassifier(cascadeFileER.getAbsolutePath());
                        if (mDetectorEye.empty()) {
                            Log.e(TAG, "Failed to load cascade classifier");
                            mDetectorEye = null;
                        } else {
                            Log.e(TAG, "Loaded cascade classifier from "
                                    + mCascadeFile.getAbsolutePath());
                        }

                        cascadeDir.delete();

                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                        Log.e(TAG, "Failed to load cascade (FileNotFoundException). Exception thrown: " + e);

                    } catch (IOException e) {
                        e.printStackTrace();

                        Log.e(TAG, "Failed to load cascade (IOException). Exception thrown: " + e);
                    }

                    mOpenCvCameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_FRONT);
                    mOpenCvCameraView.enableFpsMeter();
                    mOpenCvCameraView.enableView();

            }
            super.onManagerConnected(status);
        }
    };

    static {
        if (OpenCVLoader.initDebug()) {
            Log.e(TAG, "opencv is loaded successfully");
        } else {
            Log.e(TAG, "opencv is not loaded successfully");
        }

        System.loadLibrary("MyLibs");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE); // hide the application action bar
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); // keep screen on
        setContentView(R.layout.activity_main);
        /* check for camera permission */
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 1);
        }

        if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_ADMIN}, 2);
        }

        tvBTStatus = (TextView) findViewById(R.id.tvBTStaus);
        //getting the bluetooth adapter value and calling checkBTstate function
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        isBTSupported();
        connectToBTDevice();

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.fd_activity_surface_view);
        mOpenCvCameraView.setCvCameraViewListener(this);

        //sendData('R');
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        mGray = new Mat();
        mRgba = new Mat();
    }

    @Override
    public void onCameraViewStopped() {
        if (mGray != null) mGray.release();
        if (mRgba != null) mRgba.release();
        if (mZoomWindow != null) mZoomWindow.release();
        if (mZoomWindow2 != null) mZoomWindow2.release();

    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();
        /* note that nothing will appears without adding the Loadercallback */
        if (mAbsoluteFaceSize == 0) { // resize the face to be faster to process
            int height = mGray.rows();
            if (Math.round(height * mRelativeFaceSize) > 0) {
                mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
            }
        }

        if (mZoomWindow == null || mZoomWindow2 == null)
            CreateAuxiliaryMats();
        //Todo : use zoomwindow to make process faster // 9 .. 12
        /* create faces rectangles */
        MatOfRect faces = new MatOfRect();

        if (mDetectorFace != null)
            mDetectorFace.detectMultiScale(mGray, faces, 1.2, 2, 2, new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());
        //  Log.e(TAG,"mGray specs "+ mGray.);
        Rect[] facesArray = faces.toArray();
        if (facesArray.length == 0)
            sendData('S');
        for (Rect face : facesArray) {
            faceToProcess = face;
            Core.rectangle(mRgba, face.tl(), face.br(), new Scalar(0, 255, 0, 255), 3);
            xCenter = (face.x + face.width + face.x) / 2;
            yCenter = (face.y + face.height + face.y) / 2;
            Point center = new Point(xCenter, yCenter);
            Core.circle(mRgba, center, 10, new Scalar(255, 0, 0, 255), 3);
            Core.putText(mRgba, "[" + center.x + "," + center.y + "]",
                    new Point(center.x + 20, center.y + 20),
                    Core.FONT_HERSHEY_SIMPLEX, 0.7, new Scalar(255, 255, 255,
                            255));
            //  Log.e(TAG,"face specs "+ face.tl() + "and br = "+face.br());

            Mat ROI = new Mat(mGray, face);
            MatOfRect eyes = new MatOfRect();
            int eyeSize = 0;
            if (Math.round(ROI.rows() * mRelativeFaceSize) > 0)
                eyeSize = Math.round(ROI.rows() * mRelativeFaceSize);

            Log.e(TAG, "Eye Size = " + Integer.toString(eyeSize));
            if (mDetectorEye != null)
                mDetectorEye.detectMultiScale(ROI, eyes, 1.2, 3, 2, new Size(eyeSize, eyeSize), new Size());
            Rect[] eyesArray = eyes.toArray();
            if (eyesArray.length == 0)
                sendData('S');
            List<Integer> xArray = new ArrayList<Integer>();
            getRestectedArea(eyesArray);
            for (Rect eye : eyesArray) {
                xArray.add((int) eye.tl().x);
            }
            Rect eye = null;
            if (xArray.size() > 1) {
                eye = eyesArray[xArray.indexOf(Collections.max(xArray))];
            } else if (xArray.size() > 0) {
                eye = eyesArray[0];
            }

            if (eye != null) {
                Point tl = new Point();
                tl.x = eye.x + face.x;
                tl.y = eye.y + face.y;
                Point br = new Point();
                br.x = eye.x + face.x + eye.width;
                br.y = eye.y + face.y + eye.height;

                Point eyeCenter = new Point();
                eyeCenter.x = tl.x + eye.width / 2;
                eyeCenter.y = tl.y + eye.height / 2;

                if (eyeCompined.contains(eyeCenter)) {
                    Log.e(TAG, "eyeCompined contains eyeCenter");
                } else {
                    Log.e(TAG, "eyeCompined does not contains eyeCenter");
                }
                String nativeString = NativeClass.FindCenter(new Mat(mGray, face).getNativeObjAddr(), eye.x, eye.y, eye.width, eye.height);

                String[] pointsString = nativeString.split(",");
                Point eyePointRecognized;
                try {
                    int x = Integer.parseInt(pointsString[0].trim());
                    int y = Integer.parseInt(pointsString[1].trim());
                    eyePointRecognized = new Point(x + face.x + eye.x, y + face.y + eye.y);
                    Core.circle(mRgba, eyePointRecognized, 10, new Scalar(255, 0, 0, 255), 3);
                    char direction = getDrirection(eyeCenter, eyePointRecognized);
                    Core.putText(mRgba, "[" + direction + "]", new Point(eyeCenter.x + 20, eyeCenter.y+200), Core.FONT_HERSHEY_SIMPLEX, 3, new Scalar(255, 255, 255,
                            255));
                    sendData(direction);
                } catch (Exception e) {
                    Log.e(TAG, "Eye point Exception catched");
                    e.printStackTrace();
                }

            }
        }

        return mRgba;

    }

    void getRestectedArea(Rect[] eyes) {

        Point tlPoint;
        Point brPoint;
        if (eyes.length == 2) {
            if (eyes[0].tl().x < eyes[1].tl().x) // thats means
            {
                tlPoint = new Point(eyes[1].tl().x + faceToProcess.tl().x, eyes[1].tl().y + faceToProcess.tl().y);
                brPoint = new Point(eyes[1].br().x + faceToProcess.tl().x, eyes[1].br().y + faceToProcess.tl().y);
                eyeCompined = new Rect(tlPoint, brPoint);
                Core.rectangle(mRgba, eyeCompined.tl(), eyeCompined.br(), new Scalar(255, 0, 0, 255), 3);

            } else {
                tlPoint = new Point(eyes[0].tl().x + faceToProcess.tl().x, eyes[0].tl().y + faceToProcess.tl().y);
                brPoint = new Point(eyes[0].br().x + faceToProcess.tl().x, eyes[0].br().y + faceToProcess.tl().y);
                eyeCompined = new Rect(tlPoint, brPoint);
                Core.rectangle(mRgba, eyeCompined.tl(), eyeCompined.br(), new Scalar(255, 0, 0, 255), 3);
            }

        } else if (eyes.length == 1) {
            tlPoint = new Point(eyes[0].tl().x + faceToProcess.tl().x, eyes[0].tl().y + faceToProcess.tl().y);
            brPoint = new Point(eyes[0].br().x + faceToProcess.tl().x, eyes[0].br().y + faceToProcess.tl().y);
            eyeCompined = new Rect(tlPoint, brPoint);
            Core.rectangle(mRgba, eyeCompined.tl(), eyeCompined.br(), new Scalar(255, 0, 0, 255), 3);
        }

        if (eyeCompined != null) {
            accurate = new Rect(new Point(eyeCompined.tl().x + eyeCompined.width / 4.5, eyeCompined.tl().y + eyeCompined.height / 2), new Point(eyeCompined.br().x - eyeCompined.width / 6.5, eyeCompined.br().y - eyeCompined.height / 5));
            Core.rectangle(mRgba, accurate.tl(), accurate.br(), new Scalar(255, 255, 0, 255), 3);
            EyeCenter = new Point(accurate.tl().x + accurate.width / 2, accurate.tl().y + accurate.height / 2);
            Core.circle(mRgba, EyeCenter, 10, new Scalar(0, 0, 0, 255), 3);

            String features = NativeClass.FindFeatures(new Mat(mGray,accurate).getNativeObjAddr(),1);
            if (features.length() < 5)
                eyeClosed = true ;
            else
                eyeClosed = false ;
        }



    }

    int calculateDistance(Point p1, Point p2) {
        return (int) Math.sqrt(Math.pow(p1.x - p2.x, 2) + Math.pow(p1.y - p2.y, 2));
    }

    char getDrirection(Point calculatedCenter, Point detectedCenter) {

        char direction;
        // disance = calculateDistance(calculatedCenter, detectedCenter);
        int disance = (int) (EyeCenter.x - detectedCenter.x);
       // Core.putText(mRgba, "[ " + disance + " ]", calculatedCenter, Core.FONT_HERSHEY_SIMPLEX, 3, new Scalar(0, 255, 0, 255), 3);
        Core.line(mRgba, EyeCenter, detectedCenter, new Scalar(255, 0, 0, 255), 3);
        direction = 'S';

        if (disance < -5 && statue == 0) {
            //disance *= -1;
            direction = 'L';
            countstart=0;

        } else {
            if (disance > offset && statue == 0) {

                direction = 'R';
                countstart=0;


            } else {
                countstart+=1;

                Core.putText(mRgba, "[ " + countstart + " ]", calculatedCenter, Core.FONT_HERSHEY_SIMPLEX, 3, new Scalar(0, 0, 0, 255), 3);
                if(countstart>=10)
                {
                    statue=0;
                }
                if(countstart>5 && statue == 0)
                {
                    direction = 'F';
                  //  countstart=0;

                }

            }
        }

        if (eyeClosed)
        {
            countstop+=1;
             Core.putText(mRgba, "[ " + countstop + " ]", calculatedCenter, Core.FONT_HERSHEY_SIMPLEX, 3, new Scalar(0, 255, 0, 255), 3);
            direction = 'S';
            countstart=0;
            if(countstop>=15)
            {
                statue=1;    /////////// hold application
            }
            if(countstop>=19 )
            {
                finish();
                System.exit(0); ///////// close application
            }

        }
        else {
            countstop=0;
        }

        return direction;

    }


    @Override
    protected void onPause() {
        super.onPause();
        /* check if camera view is exsist and release it */
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
        tvBTStatus.setText("Disconnected");
        tvBTStatus.setBackgroundColor(Color.RED);

        if (btSocket!=null && btSocket.isConnected())
        {
            try {
                btSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        /* init the sync between camera and opencv */
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_13, this, mLoaderCallback);
       // connectToBTDevice();
    }

    private void CreateAuxiliaryMats() {
        if (mGray.empty())
            return;

        int rows = mGray.rows();
        int cols = mGray.cols();

        if (mZoomWindow == null) {
            mZoomWindow = mRgba.submat(rows / 2 + rows / 10, rows, cols / 2
                    + cols / 10, cols);
            mZoomWindow2 = mRgba.submat(0, rows / 2 - rows / 10, cols / 2
                    + cols / 10, cols);
        }

    }


    private void isBTSupported() {
        // Check device has Bluetooth and that it is turned on
        if (btAdapter == null) {
            Toast.makeText(getBaseContext(), "ERROR - Device does not support bluetooth", Toast.LENGTH_SHORT).show();
            finish();
        }
    }


    void connectToBTDevice()
{
    Intent i = getIntent();
    String newAddress = i.getStringExtra("macAddress");
    Log.e(TAG,"Connecting Address from main ="+newAddress);
    // Set up a pointer to the remote device using its address.
    Log.i("Data:","Mac main:  "+newAddress);
    if(newAddress != null) {
        BluetoothDevice device = btAdapter.getRemoteDevice(newAddress);
        Log.e(TAG,"After getremoteDevice");
        //Attempt to create a bluetooth socket for comms
        try {
            btSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
            Log.e(TAG,"trying to connect");
        } catch (IOException e1) {
            Toast.makeText(getBaseContext(), "ERROR - Could not create Bluetooth socket", Toast.LENGTH_SHORT).show();
            Log.e(TAG,"ERROR - Could not create Bluetooth socket");
        }

        // Establish the connection.
        try {
            Log.e(TAG,"ERROR - before connect");
            btSocket.connect();
            Log.e(TAG,"ERROR - after connect");
        } catch (IOException e) {
            try {
                Log.e(TAG,"ERROR - connect exception");
                e.printStackTrace();
                Toast.makeText(this,"Can not connect to BT device \nplease select another or try again",Toast.LENGTH_LONG).show();
                tvBTStatus.setText("Disconnected");
                tvBTStatus.setBackgroundColor(Color.RED);
                btSocket.close();        //If IO exception occurs attempt to close socket
                i = new Intent(getBaseContext(), ConnectActivity.class);
                startActivity(i);
            } catch (IOException e2) {
                Toast.makeText(getBaseContext(), "ERROR - Could not close Bluetooth socket", Toast.LENGTH_SHORT).show();
            }
        }

        // Create a data stream so we can talk to the device
        try {
            outStream = btSocket.getOutputStream();

        } catch (IOException e) {
            Toast.makeText(getBaseContext(), "ERROR - Could not create bluetooth outstream", Toast.LENGTH_SHORT).show();
        }
        tvBTStatus.setText("Connected");
        tvBTStatus.setBackgroundColor(Color.GREEN);
    }
    else
    {
         i = new Intent(getBaseContext(), ConnectActivity.class);
        startActivity(i);

    }
}

    public void sendData(char message) {
        // byte[] msgBuffer = message.getBytes();

        try {
            //attempt to place data on the outstream to the BT device
            //outStream.write(msgBuffer);
            outStream.write(message);
        } catch (Exception e) {
            //if the sending fails this is most likely because device is no longer there
            //Toast.makeText(getBaseContext(), "ERROR - Device not found", Toast.LENGTH_SHORT).show();
            tvBTStatus.setText("Disconnected");
            tvBTStatus.setBackgroundColor(Color.RED);
            // finish();
        }
    }

}

