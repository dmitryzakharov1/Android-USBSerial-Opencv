package com.example.dimon.myapplication2;

import android.support.v7.app.ActionBarActivity;
import android.app.Activity;
import android.app.PendingIntent;
import android.app.Service;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.WindowManager;

//import java.lang.*;
import android.graphics.Color;
import java.lang.ref.WeakReference;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Bundle;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import android.widget.TwoLineListItem;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.UnsupportedEncodingException;
import java.io.IOException;

//import com.hoho.android.usbserial.driver.UsbSerialPort;
//import com.hoho.android.usbserial.driver.UsbSerialDriver;
//import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.driver.*;
import com.hoho.android.usbserial.util.*;
//import com.hoho.android.usbserial.util.HexDump;
//import com.hoho.android.usbserial.util.SerialInputOutputManager;


import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.imgproc.*;



public class MainActivity extends Activity implements CvCameraViewListener2 {

    private final String TAG = MainActivity.class.getSimpleName();

    private Mat mRgba;
    private Mat mGray;
    private CameraBridgeViewBase mOpenCvCameraView;

//serial
    private UsbManager usbManager;
    private UsbSerialDriver device;
    private static UsbSerialPort sPort = null;
    private SerialInputOutputManager mSerialIoManager;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();


    private UsbManager mUsbManager;
    private ListView mListView;

    private static final int MESSAGE_REFRESH = 101;
    private static final long REFRESH_TIMEOUT_MILLIS = 5000;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_REFRESH:
                    refreshDeviceList();
                    mHandler.sendEmptyMessageDelayed(MESSAGE_REFRESH, REFRESH_TIMEOUT_MILLIS);
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }

    };

    private List<UsbSerialPort> mEntries = new ArrayList<UsbSerialPort>();
    private ArrayAdapter<UsbSerialPort> mAdapter;

    private void refreshDeviceList() {


        new AsyncTask<Void, Void, List<UsbSerialPort>>() {
            @Override
            protected List<UsbSerialPort> doInBackground(Void... params) {
                Log.d(TAG, "Refreshing device list ...");
                SystemClock.sleep(1000);

                final List<UsbSerialDriver> drivers =
                        UsbSerialProber.getDefaultProber().findAllDrivers(mUsbManager);

                final List<UsbSerialPort> result = new ArrayList<UsbSerialPort>();
                for (final UsbSerialDriver driver : drivers) {
                    final List<UsbSerialPort> ports = driver.getPorts();
                    Log.d(TAG, String.format("+ %s: %s port%s",
                            driver, Integer.valueOf(ports.size()), ports.size() == 1 ? "" : "s"));
                    result.addAll(ports);
                }

                return result;
            }

            @Override
            protected void onPostExecute(List<UsbSerialPort> result) {
                mEntries.clear();
                mEntries.addAll(result);
                mAdapter.notifyDataSetChanged();

                Log.d(TAG, "Done refreshing, " + mEntries.size() + " entries found.");
            }

        }.execute((Void) null);
    }

    private void showConsoleActivity(UsbSerialPort port) {
        SerialConsoleActivity.show(this, port);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate( savedInstanceState );
        getWindow().addFlags( WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON );
        setContentView(R.layout.activity_main);
        mOpenCvCameraView = (CameraBridgeViewBase) findViewById ( R.id.tutorial1_activity_java_surface_view );
        mOpenCvCameraView.setVisibility( SurfaceView.VISIBLE );
        mOpenCvCameraView.setCvCameraViewListener(this);

        //usbserial
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        mListView = (ListView) findViewById(R.id.deviceList);


        mAdapter = new ArrayAdapter<UsbSerialPort>(this,
                android.R.layout.simple_expandable_list_item_2, mEntries) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                final TwoLineListItem row;
                if (convertView == null){
                    final LayoutInflater inflater =
                            (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    row = (TwoLineListItem) inflater.inflate(android.R.layout.simple_list_item_2, null);
                } else {
                    row = (TwoLineListItem) convertView;
                }

                final UsbSerialPort port = mEntries.get(position);
                final UsbSerialDriver driver = port.getDriver();
                final UsbDevice device = driver.getDevice();

                final String title = String.format("Vendor %s Product %s",
                        HexDump.toHexString((short) device.getVendorId()),
                        HexDump.toHexString((short) device.getProductId()));
                row.getText1().setText(title);

                final String subtitle = driver.getClass().getSimpleName();
                row.getText2().setText(subtitle);

                return row;
            }

        };
        mListView.setAdapter(mAdapter);

        mListView.setOnItemClickListener(new ListView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Log.d(TAG, "Pressed item " + position);
                if (position >= mEntries.size()) {
                    Log.w(TAG, "Illegal position.");
                    return;
                }

                final UsbSerialPort port = mEntries.get(position);
                showConsoleActivity(port);
            }
        });

    }


    public void onClickRed(View view){
        //write the serial data
            int col;
            col = 1;
            byte[] colorBytes = {(byte)Color.red(col),
                    (byte)Color.green(col),
                    (byte)Color.blue(col),
                    0x0A};
            //remove spurious line endings so the serial device doesn't get confused
            for (int i=0; i<colorBytes.length-1; i++){
                if (colorBytes[i] == 0x0A){
                    colorBytes[i] = 0x0B;
                }
            }
            //send the color to the serial device
            if (sPort != null){
                try{
                    sPort.write(colorBytes, 500);
                }
                catch (IOException e){
                    Log.e(TAG, "couldn't write color bytes to serial device");
                }
            }
    }

    public void onClickBlue(View view){
        //write the serial data
        int col;
        col = 2;
        byte[] colorBytes = {(byte)Color.red(col),
                (byte)Color.green(col),
                (byte)Color.blue(col),
                0x0A};
        //remove spurious line endings so the serial device doesn't get confused
        for (int i=0; i<colorBytes.length-1; i++){
            if (colorBytes[i] == 0x0A){
                colorBytes[i] = 0x0B;
            }
        }
        //send the color to the serial device
        if (sPort != null){
            try{
                sPort.write(colorBytes, 500);
            }
            catch (IOException e){
                Log.e(TAG, "couldn't write color bytes to serial device");
            }
        }

    }

    public void onClickGreen(View view){

        //write the serial data
        int col;
        col = 3;
        byte[] colorBytes = {(byte)Color.red(col),
                (byte)Color.green(col),
                (byte)Color.blue(col),
                0x0A};
        //remove spurious line endings so the serial device doesn't get confused
        for (int i=0; i<colorBytes.length-1; i++){
            if (colorBytes[i] == 0x0A){
                colorBytes[i] = 0x0B;
            }
        }
        //send the color to the serial device
        if (sPort != null){
            try{
                sPort.write(colorBytes, 500);
            }
            catch (IOException e){
                Log.e(TAG, "couldn't write color bytes to serial device");
            }
        }
    }


    private void stopIoManager() {
        if (mSerialIoManager != null) {
            Log.i(TAG, "Stopping io manager ..");
            mSerialIoManager.stop();
            mSerialIoManager = null;
        }
    }

    private void startIoManager() {
        if (sPort != null) {
            Log.i(TAG, "Starting io manager ..");
            mSerialIoManager = new SerialInputOutputManager(sPort, mListener);
            mExecutor.submit(mSerialIoManager);
        }
    }

    private void onDeviceStateChange() {
        stopIoManager();
        startIoManager();
    }

    private void updateReceivedData(byte[] data) {
        //final String message = "Read " + data.length + " bytes: \n"
         //       + HexDump.dumpHexString(data) + "\n\n";
        //mDumpTextView.append(message);
        //mScrollView.smoothScrollTo(0, mDumpTextView.getBottom());
    }

    private final SerialInputOutputManager.Listener mListener =
            new SerialInputOutputManager.Listener() {

                @Override
                public void onRunError(Exception e) {
                    Log.d(TAG, "Runner stopped.");
                }

                @Override
                public void onNewData(final byte[] data) {
                    MainActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            MainActivity.this.updateReceivedData(data);
                        }
                    });
                }
            };


    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();

        //serial
        if (sPort != null) {
            try {
                sPort.close();
            } catch (IOException e) {
                // Ignore.
            }
            sPort = null;
        }
    }


    public MainActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }




    public void onCameraViewStarted(int width, int height) {
        mGray = new Mat();
        mRgba = new Mat();
    }

    public void onCameraViewStopped() {
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        //здесь происходить обработка видео фрейма
        mRgba = inputFrame.rgba();
        Imgproc.cvtColor(mRgba, mGray, Imgproc.COLOR_BGRA2GRAY);

        //отправим что-нибудь в serial port
        //String data = editText.getText().toString();
        //write("1".getBytes());

        return mGray;
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();

    }

    @Override
    public void onResume()
    {
        super.onResume();
        //Вызываем асинхронный загрузчик библиотеки
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_10, this, mLoaderCallback);

        //serial
        if (sPort == null) {
            Log.d(TAG, "No serial device.");
        } else {
            final UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

            UsbDeviceConnection connection = usbManager.openDevice(sPort.getDriver().getDevice());
            if (connection == null) {
                Log.d(TAG, "Opening device failed");
                return;
            }

            try {
                sPort.open(connection);
                sPort.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            } catch (IOException e) {
                Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
                try {
                    sPort.close();
                } catch (IOException e2) {
                    // Ignore.
                }
                sPort = null;
                return;
            }
        }
        onDeviceStateChange();
    }


    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    //Мы готовы использовать OpenCV
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

}
