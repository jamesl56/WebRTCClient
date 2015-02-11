package com.peets.webrtc;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

/*
 * the main activity to host the webRTC connectivity
 */
public class GuidedPlayActivity extends Activity {
	private Button hangupButton = null;
	private ImageView imageView = null;
	private WebView webView = null;
	private String chatRoom = null;
	private TextView scoreView = null;
	private TextView timerView = null;
	private MediaPlayer mp = null;

    private CountDownTimer countDownTimer;
    private final long startTime = 30 * 1000;
    private final long interval = 1 * 1000;
    private int challengeCount = 1;
    private int score = 0;
    private static int challengeLimit = 2;

    public class MyCountDownTimer extends CountDownTimer {
        public MyCountDownTimer(long startTime, long interval) {
            super(startTime, interval);
        }

        @Override
        public void onFinish() {
        	challengeCount++;
			if (challengeCount <= challengeLimit) {
				score += 10;
				displayScore();
				playAudio();
				startTimer(startTime, interval);
			}
			else
			{
				timerView.setText("");
			}
        }

        @Override
        public void onTick(long millisUntilFinished) {
        	timerView.setText(R.string.remaining);
        	timerView.setText(timerView.getText() + "" + millisUntilFinished / 1000);
        }
    }
    
    private void displayScore()
    {
		scoreView.setText(R.string.score);		
		scoreView.setText(scoreView.getText() + "" + score);    	
    }
    
	/**
	 * Called when the activity is first created. This is where we'll hook up
	 * our views in XML layout files to our application.
	 **/
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// show a progress bar
		getWindow().requestFeature(Window.FEATURE_PROGRESS);

		Log.e("GuidedPlayActivity", "On create");
		setContentView(R.layout.guidedplay);

		hangupButton = (Button) findViewById(R.id.hangup_button);
		hangupButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Log.e("GuidedPlayActivity", "hangupButton OnClick");

				// when clicked, it hang up the call and clean the webview
				clearWebViewCache();
			}
		});


		scoreView = (TextView) findViewById(R.id.score);
		scoreView.setText(R.string.score);
		displayScore();
		timerView = (TextView) findViewById(R.id.timer);

		playAudio();
		startTimer(startTime, interval);
		imageView = (ImageView) findViewById(R.id.guidedImageView);
		imageView.setBackgroundResource(R.drawable.start);
		imageView.post(new Runnable() {
			@Override
			public void run() {
				AnimationDrawable frameAnimation = (AnimationDrawable) imageView
						.getBackground();
				frameAnimation.start();
			}
		});
		// This will show a web view that hosts the video chat
		webView = (WebView) findViewById(R.id.guidedWebView);
		
		Intent intent = getIntent();
	    
		chatRoom = intent.getStringExtra(PlaydateActivity.CHATROOM);

		Log.e("GuidedPlayActivity", "retrieved chatRoom: " + chatRoom + " from intent");
	}

	private void playAudio()
	{

		mp = MediaPlayer.create(this, (challengeCount == 1) ? R.raw.challenge1
				: R.raw.challenge2);

		long duration = (long) mp.getDuration() + 500;
		mp.start();
//		PlaydateActivity.sleep(duration);	
	}
	
	private void startTimer(long period, long interval)
	{
        countDownTimer = new MyCountDownTimer(period, interval);
        timerView.setText(String.valueOf(startTime / 1000));
        countDownTimer.start();
	}
	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	/**
	 * @Title: clearWebViewCache
	 * @Description: Hang up method , clear webview cache
	 * @param
	 * @return void
	 * @throws
	 */
	protected void clearWebViewCache() {
		Log.e("GuidedPlayActivity", "clearWebViewCache will clear view");
		// before reload URL clear the web cache make sure setting info clear .
		webView.clearCache(true);

		hangupButton.setEnabled(false);

		// reliably reset the view state and release page resources
		webView.loadUrl("about:blank");

		PlaydateActivity.previousChatRoom = chatRoom; // remember the previous chat room
		Log.e("GuidedPlayActivity",
				"clearWebViewCache preserve previous chat room: "
						+ PlaydateActivity.previousChatRoom);

		startActivity(new Intent(getApplicationContext(),
				PlaydateActivity.class));
	}

	/**
	 * Called when the activity is coming to the foreground. This is where we
	 * will check whether there's an incoming connection.
	 **/
	@Override
	protected void onStart() {
		super.onStart();

		Log.e("GuidedPlayActivity", "on start: chatRoom = " + chatRoom);
		
		loadWebView(PlaydateActivity.WEBRTC_URL + "/r/" + chatRoom);
	}

	/**
	 * utility to do sleep
	 * 
	 * @param milliseconds
	 */
	public void sleep(long milliseconds) {
		try {
			Log.e("GuidedPlayActivity", "will sleep " + milliseconds
					+ "milliseconds");
			Thread.sleep(milliseconds);
		} catch (Exception ex) {
			Log.e("GuidedPlayActivity",
					"sleep encounters exception: " + ex.getMessage());
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
	}


	/**
	 * method to use the web view to load the apprtc url and join a video chat
	 * room
	 * 
	 * @param url
	 */
	private void loadWebView(String url) {
		Log.e("GuidedPlayActivity", "loadWebView loaded url: " + url);
		initWebView();

		webView.setWebChromeClient(new WebChromeClient() {

			@SuppressLint("NewApi")
			@Override
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

		// have the webview load the url
		webView.loadUrl(url);

		hangupButton.setEnabled(true);
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
							}
						})
				.setPositiveButton(android.R.string.yes,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								Log.e("GuidedPlayActivity",
										"OK clicked, will hangup");
								
								clearWebViewCache();
							}
						});
		AlertDialog alert = builder.create();
		alert.show();
	}
}
