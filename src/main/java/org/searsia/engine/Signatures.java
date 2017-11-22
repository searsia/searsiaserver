package org.searsia.engine;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TimeZone;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;


public class Signatures {

	private static final String UTF8_CHARSET = "UTF-8";
	private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";

	/**
	 * Signing a web request using Amazon's HmacSHA256. 
	 * Example code from:
	 *   http://docs.aws.amazon.com/AWSECommerceService/latest/DG/AuthJavaSampleSig2.html
	 * For more information:
	 *   https://tools.ietf.org/html/rfc2104
	 */
    public static String sign(String urlString, String algorithm, String secretKey) {
    	if (algorithm != null && !algorithm.equals(HMAC_SHA256_ALGORITHM)) {
    		throw new RuntimeException("Unsupported signature: " + algorithm);
    	}
    	if (secretKey == null) {
    		throw new RuntimeException("Signature key not found.");
    	}
    	Mac mac = null;
    	URL url = null;
		try {
			byte[] secretyKeyBytes = secretKey.getBytes(UTF8_CHARSET);
			SecretKeySpec secretKeySpec = new SecretKeySpec(secretyKeyBytes, HMAC_SHA256_ALGORITHM);
			mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
			mac.init(secretKeySpec);
			url = new URL(urlString);
		} catch (Exception e) { // UnsupportedEncodingException, NoSuchAlgorithmException, InvalidKeyException, MalformedURLException
			throw new RuntimeException(e);
		}
		String protocol = url.getProtocol();
        String host     = url.getHost();
        String path     = url.getPath();
        String query    = url.getQuery();

    	Map<String, String> params = new HashMap<String, String>();
    	for (String pair: query.split("&")) {
    		String[] keyValue = pair.split("=");
    		params.put(keyValue[0], keyValue[1]);
    	}
   		params.putIfAbsent("Timestamp", timestamp());
    	
	    SortedMap<String, String> sortedParamMap = new TreeMap<String, String>(params);
	    String canonicalQS = canonicalize(sortedParamMap);
	    String toSign = "GET\n" + host + "\n" + path + "\n" + canonicalQS;
	    String hmac = hmac(mac, toSign);
	    String sig = percentEncodeRfc3986(hmac);
	    return protocol + "://" + host + path + "?" + canonicalQS + "&Signature=" + sig;
    }

	private static String timestamp() {
		String timestamp = null;
		Calendar cal = Calendar.getInstance();
		DateFormat dfm = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		dfm.setTimeZone(TimeZone.getTimeZone("GMT"));
		timestamp = dfm.format(cal.getTime());
		return timestamp;
	}

    private static String hmac(Mac mac, String stringToSign) {
    	String signature = null;
    	byte[] data;
    	byte[] rawHmac;
    	try {
    		data = stringToSign.getBytes(UTF8_CHARSET);
    		rawHmac = mac.doFinal(data);
    		Base64.Encoder encoder = Base64.getEncoder();
    		signature = new String(encoder.encode(rawHmac));
    	} catch (UnsupportedEncodingException e) {
    		throw new RuntimeException(e);
    	}
    	return signature;
	}

	private static String canonicalize(SortedMap<String, String> sortedParamMap) {
		if (sortedParamMap.isEmpty()) {
			return "";
		}
		StringBuffer buffer = new StringBuffer();
		Iterator<Map.Entry<String, String>> iter = sortedParamMap.entrySet().iterator();
		while (iter.hasNext()) {
			Map.Entry<String, String> kvpair = iter.next();
			buffer.append(percentEncodeRfc3986(kvpair.getKey()));
			buffer.append("=");
			buffer.append(percentEncodeRfc3986(kvpair.getValue()));
			if (iter.hasNext()) {
				buffer.append("&");
			}
		}
		String canonical = buffer.toString();
		return canonical;
	}

	private static String percentEncodeRfc3986(String s) {
		String out;
		try {
			out = URLEncoder.encode(s, UTF8_CHARSET)
					.replace("+", "%20")
					.replace("*", "%2A")
					.replace("%7E", "~");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
		return out;
	}

}
