package com.peets.webrtc;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;

/*
 * the main activity to host the webRTC connectivity
 */
public class WebRTCActivity extends Activity {
	private Button connectButton = null;
	private Button hangupButton = null;
	private WebView webView = null;
	private static final String USER_AGENT = "Mozilla/5.0";
	private static String WEBRTC_URL = "https://apprtc.appspot.com";
	private static final String USER_AGENT_HEADER = "User-Agent";
	private static final String NAME = "chatRoomId";
	private String chatRoom = null;
	private boolean acceptIncomingRequest = false;
	
	// server related constants
	private static final String BASE_URL = "http://54.183.35.231:8080/CustomerService-server";
	private static final String SERVER_URL = BASE_URL + "/customerService";
	private static final String GET_URL = SERVER_URL + "/1";
	private static final String FIND_URL = SERVER_URL + "?action=findChatRoom";
	private static final String CREATE_FORMAT = "{\"chatRoomId\":%s}";


	/**
	 * Called when the activity is first created. This is where we'll hook up
	 * our views in XML layout files to our application.
	 **/
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Log.d("WebRTCActivity", "On create");
		setContentView(R.layout.speech);
		
		// this is the button for a user to connect to a friend
		connectButton = (Button) findViewById(R.id.connect_button);
		connectButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				Log.d("WebRTCActivity", "OnClick connectButton");
				
				// when clicked, it first finds a chat room to join
				findChatRoom();
				
				// this will connects the current user to the chat room
				// found above.
				handleCheck();				
			}
		});

		hangupButton = (Button)findViewById(R.id.hangup_button);
		hangupButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				Log.d("WebRTCActivity", "hangupButton OnClick");
				
				// when clicked, it hang up the call and clean the webview
				clearWebViewCache();				
			}
		});
		// This will show a web view that hosts the video chat
		webView = (WebView) findViewById(R.id.webview);
	}

    /**
     * @Title: clearWebViewCache
     * @Description: Hang up method , clear webview cache
     * @param
     * @return void
     * @throws
     */
    protected void clearWebViewCache() {
    	Log.d("WebRTCActivity", "clearWebViewCache will clear view");
        // before reload URL clear the web cache make sure setting info clear .
        webView.clearCache(true);
        webView.reload();
        webView.evaluateJavascript("if(window.localStream){window.localStream.stop();}", null);
               
//        initWebView();
        
        hangupButton.setEnabled(false);
        connectButton.setEnabled(true);
    }
    
	/**
	 * Called when the activity is coming to the foreground. This is where we
	 * will check whether there's an incoming connection.
	 **/
	@Override
	protected void onStart() {
		super.onStart();

		Log.d("WebRTCActivity", "on start: will check whether there's existing connection");
		checkExistingConnection();
	}

	/**
	 * method to check whether there's an incoming connection
	 */
	private void checkExistingConnection() {
		Log.d("WebRTCActivity", "getExistingConnection: will connnect to " + GET_URL);
		
		// call to the server to find out whether there is 
		// an incoming connection through an async task
		SocialPlayServer server = SocialPlayServer.get(GET_URL);
		server.getTo(new ServerGetListener());
		connectButton.setText(R.string.wait);
		connectButton.setEnabled(false);
	}

	/**
	 * method to find a chat room to join, this is for the initiator 
	 * of a video chat
	 */
	private void findChatRoom() {
		Log.d("WebRTCActivity", "findChatRoom");
		SocialPlayServer server = SocialPlayServer.findChatRoom(FIND_URL);
		server.fetchTo(new ServerFindListener());
		connectButton.setText(R.string.wait);
		connectButton.setEnabled(false);
	}

	private void create(String serverUrl, String chatRoomId) {
		Log.d("WebRTCActivity", "create");
		SocialPlayServer server = SocialPlayServer.create(serverUrl, chatRoomId);
		server.createTo(new ServerFindListener());
		connectButton.setText(R.string.wait);
		connectButton.setEnabled(false);
	}
	
	private void sleep(long milliseconds)
	{
		try{
			Log.d("WebRTCActivity", "will sleep " + milliseconds + "milliseconds");
			Thread.sleep(milliseconds);
		}catch(Exception ex)
		{
			Log.d("WebRTCActivity", "sleep encounters exception: " + ex.getMessage());
		}
	}
	/**
	 * Handle the result of an asynchronous Find request to server
	 **/
	private class ServerFindListener implements SocialPlayServer.Client {
		public void handleResponse(String chatRoomId, Exception error) {
			if (chatRoomId != null) {
				chatRoom = chatRoomId;
				Log.d("WebRTCActivity", "ServerFindListener returns: " + chatRoomId);
				connectButton.setText(R.string.button_connect);
				connectButton.setEnabled(true);
//				hangupButton.setEnabled(false);
			} else {
				Log.v("WebRTCActivity", "Server error: " + error);
				// There was either a network error or authentication error.
				// Show alert for the latter.
				alert("Server Unavailable",
						"This app was rejected by the server.  Contact the developer for an update.");
			}
		}
	}

	/**
	 * Handle the result of an asynchronous Get request to server
	 **/
	private class ServerGetListener implements SocialPlayServer.Client {
		public void handleResponse(String chatRoomId, Exception error) {
			if (chatRoomId != null) {
				chatRoom = chatRoomId;
				Log.d("WebRTCActivity", "ServerGetListener returns: " + chatRoomId);
				alertConnection("Your friend wants to connect you to the Social Play", "Do you want to connect?");
			} else {
				Log.v("WebRTCActivity", "ServerGetListener returns null.");
				alert("WebRTCActivity",
						"No existing connection");
				connectButton.setText(R.string.button_connect);
				connectButton.setEnabled(true);
//				hangupButton.setEnabled(false);
			}
		}
	}

	private class ServerCreateListener implements SocialPlayServer.Client {
		public void handleResponse(String chatRoomId, Exception error) {
			Log.d("WebRTCActivity", "ServerCreateListener returns: " + chatRoomId);
			if (error == null) {
				alert("WebRTCActivity",
						"Sent To Server");
				connectButton.setText(R.string.button_connect);
				connectButton.setEnabled(true);
//				hangupButton.setEnabled(false);
			}
		}
	}
	
	private void initWebView()
	{
		WebSettings webSettings = webView.getSettings();
		webSettings.setJavaScriptEnabled(true);
		webSettings.setDatabaseEnabled(true);
		webSettings.setDomStorageEnabled(true);
		webSettings.setAppCacheEnabled(true);
		webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
		//webView.setWebViewClient(new WebViewClient() {
		webView.setWebChromeClient(new WebChromeClient() {

			@SuppressLint("NewApi")
			//@Override
			public void onPermissionRequest(final PermissionRequest request) {
				runOnUiThread(new Runnable() {
					@TargetApi(Build.VERSION_CODES.LOLLIPOP)
					@Override
					public void run() {
						// grant the permission to access camera
						request.grant(request.getResources());
					}
				});
			}

		});
	}
	
	private boolean waitForRoomIsReady()
	{
		int count = 5;
		boolean ready = false;
		while(!ready && count > 0)
		{
			if (chatRoom == null) {
				Log.d("WebRTCActivity", "found charRoom as null");
				sleep(500);
				count--;
			}
			else
				ready = true;
		}
		
		return ready;
	}
	/**
	 * this is the main handler that handles connection
	 */
	private void handleCheck() {
		initWebView();
		try {
			// check whether it receives the chat room properly
			if (waitForRoomIsReady()) {
				// construct the url to apputc with the chat room to connect to
				String url = WEBRTC_URL + "/r/" + chatRoom;
				Log.d("WebRTCActivity", "handleCheck: "+ url);

				if (!acceptIncomingRequest) {
					// TODO: need to POST to server
					Log.d("WebRTCActivity", "POST to server: " + chatRoom);
					create(SERVER_URL, chatRoom);

					// reset to default
					acceptIncomingRequest = false;
					chatRoom = null;
				}

				// have the webview load the url
				webView.loadUrl(url);
				Log.d("WebRTCActivity", "handleCheck loaded url");
				connectButton.setEnabled(false);
				hangupButton.setEnabled(true);
			} else {
				alert("Failed to find a chat room to join!",
						"Please try again!");
			}
		} catch (Exception ex) {
			Log.d("WebRTCActivity", "exception: " + ex.getMessage());
		}
	}

	/**
	 * Show an alert dialog with message. 
	 * user can either click OK or cancel
	 * no real action is triggered
	 * @param header
	 * @param message
	 */
	private void alert(String header, String message) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(message)
				.setTitle(header)
				.setCancelable(true)
				.setPositiveButton(android.R.string.ok,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								dialog.dismiss();
							}
						});
		AlertDialog alert = builder.create();
		alert.show();
	}

	/**
	 * pops up an alert dialog with 2 messages, one as the header
	 * and the other as the message to be shown in the dialog.
	 * if user clicks on NO, it enable the connect button on screen
	 * if user clicks on YES, it connects user to the incoming request
	 * @param header
	 * @param message
	 */
	private void alertConnection(String header, String message) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(message)
				.setTitle(header)
				.setNegativeButton(R.string.no,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								dialog.dismiss();
								connectButton.setText(R.string.button_connect);
								connectButton.setEnabled(true);
							}
						})
				.setPositiveButton(android.R.string.yes,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								dialog.dismiss();
								acceptIncomingRequest = true;
								handleCheck();
							}
						});
		AlertDialog alert = builder.create();
		alert.show();
	}

}
