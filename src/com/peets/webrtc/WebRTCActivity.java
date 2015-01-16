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
import android.os.AsyncTask;
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

	// state information
	private String chatRoom = null;
	private String previousChatRoom = null;
	private boolean acceptIncomingRequest = false;
	private boolean chatInProgress = false;

	// server related constants
	private static final String BASE_URL = "http://54.183.228.194:8080/SocialPlay-server/";
	private static final String SERVER_URL = BASE_URL + "socialPlay";
	private static final String GET_URL = SERVER_URL + "/1";
	private static final String UPDATE_URL = SERVER_URL + "/2";
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
		setContentView(R.layout.videochat);

		// this is the button for a user to connect to a friend
		connectButton = (Button) findViewById(R.id.connect_button);
		connectButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Log.d("WebRTCActivity", "OnClick connectButton");

				// when clicked, it first finds a chat room to join
				findChatRoomAndConnect();
			}
		});

		hangupButton = (Button) findViewById(R.id.hangup_button);
		hangupButton.setOnClickListener(new OnClickListener() {
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
		// webView.reload();
		webView.evaluateJavascript(
				"if(window.localStream){window.localStream.stop();}", null);

		// reliably reset the view state and release page resources
		webView.loadUrl("about:blank");
		// reset state and button states
		chatInProgress = false;
		hangupButton.setEnabled(false);
		connectButton.setEnabled(true);
		
		// reset to default
		acceptIncomingRequest = false;
		previousChatRoom = chatRoom;	// remember the previous chat room
		Log.d("WebRTCActivity", "clearWebViewCache preserve previous chat room: " + previousChatRoom);
		chatRoom = null;
		
		// once a video session is terminated it needs to monitor
		// incoming connection again
		CheckExistingConnectionTask checkTask = new CheckExistingConnectionTask();
		checkTask.execute();
	}

	/**
	 * Called when the activity is coming to the foreground. This is where we
	 * will check whether there's an incoming connection.
	 **/
	@Override
	protected void onStart() {
		super.onStart();

		Log.d("WebRTCActivity", "on start: chatInProgress = " + chatInProgress);

//		if (!chatInProgress) {
//			Log.d("WebRTCActivity",
//					"on start: will check whether there's existing connection");
//			checkExistingConnection();
//		}
		
		CheckExistingConnectionTask checkTask = new CheckExistingConnectionTask();
		checkTask.execute();		
	}

	/**
	 * utility to do sleep
	 * 
	 * @param milliseconds
	 */
	public void sleep(long milliseconds) {
		try {
			Log.d("WebRTCActivity", "will sleep " + milliseconds
					+ "milliseconds");
			Thread.sleep(milliseconds);
		} catch (Exception ex) {
			Log.d("WebRTCActivity",
					"sleep encounters exception: " + ex.getMessage());
		}
	}

	/**
	 * the Async task to constantly poll whether there's an incoming connection
	 *
	 */
	private class CheckExistingConnectionTask extends
			AsyncTask<String, Void, String> {
		@Override
		protected String doInBackground(String... params) {
			String response = "";
			int count = 0;
			while (count < 1) {
				Log.d("WebRTCActivity", "doInBackground chatInProgress: "
						+ chatInProgress);

				if (!chatInProgress) {
					Log.d("WebRTCActivity",
							"getExistingConnection: will connnect to "
									+ GET_URL);
					// call to the server to find out whether there is
					// an incoming connection through an async task
					SocialPlayServer server = SocialPlayServer.get(GET_URL);
					response = server.performGet();
					if (response != null && !chatInProgress) {
						if(previousChatRoom == null || !response.equals(previousChatRoom))
							break;
					}
				}
				sleep(1000);
			}
			return response;
		}
		
		@Override
		protected void onPostExecute(String result) {
			chatRoom = result;
			Log.d("WebRTCActivity", "ServerGetListener returns: " + result);
			alertConnection("Your friend wants to connect you to the Social Play",
					"Do you want to connect?");
		}
	}


	
	/**
	 * method to check whether there's an incoming connection
	 */
	private void checkExistingConnection() {
		Log.d("WebRTCActivity", "getExistingConnection: will connnect to "
				+ GET_URL);

		// call to the server to find out whether there is
		// an incoming connection through an async task
		SocialPlayServer server = SocialPlayServer.get(GET_URL);
		server.getTo(new ServerGetListener());

		// before checkExistingConnection calls back, the connect button remains
		// disabled
		connectButton.setText(R.string.wait);
		connectButton.setEnabled(false);
	}

	/**
	 * method to find a chat room to join, this is for the initiator of a video
	 * chat
	 */
	private void findChatRoomAndConnect() {
		Log.d("WebRTCActivity", "findChatRoom");
		SocialPlayServer server = SocialPlayServer.findChatRoom(FIND_URL);
		server.fetchTo(new ServerFindListener());
		connectButton.setText(R.string.wait);
		connectButton.setEnabled(false);
	}

	/**
	 * method to post the chat room to server so it knows one party already
	 * connected and the 2nd party can then be prompted to connect
	 * 
	 * @param serverUrl
	 * @param chatRoomId
	 */
	private void create(String serverUrl, String chatRoomId) {
		Log.d("WebRTCActivity", "create");
		SocialPlayServer server = SocialPlayServer
				.create(serverUrl, chatRoomId);
		server.createTo(new ServerCreateListener());
	}

	/**
	 * this is kind of hack for now to use a GET instead of a POST method to
	 * create an entry on the server side about the chat room currently joined
	 * 
	 * @param serverUrl
	 * @param chatRoomId
	 */
	private void createAsGet(String serverUrl, String chatRoomId) {
		Log.d("WebRTCActivity", "create");
		SocialPlayServer server = SocialPlayServer.createAsGet(serverUrl,
				chatRoomId);
		server.getTo(new ServerCreateAsGetListener());
	}

	/**
	 * Handle the result of an asynchronous Find request to server
	 **/
	private class ServerFindListener implements SocialPlayServer.Client {
		public void handleResponse(String chatRoomId, Exception error) {
			if (chatRoomId != null) {
				chatRoom = chatRoomId;
				Log.d("WebRTCActivity", "ServerFindListener returns: "
						+ chatRoomId);
				handleConnection();
			} else {
				Log.v("WebRTCActivity", "Server error: " + error);
				// There was either a network error or server error.
				// Show alert for the latter.
				alert("Failed to find a chat room to join!",
						"Please try again!");
				connectButton.setText(R.string.button_connect);
				connectButton.setEnabled(false);
				hangupButton.setEnabled(true);
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
				Log.d("WebRTCActivity", "ServerGetListener returns: "
						+ chatRoomId);
				alertConnection(
						"Your friend wants to connect you to the Social Play",
						"Do you want to connect?");
			} else {
				Log.v("WebRTCActivity", "ServerGetListener returns null.");
				// alert("WebRTCActivity",
				// "No existing connection");
				connectButton.setText(R.string.button_connect);
				connectButton.setEnabled(true);
				hangupButton.setEnabled(false);
			}
		}
	}

	/**
	 * Handle the result of an asynchronous CREATE request to server
	 * 
	 */
	private class ServerCreateListener implements SocialPlayServer.Client {
		public void handleResponse(String chatRoomId, Exception error) {
			Log.d("WebRTCActivity", "ServerCreateListener returns: "
					+ chatRoomId);
			if (error == null) {
				Log.d("WebRTCActivity", "Sent To Server successfully");
				connectButton.setText(R.string.button_connect);
				connectButton.setEnabled(true);
				// hangupButton.setEnabled(false);
			} else {
				alert("Failed to communicate to server! Error: "
						+ error.getMessage(),
						"Other party can't join this chat!");
			}
		}
	}

	/**
	 * 
	 * Handle the result of an asynchronous GET request to server It's kind of
	 * hack now to use a GET instead of a CREATE
	 */
	private class ServerCreateAsGetListener implements SocialPlayServer.Client {
		public void handleResponse(String chatRoomId, Exception error) {
			Log.d("WebRTCActivity", "ServerCreateAsGetListener returns: "
					+ chatRoomId);
			if (chatRoomId != null && error == null) {
				Log.d("WebRTCActivity", "Sent To Server successfully");
				loadWebView(WEBRTC_URL + "/r/" + chatRoomId);
			} else {
				alert("Failed to communicate to server! Error: "
						+ error.getMessage(),
						"Other party can't join this chat!");
			}
		}
	}

	/**
	 * initialize the web view
	 */
	private void initWebView() {
		WebSettings webSettings = webView.getSettings();
		webSettings.setJavaScriptEnabled(true);
		webSettings.setDatabaseEnabled(true);
		webSettings.setDomStorageEnabled(true);
		webSettings.setAppCacheEnabled(true);
		webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
		// webView.setWebViewClient(new WebViewClient() {
		webView.setWebChromeClient(new WebChromeClient() {

			@SuppressLint("NewApi")
			// @Override
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

	/**
	 * method to wait for a small period of time until a chat room is ready
	 * 
	 * @return
	 */
	private boolean waitForRoomIsReady() {
		int count = 5;
		boolean ready = false;
		while (!ready && count > 0) {
			if (chatRoom == null) {
				Log.d("WebRTCActivity", "found charRoom as null");
				// sleep(500);
				count--;
			} else
				ready = true;
		}

		return ready;
	}

	/**
	 * this is the main handler that handles connection
	 */
	private void handleConnection() {
		try {
			// construct the url to apputc with the chat room to connect to
			String url = WEBRTC_URL + "/r/" + chatRoom;
			Log.d("WebRTCActivity", "handleCheck: " + url);

			if (!acceptIncomingRequest) {
				Log.d("WebRTCActivity", "POST to server: " + BASE_URL
						+ " for chatRoom:" + chatRoom);

				// POST to server about the chat room joined
				createAsGet(SERVER_URL, chatRoom);
			} else {
				// now ready to connect
				loadWebView(url);
			}

		} catch (Exception ex) {
			Log.d("WebRTCActivity", "exception: " + ex.getMessage());
		}
	}

	/**
	 * method to use the web view to load the apprtc url and join a video chat
	 * room
	 * 
	 * @param url
	 */
	private void loadWebView(String url) {
		Log.d("WebRTCActivity", "loadWebView loaded url");
		initWebView();
		// have the webview load the url
		webView.loadUrl(url);
		chatInProgress = true;
		Log.d("WebRTCActivity", "loadWebView chatInProgress: " + chatInProgress);
		connectButton.setText(R.string.button_connect);
		connectButton.setEnabled(false);
		hangupButton.setEnabled(true);
	}

	/**
	 * Show an alert dialog with message. user can either click OK or cancel no
	 * real action is triggered
	 * 
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
	 * pops up an alert dialog with 2 messages, one as the header and the other
	 * as the message to be shown in the dialog. if user clicks on NO, it enable
	 * the connect button on screen if user clicks on YES, it connects user to
	 * the incoming request
	 * 
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
								handleConnection();
							}
						});
		AlertDialog alert = builder.create();
		alert.show();
	}
}
