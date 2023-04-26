package com.vxplore.bluetoothprinter;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.util.Base64;
import android.view.View;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Button;
import android.widget.Toast;

import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

//    protected boolean shouldAskPermissions() {
//        return (Build.VERSION.SDK_INT > Build.VERSION_CODES.S);
//    }
//
//    @TargetApi(33)
//    protected void askPermissions() {
//        String[] permissions = {
//                "android.permission.READ_EXTERNAL_STORAGE",
//                "android.permission.WRITE_EXTERNAL_STORAGE"
//        };
//        int requestCode = 200;
//        requestPermissions(permissions, requestCode);
//    }

//    private static final int REQUEST_EXTERNAL_STORAGE = 1;
//    private static String[] PERMISSIONS_STORAGE = {
//            Manifest.permission.READ_EXTERNAL_STORAGE,
//            Manifest.permission.WRITE_EXTERNAL_STORAGE
//    };
//
//    public static void verifyStoragePermissions(Activity activity) {
//        // Check if we have write permission
//        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
//
//        if (permission != PackageManager.PERMISSION_GRANTED) {
//            // We don't have permission so prompt the user
//            ActivityCompat.requestPermissions(
//                    activity,
//                    PERMISSIONS_STORAGE,
//                    REQUEST_EXTERNAL_STORAGE
//
//            );
//        }
//    }







    TextView myLabel;
    EditText myTextbox;
    BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice;

    OutputStream mmOutputStream;
    InputStream mmInputStream;
    Thread workerThread;

    byte[] readBuffer;
    int readBufferPosition;
    volatile boolean stopWorker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= 30){
            if (!Environment.isExternalStorageManager()){
                Intent getpermission = new Intent();
                getpermission.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                //startActivity(getpermission);
            }
        }
     //   verifyStoragePermissions(this);
//        if (shouldAskPermissions()) {
//            askPermissions();
//        }

        try {
            Button openButton = (Button) findViewById(R.id.open);
            Button sendButton = (Button) findViewById(R.id.send);
            //Button closeButton = (Button) findViewById(R.id.close);

            myLabel = (TextView) findViewById(R.id.label);
            myTextbox = (EditText) findViewById(R.id.entry);

            openButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    try {
                        findBT();
                        openBT();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            });//openButton Listener


            // send data typed by the user to be printed
            sendButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    try {
                        sendData();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            });////sendButton Listener


//            // close bluetooth connection
//            closeButton.setOnClickListener(new View.OnClickListener() {
//                public void onClick(View v) {
//                    try {
//                        closeBT();
//                    } catch (IOException ex) {
//                        ex.printStackTrace();
//                    }
//                }
//            });////closeButton Listener


        } catch (Exception e) {
            e.printStackTrace();
        }//try-catch


        // open bluetooth connection


    }//onCreate(Bundle savedInstanceState)

    // this will find a bluetooth printer device
    void findBT() {

        try {
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

            if (mBluetoothAdapter == null) {
                myLabel.setText("No bluetooth adapter available");
            }

            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    startActivityForResult(enableBluetooth, 0);
                    return;
                }

            }

            Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

            if (pairedDevices.size() > 0) {
                for (BluetoothDevice device : pairedDevices) {

                    // RPP300 is the name of the bluetooth printer device
                    // we got this name from the list of paired devices
                    if (device.getName().equals("MT580P")) {
                        mmDevice = device;
                        //Toast.makeText(this, "@@@@@" + device.getName(), Toast.LENGTH_SHORT).show();
                        break;
                    }
                }
            }

            myLabel.setText("Bluetooth device found.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }//findBT()


    // tries to open a connection to the bluetooth printer device
    void openBT() throws IOException {
        try {

            // Standard SerialPortService ID
            UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");


            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
                mmSocket.connect();

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                    Toast.makeText(this,"Connection Status: "+mmSocket.isConnected(),Toast.LENGTH_SHORT).show();
                }
                return;
            }

            mmOutputStream = mmSocket.getOutputStream();
            mmInputStream = mmSocket.getInputStream();



            beginListenForData();

            myLabel.setText("Bluetooth Opened");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }//openBT()

    /*
     * after opening a connection to bluetooth printer device,
     * we have to listen and check if a data were sent to be printed.
     */
    void beginListenForData() {
        try {
            final Handler handler = new Handler();

            // this is the ASCII code for a newline character
            final byte delimiter = 10;

            stopWorker = false;
            readBufferPosition = 0;
            readBuffer = new byte[1024];

            workerThread = new Thread(new Runnable() {
                public void run() {

                    while (!Thread.currentThread().isInterrupted() && !stopWorker) {

                        try {

                            int bytesAvailable = mmInputStream.available();

                            if (bytesAvailable > 0) {

                                byte[] packetBytes = new byte[bytesAvailable];
                                mmInputStream.read(packetBytes);

                                for (int i = 0; i < bytesAvailable; i++) {

                                    byte b = packetBytes[i];
                                    if (b == delimiter) {

                                        byte[] encodedBytes = new byte[readBufferPosition];
                                        System.arraycopy(
                                                readBuffer, 0,
                                                encodedBytes, 0,
                                                encodedBytes.length
                                        );

                                        // specify US-ASCII encoding
                                        final String data = new String(encodedBytes, "US-ASCII");
                                        readBufferPosition = 0;

                                        // tell the user data were sent to bluetooth printer device
                                        handler.post(new Runnable() {
                                            public void run() {
                                                myLabel.setText(data);
                                            }
                                        });

                                    } else {
                                        readBuffer[readBufferPosition++] = b;
                                    }
                                }
                            }

                        } catch (IOException ex) {
                            stopWorker = true;
                        }

                    }
                }
            });

            workerThread.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }//beginListenForData()



 //=================================================

//    void sendData() throws IOException {
//
//
//
//        try {
//            String filepath = "/storage/self/primary/Download/DSC_1143.JPG";
//            File imagefile = new File(filepath);
//            FileInputStream fis = null;
//            try {
//                fis = new FileInputStream(imagefile);
//            } catch (FileNotFoundException e) {
//                e.printStackTrace();
//            }
//
//            Bitmap bm = BitmapFactory.decodeStream(fis);
//            ByteArrayOutputStream baos = new ByteArrayOutputStream();
//            bm.compress(Bitmap.CompressFormat.JPEG, 100 , baos);
//            byte[] b = baos.toByteArray();
//            String encImage = Base64.encodeToString(b, Base64.DEFAULT);
//            encImage += "\n";
//
//            // the text typed by the user
////            String msg = myTextbox.getText().toString();
////            msg += "\n";
//
//            if (encImage!=null) {
//                byte[] msgBuffer = encImage.getBytes();
//                try {
//                    mmOutputStream = mmSocket.getOutputStream();
//                } catch (IOException e) {
//                    //  errorExit("Fatal Error", "in sendData() input and output stream creation failed:" + e.getMessage() + ".");
//
//                }
//                try {
//                    mmOutputStream.write(msgBuffer);
//                } catch (IOException e) {
//                }
//            }
//
//
//            if(mmOutputStream!=null){
//                myLabel.setText("Data sent.");
//            }
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

// void sendData() throws IOException {
//        try {
//
//            //verifyStoragePermissions(this);
//            // FileInputStream fis = new FileInputStream (new File(getFilePath()));
//            if (Build.VERSION.SDK_INT >= 30) {
//                Toast.makeText(this,"Connection Status: "+mmSocket.isConnected(),Toast.LENGTH_SHORT).show();
//
//             FileInputStream fis = new FileInputStream(new File("//storage//self//primary//Download//pdffiletestestt.pdf"));
//            //InputStream fis = this.openFileInput("/storage/self/primary/Download/DownloaderDemo//pdffiletestestt.pdf"); // Where this is Activity
//            ByteArrayOutputStream bos = new ByteArrayOutputStream();
//            byte[] b = new byte[1024];
//            int bytesRead = fis.read(b);
//
//            while (bytesRead != -1) {
//                bos.write(b, 0, bytesRead);
//            }
//            byte[] bytes = bos.toByteArray();
//
//            byte[] printformat = {27, 33, 0}; //try adding this print format
//
//            mmOutputStream.write(printformat);
//            mmOutputStream.write(bytes);
//
//            // tell the user data were sent
//            myLabel.setText("Data Sent");
//
//            fis.close();
//
//            //closeBT();
//        }
//        }
////        } catch (NullPointerException e) {
////            e.printStackTrace();
////        } catch (Exception e) {
////            e.printStackTrace();
////        }
//        catch (FileNotFoundException e) {
//            e.printStackTrace();
//        }
//    }

//    private String getFilePath(){
//        ContextWrapper contextWrapper=new ContextWrapper(getApplicationContext());
//        File pdfDirectory=contextWrapper.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
//        File pdfFile=new File(pdfDirectory,"pdffiletestestt"+".pdf");
//
//        return pdfFile.getPath();
//    }


 //=================================================


    // this will send text data to be printed by the bluetooth printer
    void sendData() throws IOException {
        try {
//---------------------------------------
            String extractedText = "          ";
            PdfReader reader = new PdfReader("res/raw/test_pdf.pdf");
            int n = reader.getNumberOfPages();
            for (int i = 0; i < n; i++) {
                extractedText =extractedText + PdfTextExtractor.getTextFromPage(reader, i + 1).trim() + "\n";

//                if (extractedText=="CASH RECEIPT"){
//                    extractedText = extractedText + PdfTextExtractor.getTextFromPage(reader, i + 1).trim() + "       ";
//                }
            }

            // if (extractedText.contains("CASH RECEIPT")){
//            if (extractedText.matches()){
//
//            }

//            String txt_split= String.valueOf(extractedText.split("\n"));
//            System.out.println(txt_split);


            reader.close();
//-----------------------------------------
            // the text typed by the user
           // String msg = myTextbox.getText().toString();
            String msg = extractedText;
            //msg += "\n";

            if (msg!=null) {
                byte[] msgBuffer = msg.getBytes();
                try {
                    mmOutputStream = mmSocket.getOutputStream();
                } catch (IOException e) {
                    //  errorExit("Fatal Error", "in sendData() input and output stream creation failed:" + e.getMessage() + ".");

                }
                try {
                    mmOutputStream.write(msgBuffer);
                } catch (IOException e) {
                }
            }


            if(mmOutputStream!=null){
                myLabel.setText("Data sent.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }//sendData()

    // close the connection to bluetooth printer.
//    void closeBT() throws IOException {
//        try {
//            stopWorker = true;
//            mmOutputStream.close();
//            mmInputStream.close();
//            mmSocket.close();
//            myLabel.setText("Bluetooth Closed");
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }//closeBT()





}//MainActivity extends AppCompatActivity