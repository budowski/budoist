package budo.budoist.services;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import javax.net.ssl.SSLException;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.google.gson.Gson;

import android.util.Log;

/**
 * Implements a Json server - for encoding / decoding JSON requests over a HTTP server
 * @author Yaron Budowski
 *
 */
public class JsonServer {
	
	private final static String TAG = "JsonServer";
	
	private final static int CONNECTION_TIMEOUT = 10000;
	private final static int SOCKET_DATA_RECV_TIMEOUT = 10000;
	
	private String mBaseUrl;
	
	// Number of Ms to wait before retrying an online server call
	private static final long RETRY_ONLINE_WAIT_TIME = 1500;
	// Max number of times we should retry calling an online server method
	private static final int MAX_ONLINE_RETRY_COUNT = 3;
	

	public JsonServer(String baseUrl) {
		
		mBaseUrl = baseUrl;
		
		// Remove any existing protocols
		if (mBaseUrl.startsWith("http://"))
			mBaseUrl = mBaseUrl.substring("http://".length());
		if (mBaseUrl.startsWith("https://"))
			mBaseUrl = mBaseUrl.substring("https://".length());
		
		if (!mBaseUrl.endsWith("/"))
			mBaseUrl = mBaseUrl + "/";
	}
	
	/**
	 * Sends out a command, with given parameters, and returns key-value
	 * results. If the command fails in case of a connection/socket error, it retries several
	 * times, and if still fails - returns null.
	 * 
	 * @param subUrl the url to use (will be appended to baseUrl)
	 * @param parameters key/value of the parameters
	 * @param isSecure should we use HTTPS?
	 * @return key/value results
	 */
	public Object sendCommand(
			String subUrl, Hashtable<String, Object> parameters,
			Boolean isSecure) {
		int retryCount = 0;
		
		while (retryCount < MAX_ONLINE_RETRY_COUNT) {
			Object retVal = sendCommandOnce(subUrl, parameters, isSecure);
			
			if (retVal != null) {
				// Command was sent successfully
				return retVal;
			}
			
			// A socket/connection exception was raised
			retryCount++;
			
			// Wait a little before retrying
			try {
				Thread.sleep(RETRY_ONLINE_WAIT_TIME * retryCount);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	
		// Retried too many times - return null
		return null;
	}

	
	/**
	 * Sends out a command once, with given parameters, and returns key-value
	 * results.
	 * 
	 * @param subUrl the url to use (will be appended to baseUrl)
	 * @param parameters key/value of the parameters
	 * @param isSecure should we use HTTPS?
	 * @return key/value results
	 */
	public Object sendCommandOnce(
			String subUrl, Hashtable<String, Object> parameters,
			Boolean isSecure) {
		String fullUrl;
		
		if (isSecure)
			fullUrl = "https://" + mBaseUrl + subUrl;
		else
			fullUrl = "http://" + mBaseUrl + subUrl;

		
		InputStream streamContent;
		String resultData;
		
		// Next, send a HTTP Request to the server
		
		try {
			String urlParams = "";
			String urlWithParams;
			
			// Prepare arguments for GET
			
			Gson gson = new Gson();
			
	        for (Enumeration<String> e = parameters.keys(); e.hasMoreElements();) {
	        	String key = e.nextElement();
	        	Object value = parameters.get(key);
	        	String strValue;
	        	
	        	if ((value instanceof Hashtable<?, ?>) || (value instanceof ArrayList<?>)) {
	        		// A complex type - needed to be represented as JSON
	        		strValue = gson.toJson(value);
	        	} else {
	        		// Basic type - use as-is (since Todoist API accepts singular basic types, such
	        		// as strings, not as their JSON counterpart - e.g. mystring and not "mystring")
	        		strValue = value.toString();
	        	}
	        	
				urlParams += key + "=" + URLEncoder.encode(strValue, "UTF-8");
	        	if (e.hasMoreElements())
	        		urlParams += "&";
	        }
	        
	        if (urlParams != "")
	        	urlWithParams = fullUrl + "?" + urlParams;
	        else
	        	urlWithParams = fullUrl;
	        
	        Log.d(TAG, String.format("Executing GET request url = %s ", urlWithParams));

			HttpGet httpGet = new HttpGet(urlWithParams);
			
			// Set timeout
			HttpParams params = new BasicHttpParams();
			HttpConnectionParams.setConnectionTimeout(params, CONNECTION_TIMEOUT);
			HttpConnectionParams.setSoTimeout(params, SOCKET_DATA_RECV_TIMEOUT);
			
			HttpClient client = new DefaultHttpClient(params);
			
			HttpResponse response = client.execute(httpGet);
			streamContent = response.getEntity().getContent();
			
			resultData = readToEnd(streamContent);
			
			Log.d(TAG, String.format("Result data: %s", resultData));
			
			int status = response.getStatusLine().getStatusCode();
			if (status != 200) {
				Log.e(TAG, String.format("Expected 200 http status code. %d received - returning null", status));
				return null;
			}

			  // Parse results (returned as JSON string)
			  return jsonDecodeString(resultData);
		  
		  } catch (SSLException e) {
			  // SSL Certificate problems - try without encryption (could happen since Todoist's
			  // certificates were renewed and this still causes problems for some devices)
			  return sendCommand(subUrl, parameters, false);
			  
		  } catch (Exception e) {
			  // In case of error - we'll simply return null
			  Log.e(TAG, "Error while executing GET", e);
			  return null;
		  }
		  
	}
	
	

	/**
	 * Decodes a JSON string into a hashtable that contains either string
	 * values, ArrayList or inner Hash tables
	 * 
	 * @param input
	 *            the JSON string to decode
	 * @return
	 */
	private Object jsonDecodeString(String input) {
		try {
			Object main = (new JSONTokener(input)).nextValue();
			
			if ((main instanceof JSONObject) || (main instanceof JSONArray)) {
				return jsonDecodeObject(main);
			} else {
				// String or other basic type
				return main;
			}

		} catch (Exception exc) {
			Log.e(TAG, String.format("Invalid json data received: %s", input), exc);
			return null;
		}

	}

	/**
	 * Decodes a JSON object into a hashtable that contains either string
	 * values, ArrayList or inner Hash tables
	 * 
	 * @param input
	 *            the JSONObject to decode
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private Object jsonDecodeObject(Object input) {
		if (input instanceof JSONArray) {
			// JSON array - convert to ArrayList
			List<Object> arr = new ArrayList<Object>();

			try {
				JSONArray jarray = (JSONArray)input;
				for (int i = 0; i < jarray.length(); i++) {
					// Each item in the array is in itself a sub-JSON object
					arr.add(jsonDecodeObject(jarray.get(i)));
				}
			} catch (Exception exc) {
				// In case of error - we'll simply return null
				return null;
			}

			return arr;
			
		} else if (input instanceof JSONObject) {
			// JSON Object (i.e. dictionary)
			
			Hashtable<String, Object> values = new Hashtable<String, Object>();
			JSONObject inputDict = (JSONObject)input;

			try {
				for (Iterator<String> iter = inputDict.keys(); iter.hasNext();) {
					String key = iter.next();
					Object value = inputDict.get(key);
					
					// In case the key exists but the value is null
					if (!inputDict.isNull(key))
						values.put(key, jsonDecodeObject(value));
				}
			} catch (Exception exc) {
				// In case of error - we'll simply return null
				return null;
			}

			return values;
			
		} else {
			// Simple string/int/... value
			return input;
		}
	}
	
	private String readToEnd(InputStream is) {
		return readToEnd(is, "utf-8");
	}
	
	/**
	 * Reads data from an InputStream instance
	 * 
	 * @param is
	 *            the InputStream instance
	 * @return the data that was read
	 */
	private String readToEnd(InputStream is, String charset) {
		BufferedReader reader = new BufferedReader(new InputStreamReader(is, Charset.forName(charset)),
				8 * 1024);
		StringBuilder sb = new StringBuilder();

		String line = null;
		try {
			while ((line = reader.readLine()) != null) {
				sb.append(line + "\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				is.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return sb.toString();
	}
}
