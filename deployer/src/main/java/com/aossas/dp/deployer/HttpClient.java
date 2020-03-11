package com.aossas.dp.deployer;

import java.io.File;
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
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.XMLOutputter;

public final class HttpClient {

	private CloseableHttpClient httpClient;

	private static final XMLOutputter outputter = new XMLOutputter();

	private long elapsedTime = 0L;

	private Date resquestDate = null;

	public HttpClient(Deploy deploy) {
		String keystoreFile = deploy.getProps().getProperty("ssl.keystore.file");
		String keystorePassword = deploy.decrypt(deploy.getProps().getProperty("ssl.keystore.password"));

		try {
			SSLContext sslContext = SSLContexts.custom().loadKeyMaterial(new File(keystoreFile), keystorePassword.toCharArray(), keystorePassword.toCharArray())
					.loadTrustMaterial(new TrustStrategy() {
						@Override
						public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
							return true;
						}
					}).build();
			HttpClientBuilder builder = HttpClients.custom();
			builder.setSSLContext(sslContext);
			builder.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
			httpClient = builder.build();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public Document sendRequest(String url, Document request, Map<String, String> headers) throws Exception {
		String req = outputter.outputString(request);

		HttpPost httpPost = new HttpPost(url);
		httpPost.setEntity(new StringEntity(req, ContentType.create("text/xml", "UTF-8")));

		for (Entry<String, String> entry : headers.entrySet()) {
			httpPost.setHeader(entry.getKey(), entry.getValue());
		}

		resquestDate = Calendar.getInstance().getTime();
		long start = System.currentTimeMillis();
		try (CloseableHttpResponse httpResponse = httpClient.execute(httpPost)) {
			String res = EntityUtils.toString(httpResponse.getEntity(), "UTF-8");
			return new SAXBuilder().build(new StringReader(res));
		} finally {
			elapsedTime = System.currentTimeMillis() - start;
		}
	}

	public long getElapsedTime() {
		return elapsedTime;
	}

	public Date getResquestDate() {
		return resquestDate;
	}

	public void close() {
		try {
			httpClient.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}