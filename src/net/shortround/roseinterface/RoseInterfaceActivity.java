package net.shortround.roseinterface;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ToggleButton;

public class RoseInterfaceActivity extends Activity {
	// Debug
	private static final String TAG = "RoseInterfaceActivity";
	
	// Intent request codes
	private static final int REQUEST_CONNECT_DEVICE = 1;
	private static final int REQUEST_ENABLE_BT = 2;
	
	// Messages sent from the Bluetooth Service
	public static final int MESSAGE_FAILURE = 1;
	public static final int MESSAGE_GET_DATA = 2;
	public static final int MESSAGE_READ = 3;
	public static final int MESSAGE_STATE_CHANGE = 4;
	public static final int MESSAGE_WRITE = 5;
	
	// Layout Views
	private TextView batteryTextView;
	private Button decayButton;
	private TextView decayTextView;
	private ToggleButton displayButton;
	private Button revertButton;
	
	// String buffer for outgoing messages
	private StringBuffer outputStringBuffer;
	// Local bluetooth adapter
	private BluetoothAdapter bluetoothAdapter = null;
	// Member object for the bluetooth service
	private BluetoothService bluetoothService = null;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
       
        // Set up the layout
        setContentView(R.layout.main);
        
        // Get local Bluetooth adapter
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        
        // If the adapter is null, then Bluetooth is not supported
        if (bluetoothAdapter == null) {
        	Log.e(TAG, "No bluetooth adapter");
        	finish();
        	return;
        }
    }
    
    @Override
    public void onStart() {
    	super.onStart();
    	
    	// Request bluetooth 
    	if (bluetoothAdapter.isEnabled()) {
    		Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
    		startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
    	} else {
    		if (bluetoothService == null) setupBluetoothService();
    	}
    }
    
    @Override
    public synchronized void onResume() {
    	super.onResume();
    	
    	if (bluetoothService != null) {
    		if (bluetoothService.getState() == BluetoothService.STATE_NONE) {
    			bluetoothService.start();
    		}
    	}
    }
    
    private void setupBluetoothService() {
    	// Set up buttons
    	decayButton = (Button) findViewById(R.id.decay);
    	decayButton.setOnClickListener(new OnClickListener() {
    		public void onClick(View v) {
    			sendMessage("decay");
    		}
    	});
    	
    	revertButton = (Button) findViewById(R.id.revert);
    	revertButton.setOnClickListener(new OnClickListener() {
    		public void onClick(View v) {
    			sendMessage("revert");
    		}
    	});
    	
    	displayButton = (ToggleButton) findViewById(R.id.display);
    	displayButton.setOnClickListener(new OnClickListener() {
    		public void onClick(View v) {
    			sendMessage("display");
    		}
    	});
    	
    	// Link up labels
    	batteryTextView = (TextView) findViewById(R.id.batteryStatus);
    	decayTextView = (TextView) findViewById(R.id.decayStatus);
    	
    	// Initialize the bluetooth service
    	bluetoothService = new BluetoothService(this, handler);
    	
    	// Build the outgoing buffer
    	outputStringBuffer = new StringBuffer("");
    	
    	// Shutdown the interface
    	disableFields("Not connected");
    }
    
    @Override
    public synchronized void onPause() {
    	super.onPause();
    }
    
    @Override
    public void onStop() {
    	super.onStop();
    }
    
    @Override
    public void onDestroy() {
    	super.onDestroy();
    }
    
    private void sendMessage(String message) {
    	// Check that we have a connection
    	if (bluetoothService.getState() != BluetoothService.STATE_CONNECTED) {
    		Log.e(TAG, "Send message with no connection");
    		return;
    	}
    	
    	// Check that there's something to send
    	if (message.length() > 0) {
    		// Get the message as bytes
    		byte[] send = message.getBytes();
    		bluetoothService.write(send);
    		
    		// Clean up
    		outputStringBuffer.setLength(0);
    	}
    }
    
    private final Handler handler = new Handler() {
    	@Override
    	public void handleMessage(Message message) {
    		switch (message.what) {
    		case MESSAGE_FAILURE:
    			break;
    		case MESSAGE_GET_DATA:
    			break;
    		case MESSAGE_READ:
    			break;
    		case MESSAGE_STATE_CHANGE:
    			switch(message.arg1) {
    			case BluetoothService.STATE_NONE:
    			case BluetoothService.STATE_LISTEN:
    				// Disable fields
    				disableFields(getString(R.string.not_connected));
    				break;
    			case BluetoothService.STATE_CONNECTING:
    				// Disable fields, show connecting
    				disableFields(getString(R.string.connecting));
    				break;
    			case BluetoothService.STATE_CONNECTED:
    				// Enable fields
    				enableFields();
    				break;
    			}
    			break;
    		case MESSAGE_WRITE:
    			break;
    		}
    	}
    };
    
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
    	switch (requestCode) {
    	case REQUEST_CONNECT_DEVICE:
    		if (resultCode == Activity.RESULT_OK) {
    			connectDevice(data);
    		}
    		break;
    	case REQUEST_ENABLE_BT:
    		if (resultCode == Activity.RESULT_OK) {
    			setupBluetoothService();
    		} else {
    			Log.e(TAG, "Bluetooth not enabled");
    			finish();
    		}
    		break;
    	}
    }
    
    private void connectDevice(Intent data) {
    	// Get the device MAC address
    	String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
    	// Get the BluetoothDevice object
    	BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
    	// Attempt to connect to the device
    	bluetoothService.connect(device);
    }
    
    private void disableFields(String message) {
    	// Disable buttons
    	decayButton.setEnabled(false);
    	displayButton.setEnabled(false);
    	revertButton.setEnabled(false);
    	
    	// Clear text fields
    	batteryTextView.setText("Battery: --");
    	decayTextView.setText("Decay: --");
    	
    	// Set the title
    	setTitle(getString(R.string.app_name) + " - " + message);
    }
    
    private void enableFields() {
    	// Enable buttons
    	decayButton.setEnabled(true);
    	displayButton.setEnabled(true);
    	revertButton.setEnabled(true);
    	
    	// Clear text fields
    	batteryTextView.setText("Battery: --");
    	decayTextView.setText("Decay: --");
    	
    	// Set the title
    	setTitle(getString(R.string.app_name));
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	MenuInflater inflater = getMenuInflater();
    	inflater.inflate(R.menu.option_menu, menu);
    	return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	Intent serverIntent = null;
    	switch (item.getItemId()) {
    	case R.id.connect_scan:
    		serverIntent = new Intent(this, DeviceListActivity.class);
    		startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
    		return true;
    	}
    	
    	return false;
    }
}