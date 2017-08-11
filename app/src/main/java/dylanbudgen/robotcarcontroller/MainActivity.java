package ***REMOVED***robotcarcontroller;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.view.menu.ActionMenuItemView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroupOverlay;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.view.ViewGroup.LayoutParams;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import ***REMOVED***robotcarcontroller.bluetooth.BleNamesResolver;
import ***REMOVED***robotcarcontroller.bluetooth.BleWrapper;
import ***REMOVED***robotcarcontroller.bluetooth.BleWrapperUiCallbacks;

public class MainActivity extends AppCompatActivity {

    // BatMobile Bluetooth address
    private static final String BATMOBILE_ADDRESS = "CA:2F:AE:13:FD:41";

    // Code for permissions
    private static final int REQUEST_CODE_ACCESS_FINE_LOCATION = 10;

    // Distance until avoidance is detected in cm
    private final int ERROR_DISTANCE_FORWARD = 15;
    private final int ERROR_DISTANCE_SIDES = 15;

    // Directions
    private final static int STOP = 0;
    private final static int FORWARD = 1;
    private final static int LEFT = 2;
    private final static int RIGHT = 3;
    private final static int BACKWARD = 4;

    // Colours
    private final static int RECT_GREY = R.drawable.rect_grey;
    private final static int RECT_GREEN = R.drawable.rect_green;
    private final static int RECT_RED = R.drawable.rect_red;
    private final static int CIRCLE_GREY = R.drawable.circle_grey;
    private final static int CIRCLE_GREEN = R.drawable.circle_green;
    private final static int CIRCLE_RED = R.drawable.circle_red;

    // BL wrapper
    private BleWrapper mBleWrapper = null;

    // Media player for error sounds
    MediaPlayer errorSoundPlayer;

    // Sound enabled setting
    boolean soundEnabled;

    // Variables for scanning popup
    private FoundDeviceArrayAdapter scanningListviewAdapter;
    private ArrayList<FoundDevice> devicesList = new ArrayList<>();
    private PopupWindow mPopupWindow = null;

    // States
    private String mState = "";
    int mDirection = 0;

    // Ultrasound values
    int ultrasoundLeftValue = 100;
    int ultrasoundFrontValue = 100;
    int ultrasoundRightValue = 100;

    // UUIDs of serivces and characteristics
    private static final UUID
            UUID_DIRECTION_SERVICE = UUID.fromString("f0000a000-0000-1000-8000-00805f9b34fb"),
            UUID_DIRECTION_WRITE = UUID.fromString("f0000a002-0000-1000-8000-00805f9b34fb"),
            UUID_SPEED_SERVICE = UUID.fromString("f0000a003-0000-1000-8000-00805f9b34fb"),
            UUID_SPEED_WRITE = UUID.fromString("f0000a004-0000-1000-8000-00805f9b34fb"),
            UUID_ULTRASOUND_SERVICE = UUID.fromString("f0000a005-0000-1000-8000-00805f9b34fb"),
            UUID_ULTRASOUND_LEFT = UUID.fromString("f0000a007-0000-1000-8000-00805f9b34fb"),
            UUID_ULTRASOUND_FRONT = UUID.fromString("f0000a006-0000-1000-8000-00805f9b34fb"),
            UUID_ULTRASOUND_RIGHT = UUID.fromString("f0000a008-0000-1000-8000-00805f9b34fb");


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set up toolbar
        Toolbar myToolbar = (Toolbar) findViewById(R.id.main_toolbar);
        setSupportActionBar(myToolbar);

        // Set up button listeners
        setupButtonListener(R.id.button_direction_backward, BACKWARD);
        setupButtonListener(R.id.button_direction_stop, STOP);
        setupButtonListener(R.id.button_direction_forward, FORWARD);
        setupButtonListener(R.id.button_direction_left, LEFT);
        setupButtonListener(R.id.button_direction_right, RIGHT);

        // Initiate BL wrapper
        mBleWrapper = new BleWrapper(this, new BleWrapperUiCallbacks.Null() {

            // Override the methods for Bluetooth

            @Override
            public void uiDeviceFound(final BluetoothDevice device,
                                      final int rssi,
                                      final byte[] record)
            {
                if(device.getName() != null) {

                    String deviceName = device.getName();
                    String deviceAddress = device.getAddress().toString();

                    //Log.d("DEBUG", "000P uiDeviceFound: " + deviceName + ", " + rssi);

                    // Checking if device has already been found
                    for (FoundDevice foundDevice : devicesList) {
                        if (deviceAddress.equals(foundDevice.getDeviceAddress())) {
                            return; // Device has already been found
                        }
                    }

                    devicesList.add(new FoundDevice(deviceAddress, deviceName));
                    scanningListviewAdapter.notifyDataSetChanged();

                }

            }

            // Executed when a service is found
            @Override
            public void uiAvailableServices(BluetoothGatt gatt,
                                            BluetoothDevice device,
                                            List<BluetoothGattService> services) {

                /* for (BluetoothGattService service : services) {
                    String serviceName = BleNamesResolver.resolveUuid(service.getUuid().toString());

                    //Log.d("DEBUG", "000P Found service: " + serviceName + " with UUID: "
                            + service.getUuid().toString());

                    for (BluetoothGattCharacteristic c : service.getCharacteristics()) {
                        //Log.d("DEBUG", "000P Found characteristic: " + " with UUID: "
                                + c.getUuid().toString());
                    }
                } */

                // Device was told to disconnect but attempted to connect again
                if (mState.equals("MANUAL_DISCONNECT")) {
                    return;
                }

                updateStatusMessage(getString(R.string.updating_settings));
                setSpeedSetting();
            }


            // Executed when a new value for a characteristic is found (also through notifications)
            @Override
            public void uiNewValueForCharacteristic(BluetoothGatt gatt,
                                                    BluetoothDevice device,
                                                    BluetoothGattService service,
                                                    BluetoothGattCharacteristic ch,
                                                    String strValue,
                                                    int intValue,
                                                    byte[] rawValue,

                                                    String timestamp) {

                super.uiNewValueForCharacteristic(gatt,device,service,ch,strValue,
                        intValue,rawValue,timestamp);

                UUID updatedUUID = ch.getUuid();

                if (updatedUUID.equals(UUID_ULTRASOUND_LEFT)) {

                    //Log.d("DEBUG", "000P Ultrasound left read: " + intValue);
                    ultrasoundLeftValue = intValue;

                } else if (updatedUUID.equals(UUID_ULTRASOUND_FRONT)) {

                    //Log.d("DEBUG", "000P Ultrasound front read: " + intValue);
                    ultrasoundFrontValue = intValue;

                } else if (updatedUUID.equals(UUID_ULTRASOUND_RIGHT)) {

                    //Log.d("DEBUG", "000P Ultrasound right read: " + intValue);
                    ultrasoundRightValue = intValue;

                }

                // Device was told to disconnect but attempted to connect again
                if (mState.equals("MANUAL_DISCONNECT")) {
                    return;
                }

                checkSensor();

            }

            // Executed on successful write
            @Override
            public void uiSuccessfulWrite( BluetoothGatt gatt,
                                           BluetoothDevice device,
                                           BluetoothGattService service,
                                           BluetoothGattCharacteristic ch,
                                           String description) {

                BluetoothGattCharacteristic c;

                super.uiSuccessfulWrite(gatt, device, service, ch, description);

                //Log.d("DEBUG", "000P uiSuccessfulWrite.");

                // Chain reaction for the enabling of notifcations
                switch (mState) {
                    case "SET_SPEED_SETTING" :
                        // Now enable the left sensor, then the front sensor
                        mState = "ULTRASOUND_FRONT_ENABLE";
                        enableNotifications(UUID_ULTRASOUND_SERVICE, UUID_ULTRASOUND_LEFT);
                        break;
                    case "ULTRASOUND_FRONT_ENABLE" :
                        // Now enable front sensor, then the right sensor
                        mState = "ULTRASOUND_RIGHT_ENABLE";
                        enableNotifications(UUID_ULTRASOUND_SERVICE, UUID_ULTRASOUND_FRONT);
                        break;
                    case "ULTRASOUND_RIGHT_ENABLE" :
                        mState = "UPDATE_PROGRESS_BAR";
                        enableNotifications(UUID_ULTRASOUND_SERVICE, UUID_ULTRASOUND_RIGHT);
                        break;
                    case "UPDATE_PROGRESS_BAR" :
                        mState = "CONNECTED";
                        updateProgressBar(false);
                        updateStatusMessage(getString(R.string.connected));
                        updateDirectionButtons(true);
                        updateUltrasoundGraphics(RECT_GREEN, CIRCLE_GREEN);
                        break;
                }


            }

            // Executed on failed write
            @Override
            public void uiFailedWrite( BluetoothGatt gatt,
                                       BluetoothDevice device,
                                       BluetoothGattService service,
                                       BluetoothGattCharacteristic ch,
                                       String description) {

                super.uiFailedWrite(gatt, device, service, ch, description);

                mBleWrapper.diconnect();

                updateStatusMessage(getString(R.string.error_connecting));
                updateProgressBar(false);

                //Log.d("DEBUG", "000P uiFailedWrite");
            }


            @Override
            public void uiDeviceConnected(BluetoothGatt gatt, BluetoothDevice device) {
                super.uiDeviceConnected(gatt, device);

                //Log.d("DEBUG", "000P Device connected");

                return;
            }


            @Override
            public void uiDeviceDisconnected(BluetoothGatt gatt, BluetoothDevice device) {
                super.uiDeviceDisconnected(gatt, device);

                // Device has disconnected unexpectedly - excepted disconnections handled in disconnect()
                if (!mState.equals("MANUAL_DISCONNECT")) {

                    //Log.d("DEBUG", "000P Device disconnected");
                    updateStatusMessage(getString(R.string.disconnected));
                    updateProgressBar(false);
                    updateDirectionButtons(false);
                    updateUltrasoundGraphics(RECT_GREY, CIRCLE_GREY);
                    mState = "";
                }
            }

        });

        // Check if BLE is supported by the device
        if(!mBleWrapper.checkBleHardwareAvailable()) {
            Toast.makeText(this, "No BLE compatible hardware detected",
                    Toast.LENGTH_SHORT).show();
            finish();
        }

        checkBluetoothStatus();
    }

    @Override
    public void onResume() {
        super.onResume();

        mState = "";
        updateStatusMessage(getString(R.string.no_connection));
        updateProgressBar(false);
        updateDirectionButtons(false);
        updateUltrasoundGraphics(RECT_GREY, CIRCLE_GREY);

        devicesList.clear();
        mBleWrapper.initialize();

        // Initialise media player
        errorSoundPlayer = MediaPlayer.create(this, R.raw.beep_censor);
        errorSoundPlayer.setLooping(false);

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        soundEnabled = sharedPref.getBoolean("sound_switch", false);
    }


    @Override
    public void onPause() {
        super.onPause();

            mBleWrapper.stopScanning();
            disconnect();
            mBleWrapper.close();

        if  (mPopupWindow != null) {
            devicesList.clear();
            mPopupWindow.dismiss();
            ViewGroup viewGroup = (ViewGroup) findViewById(android.R.id.content);
            clearDim(viewGroup);

        }

        // Media errorSoundPlayer release
        errorSoundPlayer.release();
        errorSoundPlayer = null;

    }

    // Required to set up toolbar and add buttons from res/main_menu/main_menu.xmlu.xml
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(R.menu.main_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case R.id.action_connect:
                // Connect button pressed
                scan();
                return true;

            case R.id.action_settings:
                // Settings button pressed
                showSettings();
                return true;

            case R.id.action_about:
                // About button pressed
                showAbout();
                return true;

            default:
                // Input not recognised, invoke superclass.
                return super.onOptionsItemSelected(item);
        }
    }

    // Show the about page
    private void showAbout() {
        Intent intent = new Intent(this, AboutActivity.class);
        startActivity(intent);
    }

    // Change the settings
    private void showSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }


    private void scan() {

        devicesList.clear();

        // Check if the window is already open
        if(mState.equals("SCANNING")) {
            // The scanning window is already open.
        } else {

            // Permissions for fine location called when user wishes to use Bluetooth
            if (!checkBluetoothPermissions()) {
                return;
            }

            // Reset bluetooth if already connected, so user can connect again
            if (mBleWrapper.isConnected()) {
                updateProgressBar(true);
                updateStatusMessage(getString(R.string.disconnecting));
                disconnect();
                return;
            }

            if (!mBleWrapper.isBtEnabled()) {
                Toast.makeText(this, R.string.BL_off, Toast.LENGTH_SHORT).show();
                finish();
            }

            // Update the state to prevent user opening multiple windows
            mState = "SCANNING";

            ConstraintLayout layout = (ConstraintLayout) findViewById(R.id.main_activity);
            LayoutInflater inflater = (LayoutInflater) this.getSystemService(LAYOUT_INFLATER_SERVICE);

            // Inflate the custom layout/view
            View customView = inflater.inflate(R.layout.popup_scanning, null, true);

            // Initialize a new instance of popup window
            mPopupWindow = new PopupWindow(
                    customView,
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT
            );

            // Set an elevation value for popup window
            if (Build.VERSION.SDK_INT >= 21) {
                mPopupWindow.setElevation(5.0f);
            }

            // Set open/close animation
            mPopupWindow.setAnimationStyle(R.style.PopUp_Animation);

            // Get a reference for the custom view close button
            ImageButton closeButton = (ImageButton) customView.findViewById(R.id.ib_close);

            // Dim background
            final ViewGroup viewGroup = (ViewGroup) findViewById(android.R.id.content);
            applyDim(viewGroup, 0.5f);

            // Set a click listener for the popup window close button
            closeButton.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {
                        mState = "";
                        mBleWrapper.stopScanning();
                        devicesList.clear();
                        mPopupWindow.dismiss();
                        clearDim(viewGroup);

                        // Null the scanningListviewAdapter and PopupWindow to free resources
                        mPopupWindow = null;
                        scanningListviewAdapter = null;
                    }
            });

            // Show the popup window at the center location of root relative layout
            mPopupWindow.showAtLocation(layout, Gravity.CENTER, 0, 0);

            // Set up the button listener for connecting
            AdapterView.OnItemClickListener mMessageClickedHandler = new AdapterView.OnItemClickListener() {
                    public void onItemClick(AdapterView parent, View v, int position, long id) {

                        mBleWrapper.stopScanning();
                        mPopupWindow.dismiss();
                        ViewGroup viewGroup = (ViewGroup) findViewById(android.R.id.content);
                        clearDim(viewGroup);

                        connect(devicesList.get(position).getDeviceAddress());

                    }
                };

            // Set up the list adapter to update the list
            scanningListviewAdapter = new FoundDeviceArrayAdapter(this,
                        android.R.layout.simple_list_item_2, devicesList);

            ListView listView = (ListView) customView.findViewById(R.id.listView_scanning);
            listView.setAdapter(scanningListviewAdapter);
                listView.setOnItemClickListener(mMessageClickedHandler);

            // Start scanning
            mBleWrapper.startScanning();

        }
    }


    /*
     *  Method written by Markus Rubey - https://stackoverflow.com/users/1399597/markus-rubey
     */
    public static void applyDim(@NonNull ViewGroup parent, float dimAmount){
        Drawable dim = new ColorDrawable(Color.BLACK);
        dim.setBounds(0, 0, parent.getWidth(), parent.getHeight());
        dim.setAlpha((int) (255 * dimAmount));

        ViewGroupOverlay overlay = parent.getOverlay();
        overlay.add(dim);
    }

    /*
     *  Method written by Markus Rubey - https://stackoverflow.com/users/1399597/markus-rubey
     */
    public static void clearDim(@NonNull ViewGroup parent) {
        ViewGroupOverlay overlay = parent.getOverlay();
        overlay.clear();
    }

    public void connect(String address) {

        if (!address.equals(BATMOBILE_ADDRESS)) {
            updateStatusMessage(getString(R.string.wrong_device));
            return;
        }

        if (mBleWrapper.isConnected()) {
            Toast.makeText(this, R.string.error_restarting, Toast.LENGTH_SHORT).show();
            finish();

        } else {
            //Log.d("DEBUG", "000P Connecting");
            mState = "CONNECTING";
            updateProgressBar(true);
            updateStatusMessage(getString(R.string.connecting));
            mBleWrapper.connect(address);
        }
    }

    /*
     * Manual disconnect - required to allow time for wrapper to disconnect successfully
     */
    public void disconnect() {

        mState = "MANUAL_DISCONNECT";

        mBleWrapper.diconnect();

        updateDirectionButtons(false);
        updateUltrasoundGraphics(RECT_GREY, CIRCLE_GREY);
        updateConnectButton(false);

        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {

                updateConnectButton(true);
                updateProgressBar(false);
                updateStatusMessage(getString(R.string.ready));
            }
        }, 2000);


    }

    public void enableNotifications(UUID serviceUUID, UUID charUUID) {

        //Log.d("DEBUG", "000P Setting on notifications");

        BluetoothGatt gatt;
        BluetoothGattCharacteristic c;
        gatt = mBleWrapper.getGatt();
        c = gatt.getService(serviceUUID).getCharacteristic(charUUID);
        mBleWrapper.setNotificationForCharacteristic(c, true);
    }

    public void setSpeedSetting() {

        mState = "SET_SPEED_SETTING";

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        int speed = Integer.parseInt(sharedPref.getString("speed", "2"));

        writeToCharacteristic(UUID_SPEED_SERVICE, UUID_SPEED_WRITE, speed);
    }

    public void writeToCharacteristic(UUID service, UUID characteristic, int writeValue) {

        BluetoothGatt gatt;
        BluetoothGattCharacteristic c;

        gatt = mBleWrapper.getGatt();
        c = gatt.getService(service).getCharacteristic(characteristic);

        byte[] value = new byte[1];

        value[0] = (byte) (writeValue);
        mBleWrapper.writeDataToCharacteristic(c, value);

    }


    public boolean checkSensor() {

        boolean status = true;

        if (ultrasoundFrontValue <= ERROR_DISTANCE_FORWARD) {
            updateUltrasoundSensorGraphic(R.id.textView_front_ultrasound, RECT_RED);
            status = false;
        } else {
            updateUltrasoundSensorGraphic(R.id.textView_front_ultrasound, RECT_GREEN);
        }

        if (ultrasoundLeftValue <= ERROR_DISTANCE_SIDES) {
            updateUltrasoundSensorGraphic(R.id.textView_left_ultrasound, RECT_RED);
            status = false;
        } else {
            updateUltrasoundSensorGraphic(R.id.textView_left_ultrasound, RECT_GREEN);
        }

        if (ultrasoundRightValue <= ERROR_DISTANCE_SIDES) {
            updateUltrasoundSensorGraphic(R.id.textView_right_ultrasound, RECT_RED);
            status = false;
        } else {
            updateUltrasoundSensorGraphic(R.id.textView_right_ultrasound, RECT_GREEN);
        }

        // Sensors are triggered so disable forward button
        if (!status) {

            updateForwardButton(false);

            if (mDirection == FORWARD) {
                if (soundEnabled) {
                    errorSoundPlayer.start();
                }
                changeDirection(STOP);
                return false;
            }

            // direction is not forward, so car can proceed in current direction
            return true;

        } else {

            // sensors are all fine, so return true and enable forward button
            updateForwardButton(true);
            return true;

        }
    }

    public void changeDirection(int direction) {

        mDirection = direction;

        if (direction == STOP) {
            writeToCharacteristic(UUID_DIRECTION_SERVICE, UUID_DIRECTION_WRITE, direction);
            return;

        } else if (checkSensor()) {
            writeToCharacteristic(UUID_DIRECTION_SERVICE, UUID_DIRECTION_WRITE, direction);
        }

    }


    private void updateConnectButton(final boolean status) {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                ActionMenuItemView button = (ActionMenuItemView) findViewById(R.id.action_connect);
                button.setEnabled(status);

            }
        });

    }


    private void updateForwardButton(final boolean status) {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                Button buttonForward = (Button) findViewById(R.id.button_direction_forward);
                buttonForward.setEnabled(status);

            }
        });
    }

    private void updateDirectionButtons(final boolean status) {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                Button buttonStop = (Button) findViewById(R.id.button_direction_stop);
                buttonStop.setEnabled(status);

                Button buttonLeft = (Button) findViewById(R.id.button_direction_left);
                buttonLeft.setEnabled(status);

                Button buttonForward = (Button) findViewById(R.id.button_direction_forward);
                buttonForward.setEnabled(status);

                Button buttonRight = (Button) findViewById(R.id.button_direction_right);
                buttonRight.setEnabled(status);

                Button buttonBackward = (Button) findViewById(R.id.button_direction_backward);
                buttonBackward.setEnabled(status);

            }
        });
    }

    private void updateUltrasoundSensorGraphic(final int id, final int colour) {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                TextView textView = (TextView) findViewById(id);
                textView.setBackgroundResource(colour);
            }
        });
    }

    // Null for grey, false for red, true for green
    private void updateUltrasoundGraphics(final int rectColour, final int circleColour) {

        updateUltrasoundSensorGraphic(R.id.textView_left_ultrasound, rectColour);
        updateUltrasoundSensorGraphic(R.id.textView_right_ultrasound, rectColour);
        updateUltrasoundSensorGraphic(R.id.textView_front_ultrasound, rectColour);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                ImageView image = (ImageView) findViewById(R.id.imageView);
                image.setImageResource(circleColour);

            }
        });

    }

    private void updateProgressBar(final boolean visibility) {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                ProgressBar progressBar = (ProgressBar) findViewById(R.id.progressBar);

                if (visibility) {
                    progressBar.setVisibility(ProgressBar.VISIBLE);
                } else {
                    progressBar.setVisibility(ProgressBar.INVISIBLE);
                }
            }
        });
    }


    private void updateStatusMessage(final String message) {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                TextView textView = (TextView) findViewById(R.id.textview_connection_status);
                textView.setText(message);
            }
        });
    }

    private boolean checkBluetoothPermissions() {

        if (Build.VERSION.SDK_INT > 22) {  // Device needs runtime permissions

            // Checking if permission for location is already set
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CODE_ACCESS_FINE_LOCATION);
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {

        if (requestCode == REQUEST_CODE_ACCESS_FINE_LOCATION) {
            // Permission for location recieved
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            } else {
                Toast.makeText(this, R.string.permissions_rejected, Toast.LENGTH_SHORT).show();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void setupButtonListener(int buttonName, final int direction) {

        Button button = (Button) findViewById(buttonName);

        button.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                switch (event.getAction()) {

                    case MotionEvent.ACTION_DOWN:
                        //Log.d("DEBUG", "00P Button down " + " Direction: " + direction);

                        mDirection = direction;
                        changeDirection(direction);
                        break;

                    case MotionEvent.ACTION_UP:
                        //Log.d("DEBUG", "00P Button up " + " Direction: " + direction);
                        changeDirection(STOP);
                        break;
                }
                return true;
            }
        });
    }



    private void checkBluetoothStatus() {

        if (!mBleWrapper.isBtEnabled()) {
            // Bluetooth is not enabled.
            Intent enableBT = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(enableBT);
            finish();
        }
    }

}
