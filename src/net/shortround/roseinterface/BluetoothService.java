package net.shortround.roseinterface;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class BluetoothService {
	// Debugging
	private static final String TAG = "BluetoothService";
	private static final boolean D = true;

	// Name and UUID for the SDP record when creating server socket
	private static final UUID ROSE_SERVICE_UUID = UUID.fromString("227600fc-217a-4766-83bb-49e596bb9e88");
	
	// Member fields
	private final BluetoothAdapter adapter;
	private final Handler handler;
	private ConnectThread connectThread;
	private ConnectedThread connectedThread;
	private int state;
	
	// State constants
	public static final int STATE_NONE = 0;       // Doing nothing
	public static final int STATE_LISTEN = 1;     // NOT USED
	public static final int STATE_CONNECTING = 2; // Initiating an outgoing connection
	public static final int STATE_CONNECTED = 3;  // Connected to a remote device
	
	public BluetoothService(Context context, Handler handler) {
		if (D) Log.d(TAG, "New Bluetooth Service");
		
		adapter = BluetoothAdapter.getDefaultAdapter();
		state = STATE_NONE;
		this.handler = handler;
	}
	
	private synchronized void setState(int value) {
		if (D) Log.d(TAG, "setState() " + state + " -> " + value);
		state = value;
		
		handler.obtainMessage(RoseInterfaceActivity.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
	}
	
	public synchronized int getState() {
		return state;
	}
	
	public synchronized void start() {
		if (D) Log.d(TAG, "start");
		
		// Cancel any existing connect threads
		if (connectThread != null) { connectThread.cancel(); connectThread = null; }
		
		// Cancel any existing connected threads
		if (connectedThread != null) { connectedThread.cancel(); connectedThread = null; }
	}
	
	public synchronized void connect(BluetoothDevice device) {
		if (D) Log.d(TAG, "connect to: " + device);
		
		// Cancel any existing connect threads
		if (state == STATE_CONNECTING) {
			if (connectThread != null) { connectThread.cancel(); connectThread = null; }
		}
		
		// Cancel any existing connected threads
		if (connectedThread != null) { connectedThread.cancel(); connectedThread = null; }
		
		// Start a new connect thread
		connectThread = new ConnectThread(device);
		connectThread.start();
		setState(STATE_CONNECTING);
	}
	
	public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
		if (D) Log.d(TAG, "connected");
		
		// Cancel the thread that made the connection
		if (connectThread != null) { connectThread.cancel(); connectThread = null; }
		
		// Cancel any existing connected threads
		if (connectedThread != null) { connectedThread.cancel(); connectedThread = null; }
		
		// Start the new connected thread
		connectedThread = new ConnectedThread(socket);
		connectedThread.start();
		
		setState(STATE_CONNECTED);
	}
	
	public synchronized void stop() {
		if (D) Log.d(TAG, "stop");
		
		if (connectedThread != null) { connectedThread.cancel(); connectedThread = null; }
		if (connectThread != null) { connectThread.cancel(); connectThread = null; }
		
		setState(STATE_NONE);
	}
	
	public void write(byte[] out) {
		// Temp holder for the thread
		ConnectedThread r;
		
		// Get a synchronized copy of the ConnectedThread
		synchronized (this) {
			if (state != STATE_CONNECTED) return;
			r = connectedThread;
		}
		
		// Perform the write
		r.write(out);
	}
	
	private void connectionFailed() {
		Log.e(TAG, "Connection failed");
		Message message = handler.obtainMessage(RoseInterfaceActivity.MESSAGE_FAILURE);
		handler.sendMessage(message);
		
		BluetoothService.this.start();
	}
	
	private void connectionLost() {
		Log.e(TAG, "Connection lost");
		Message message = handler.obtainMessage(RoseInterfaceActivity.MESSAGE_FAILURE);
		handler.sendMessage(message);
		
		BluetoothService.this.start();
	}
	
	private class ConnectThread extends Thread {
		private final BluetoothDevice device;
		private final BluetoothSocket socket;
		
		public ConnectThread(BluetoothDevice device) {
			this.device = device;
			BluetoothSocket tmp = null;
			
			try {
				tmp = device.createRfcommSocketToServiceRecord(ROSE_SERVICE_UUID);
			} catch (IOException e) {
				Log.e(TAG, "create() failed", e);
			}
			
			socket = tmp;
		}
		
		public void run() {
			Log.i(TAG, "BEGIN connectThread");
			setName("ConnectThread");
			
			// Cancel discovery because we don't need it
			adapter.cancelDiscovery();
			
			// Make a connection to the socket
			try {
				socket.connect();
			} catch (IOException e) {
				// Close the socket
				try {
					socket.close();
				} catch (IOException e2) {
					Log.e(TAG, "unable to close() socket during connection failure", e2);
				}
				connectionFailed();
				return;
			}
			
			// Reset the ConnectThread because we're done
			synchronized(BluetoothService.this) {
				connectThread = null;
			}
			
			connected(socket, device);
		}
		
		public void cancel() {
			try {
				socket.close();
			} catch (IOException e) {
				Log.e(TAG, "close() of connect socket failed", e);
			}
		}
	}
	
	private class ConnectedThread extends Thread {
		private final InputStream inputStream;
		private final OutputStream outputStream;
		private final BluetoothSocket socket;
		
		public ConnectedThread(BluetoothSocket socket) {
			Log.d(TAG, "create ConnectedThread");
			this.socket = socket;
			
			InputStream tmpIn = null;
			OutputStream tmpOut = null;
			
			// Get the BluetoothSocket input and output streams
			try {
				tmpIn = socket.getInputStream();
				tmpOut = socket.getOutputStream();
			} catch (IOException e) {
				Log.e(TAG, "temp sockets not created", e);
			}
			
			inputStream = tmpIn;
			outputStream = tmpOut;
		}
		
		public void run() {
			Log.i(TAG, "BEGIN connectedThread");
			byte[] buffer = new byte[1024];
			int bytes;
			
			// Keep listening to the InputStream while connected
			while (true) {
				try {
					// Read from the InputStream
					bytes = inputStream.read(buffer);
					
					handler.obtainMessage(RoseInterfaceActivity.MESSAGE_READ, bytes, -1, buffer).sendToTarget();
				} catch (IOException e) {
					Log.e(TAG, "disconnected", e);
					connectionLost();
					BluetoothService.this.start();
					break;
				}
			}
		}
		
		public void write(byte[] buffer) {
			try {
				outputStream.write(buffer);
				
				handler.obtainMessage(RoseInterfaceActivity.MESSAGE_WRITE, -1, -1, buffer).sendToTarget();
			} catch (IOException e) {
				Log.e(TAG, "Exception during write", e);
			}
		}
		
		public void cancel() {
			try {
				socket.close();
			} catch (IOException e) {
				Log.e(TAG, "close() of connect socket failed", e);
			}
		}
	}
}
