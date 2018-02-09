package com.aossas.ws.tc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.Inet4Address;
import java.net.UnknownHostException;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;
import javax.xml.ws.Endpoint;

@WebService
public class TempConvert {

	public static void main(String[] args) throws UnknownHostException {
		int port = 8080;
		if (args.length > 0) {
			port = Integer.valueOf(args[0]);
		}

		String url = "http://" + Inet4Address.getLocalHost().getHostAddress() + ":" + port + "/"
				+ TempConvert.class.getSimpleName();
		Endpoint.publish(url, new TempConvert());
		System.out.println(url);
	}

	@WebMethod
	@WebResult(name = "celsius")
	public BigDecimal FahrenheitToCelsius(@WebParam(name = "fahrenheit") BigDecimal fahrenheit) {
		BigDecimal celsius = fahrenheit.subtract(BigDecimal.valueOf(32)).multiply(BigDecimal.valueOf(5))
				.divide(BigDecimal.valueOf(9), 1, RoundingMode.HALF_EVEN);
		System.out.println("FahrenheitToCelsius(" + fahrenheit + "): " + celsius);
		return celsius;
	}

	@WebMethod
	@WebResult(name = "fahrenheit")
	public BigDecimal CelsiusToFahrenheit(@WebParam(name = "celsius") BigDecimal celsius) {
		BigDecimal fahrenheit = celsius.multiply(BigDecimal.valueOf(9))
				.divide(BigDecimal.valueOf(5), 1, RoundingMode.HALF_EVEN).add(BigDecimal.valueOf(32));
		System.out.println("CelsiusToFahrenheit(" + celsius + "): " + fahrenheit);
		return fahrenheit;
	}

}
