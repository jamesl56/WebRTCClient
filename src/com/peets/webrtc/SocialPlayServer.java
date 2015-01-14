package com.peets.webrtc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.Locale;
import java.util.Collections;

import org.json.JSONObject;

import com.chinamobile.customerservice.server.CustomerServiceBuilders;
import com.chinamobile.customerservice.server.CustomerServiceContext;
import com.chinamobile.customerservice.server.CustomerServiceRequestBuilders;
import com.linkedin.r2.transport.common.Client;
import com.linkedin.r2.transport.common.bridge.client.TransportClient;
import com.linkedin.r2.transport.common.bridge.client.TransportClientAdapter;
import com.linkedin.r2.transport.http.client.HttpClientFactory;
import com.linkedin.restli.client.CreateRequest;
import com.linkedin.restli.client.Response;
import com.linkedin.restli.client.ResponseFuture;
import com.linkedin.restli.client.RestClient;
import com.linkedin.restli.common.EmptyRecord;
import com.linkedin.restli.common.IdResponse;
import android.os.Handler;
import android.util.Log;

/**
 * the social play server handles three scenarios: 1. find a chat room 2. get an
 * existing room if there's any, which indicates there's an incoming connection
 * 3. post to server to indicate one user joins to the chat room so if someone
 * else calls 2
 * 
 * calling an interface when done. Performs the load over a separate thread so
 * that it doesn't block the main UI.
 **/
public class SocialPlayServer {
	/** Supplies SocialPlayServer response back to client. **/
	public interface Client {

		public void handleResponse(String chatRoomId, Exception error);
	}

	/**
	 * the action to call to server to find a chat room via async task here it
	 * connects to the server via the url passed in
	 * 
	 * @param baseUrl
	 * @return
	 * @throws IllegalArgumentException
	 */
	public static SocialPlayServer findChatRoom(String baseUrl)
			throws IllegalArgumentException {
		try {
			URL url = new URL(baseUrl);
			HttpURLConnection request = (HttpURLConnection) url
					.openConnection();
			request.setConnectTimeout(CONNECT_TIMEOUT);
			request.setReadTimeout(READ_TIMEOUT);
			return new SocialPlayServer(request, null);
		} catch (IOException e) {
			throw new IllegalArgumentException(e);
		} catch (ClassCastException e) {
			throw new IllegalArgumentException("URL must be HTTP: " + baseUrl,
					e);
		}
	}

	/**
	 * this is a similar GET operation as the find action
	 * 
	 * @param baseUrl
	 * @return
	 * @throws IllegalArgumentException
	 */
	public static SocialPlayServer get(String baseUrl)
			throws IllegalArgumentException {
		try {
			URL url = new URL(baseUrl);
			HttpURLConnection request = (HttpURLConnection) url
					.openConnection();
			request.setConnectTimeout(CONNECT_TIMEOUT);
			request.setReadTimeout(READ_TIMEOUT);
			return new SocialPlayServer(request, null);
		} catch (IOException e) {
			throw new IllegalArgumentException(e);
		} catch (ClassCastException e) {
			throw new IllegalArgumentException("URL must be HTTP: " + baseUrl,
					e);
		}
	}

	/**
	 * this is a hack to post to server, it reuses the get methods to
	 * send response to client
	 * @param baseUrl
	 * @return
	 * @throws IllegalArgumentException
	 */
	public static SocialPlayServer createAsGet(String serverUrl, String chatRoomId)
			throws IllegalArgumentException {
		try {
			String targetUrl = serverUrl + "/" + chatRoomId;
			URL url = new URL(targetUrl);
			HttpURLConnection request = (HttpURLConnection) url
					.openConnection();
			request.setConnectTimeout(CONNECT_TIMEOUT);
			request.setReadTimeout(READ_TIMEOUT);
			return new SocialPlayServer(request, null);
		} catch (IOException e) {
			throw new IllegalArgumentException(e);
		} 
	}
	
	/**
	 * this is to call CREATE to server
	 * @param serverUrl
	 * @param chatRoomId
	 * @return
	 */
	public static SocialPlayServer create(String serverUrl, String chatRoomId) {
		HttpURLConnection request = null;
		try {
			URL url = new URL(serverUrl);
			request = (HttpURLConnection) url.openConnection();
			Log.d("SocialPlayServer", "in create: " + serverUrl + " "
					+ chatRoomId);
			final HttpClientFactory http = new HttpClientFactory();
			final TransportClient transportClient = http.getClient(Collections
					.<String, String> emptyMap());
			// create an abstraction layer over the actual client, which
			// supports both REST and RPC
			final com.linkedin.r2.transport.common.Client r2Client = new TransportClientAdapter(
					transportClient);

			// Create a RestClient to talk to server
			RestClient restClient = new RestClient(r2Client, serverUrl);
			CustomerServiceBuilders builders = new CustomerServiceBuilders();

			CustomerServiceContext csc = new CustomerServiceContext()
					.setChatRoomId(chatRoomId).setTimestamp(
							new Date().getTime());
			CreateRequest<CustomerServiceContext> createReq = builders.create().input(csc).build();
			Log.d("SocialPlayServer", "in create: will send create request: " + createReq.toString());
			ResponseFuture<EmptyRecord> createFuture = restClient
					.sendRequest(createReq);
			Log.d("SocialPlayServer", "request sent");
			Response<EmptyRecord> createResp = createFuture.getResponse();
			int statusCode = createResp.getStatus();
			Log.d("SocialPlayServer", "create receives status code: "
					+ statusCode);

			return new SocialPlayServer(request, null);
		} catch (IOException e) {
			throw new IllegalArgumentException(e);
		} catch (Exception ex) {
			Log.d("SocialPlayServer", "in create: " + ex.getMessage());
			for (StackTraceElement elem : ex.getStackTrace()) {
				Log.d("SocialPlayServer", "in create: " + elem.toString());
			}

			return new SocialPlayServer(request, null);
		}
	}

	// connection parameters
	private static final int CONNECT_TIMEOUT = 5 * 1000; // milliseconds
	private static final int READ_TIMEOUT = 5 * 1000;

	/**
	 * Create a loader for a URLConnection that returns the response data to a
	 * client.
	 * 
	 * @param request
	 * @param postData
	 */
	private SocialPlayServer(HttpURLConnection request, byte[] postData) {
		this.connection = request;
		this.postData = postData;
	}

	private Client client; // becomes null after cancel()
	private HttpURLConnection connection;
	private byte[] postData; // becomes null after getResponseStream()

	/**
	 * Stop the loading. This should only be called from the same thread that
	 * called start().
	 **/
	public void cancel() {
		// TODO: figure out a way to implement cancel()
		client = null;
		// connection.disconnect();
	}

	/**
	 * this is paired with the find method above which will start a separate
	 * thread to complete the find action by fetching the http response
	 * 
	 * @param client
	 */
	public void fetchTo(Client client) {
		this.client = client;
		// TODO: prevent starting twice
		final Handler callingThread = new Handler();
		Thread reader = new Thread(connection.getURL().toString()) {
			@Override
			public void run() {
				performFind(callingThread);
			}
		};
		reader.start();
	}

	/**
	 * complete the find action and post the result to a client to be consumed
	 * in the UI thread to determine what to do
	 * 
	 * @param callingThread
	 */
	private void performFind(Handler callingThread) {
		try {
			// the find action requires a POST method
			connection.setRequestMethod("POST");
			// Wait for the response.
			// Note that getInputStream will throw exception for non-200 status.
			InputStream response = connection.getInputStream();
			byte[] data = readAllBytes(response);
			// Extract the token from JSON.
			String body = new String(data, "UTF8");
			JSONObject json = new JSONObject(body);
			Log.d("SocialPlayServer", "performFind received: " + body);
			final String chatRoomId = json.getString("value");
			Log.d("SocialPlayServer", "performFind received chatRoomId: "
					+ chatRoomId);
			// Give it back to the client.
			callingThread.post(new Runnable() {
				public void run() {
					if (client != null) {
						Log.d("SocialPlayServer",
								"performFind handleResponse will send chatRoomId: "
										+ chatRoomId);
						client.handleResponse(chatRoomId, null);
					}
				}
			});
		} catch (final Exception ex) {
			callingThread.post(new Runnable() {
				public void run() {
					if (client != null) {
						Log.d("SocialPlayServer",
								"performFind handleResponse will send null");
						client.handleResponse(null, ex);
					}
				}
			});
		}
		// XXX: Should we close the connection?
		// finally { connection.close(); }
	}

	/**
	 * similar to the find this is paired with get method
	 * 
	 * @param client
	 */
	public void getTo(Client client) {
		this.client = client;

		final Handler callingThread = new Handler();
		Thread reader = new Thread(connection.getURL().toString()) {
			@Override
			public void run() {
				performGet(callingThread);
			}
		};
		reader.start();
	}

	/**
	 * fetch the http response from get method and send to client
	 * 
	 * @param callingThread
	 */
	private void performGet(Handler callingThread) {
		try {
			// Post the credentials.
			connection.setRequestMethod("GET");
			// Wait for the response.
			// Note that getInputStream will throw exception for non-200 status.
			InputStream response = connection.getInputStream();
			byte[] data = readAllBytes(response);
			// Extract the token from JSON.
			String body = new String(data, "UTF8");
			JSONObject json = new JSONObject(body);
			Log.d("SocialPlayServer", "performGet received: " + body);
			final String chatRoomId = json.getString("chatRoomId");
			Log.d("SocialPlayServer", "performGet received chatRoomId: "
					+ chatRoomId);
			// Give it back to the client.
			callingThread.post(new Runnable() {
				public void run() {
					if (client != null) {
						Log.d("SocialPlayServer",
								"handleResponse will send chatRoomId: "
										+ chatRoomId);
						client.handleResponse(chatRoomId, null);
					}
				}
			});
		} catch (final Exception ex) {
			callingThread.post(new Runnable() {
				public void run() {
					if (client != null) {
						Log.d("SocialPlayServer",
								"handleResponse will send null");
						client.handleResponse(null, ex);
					}
				}
			});
		}
	}

	/**
	 * for create
	 * 
	 * @param client
	 */
	public void createTo(Client client) {
		this.client = client;

		final Handler callingThread = new Handler();
		Thread reader = new Thread(connection.getURL().toString()) {
			@Override
			public void run() {
				performCreate(callingThread);
			}
		};
		reader.start();
	}

	/**
	 * fetch the create response and send back to client
	 * 
	 * @param callingThread
	 */
	private void performCreate(Handler callingThread) {
		try {
			// Give it back to the client.
			callingThread.post(new Runnable() {
				public void run() {
					if (client != null) {
						Log.d("SocialPlayServer", "performCreate");
						client.handleResponse(null, null);
					}
				}
			});
		} catch (final Exception ex) {
			callingThread.post(new Runnable() {
				public void run() {
					if (client != null) {
						Log.d("SocialPlayServer", "performCreate");
						client.handleResponse(null, ex);
					}
				}
			});
		}
	}

	private static final int BLOCK_SIZE = 16 * 1024;

	/**
	 * Reads the stream until EOF. Returns all the bytes read.
	 * 
	 * @param input
	 *            stream to read
	 * @param maxLength
	 *            maximum number of bytes to read
	 * @return all the bytes from the steam
	 * @throws IOException
	 * @throws InputTooLargeException
	 *             when length exceeds
	 **/
	private static byte[] readAllBytes(InputStream input) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream(BLOCK_SIZE);
		byte[] block = new byte[BLOCK_SIZE];
		int n;
		while ((n = input.read(block)) > 0)
			buffer.write(block, 0, n);
		return buffer.toByteArray();
	}
}
