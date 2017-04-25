package co.com.assist.dp.tester;

import java.io.IOException;
import java.io.StringReader;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;

import javax.net.ssl.SSLContext;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.ssl.TrustStrategy;
import org.apache.http.util.EntityUtils;
import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

public final class HttpClient {

	private static final CloseableHttpClient httpClient = getHttpClient();

	public static final XMLOutputter outputter = new XMLOutputter(Format.getCompactFormat().setOmitDeclaration(true));

	private static long elapsedTime = 0L;

	private static Date resquestDate = null;

	private HttpClient() {
	}

	private static CloseableHttpClient getHttpClient() {
		try {
			SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(new TrustStrategy() {
				@Override
				public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
					return true;
				}
			}).build();
			HttpClientBuilder builder = HttpClients.custom();
			builder.setSSLContext(sslContext);
			builder.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
			return builder.build();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static Document sendRequest(String url, Document request, Map<String, String> headers) {
		String req = outputter.outputString(request);

		HttpPost httpPost = new HttpPost(url);
		httpPost.setEntity(new StringEntity(req, ContentType.create("text/xml", "UTF-8")));

		for (Entry<String, String> entry : headers.entrySet()) {
			httpPost.setHeader(entry.getKey(), entry.getValue());
		}

		resquestDate = Calendar.getInstance().getTime();
		long start = System.currentTimeMillis();
		try (CloseableHttpResponse httpResponse = httpClient.execute(httpPost)) {
			elapsedTime = System.currentTimeMillis() - start;
			String res = EntityUtils.toString(httpResponse.getEntity(), "UTF-8");
			return new SAXBuilder().build(new StringReader(res));
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (JDOMException e) {
			return null;
		}
	}

	public static long getElapsedTime() {
		return elapsedTime;
	}

	public static Date getResquestDate() {
		return resquestDate;
	}

}