package co.com.assist.dp.templater;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;

public class BuildProperties {

	private static final Namespace wsdlns = Namespace.getNamespace("wsdl", "http://schemas.xmlsoap.org/wsdl/");
	private static final Namespace soapns = Namespace.getNamespace("soap", "http://schemas.xmlsoap.org/wsdl/soap/");

	public static void main(String[] args) throws Exception {
		File wsdl = new File("C:\\Users\\manji\\Documents\\assist\\multibank\\workspace\\mb.template\\etc\\wsdl");
		Collection<File> files = FileUtils.listFiles(wsdl, new String[] { "wsdl" }, false);
		for (File f : files) {
			processFile(f);
		}
	}

	private static void processFile(File f) throws JDOMException, IOException {
		Collection<String> lines = new ArrayList<String>();
		Document wsdl = new SAXBuilder().build(f);

		String wsdlName = f.getName();
		String targetNamespace = wsdl.getRootElement().getAttributeValue("targetNamespace");
		String name = targetNamespace.substring(29, targetNamespace.length() - 5).replaceAll("/", ".").toLowerCase();
		lines.add("name=" + name);
		lines.add("wsdl=" + wsdlName);
		lines.add("namespace=" + targetNamespace);

		String service_name = wsdl.getRootElement().getChild("service", wsdlns).getAttributeValue("name");
		lines.add("service_name=" + service_name);

		String port_name = wsdl.getRootElement().getChild("service", wsdlns).getChild("port", wsdlns)
				.getAttributeValue("name");
		lines.add("port_name=" + port_name);

		String location = wsdl.getRootElement().getChild("service", wsdlns).getChild("port", wsdlns)
				.getChild("address", soapns).getAttributeValue("location");
		String service_uri = new URL(location).getPath();
		lines.add("service_uri=" + service_uri);

		Map<String, Integer> portMapping = new HashMap<>();
		portMapping.put("/services/accounts/AccountStatementInquiry", 7843);
		portMapping.put("/services/accounts/CheckBookManagement", 7844);
		portMapping.put("/services/accounts/CheckBookStatusInquiry", 7843);
		portMapping.put("/services/accounts/CreditCardStatementInquiry", 7843);
		portMapping.put("/services/accounts/CustomerAccountInquiry", 7844);
		portMapping.put("/services/accounts/GeneralBalanceCompInquiry", 7843);
		portMapping.put("/services/accounts/LoanDetailInquiry", 7843);
		portMapping.put("/services/customers/AccountTransfersAdd", 7844);
		portMapping.put("/services/customers/CustAcctExecutiveInquiry", 7843);
		portMapping.put("/services/customers/CustomerCreditInquiry", 7843);
		portMapping.put("/services/customers/CustomerCreditManagement", 7843);
		portMapping.put("/services/customers/CustomerDepositInquiry", 7843);
		portMapping.put("/services/customers/CustomerInformationInquiry", 7844);
		portMapping.put("/services/customers/CustomerProductInquiry", 7844);
		portMapping.put("/services/customers/PaymentButtonAdd", 7844);
		portMapping.put("/services/customers/UserAdminAdd", 7844);
		portMapping.put("/services/customers/ValidateUserInquiry", 7844);
		portMapping.put("/services/customers/ValidateUserManagement", 7844);
		portMapping.put("/services/inquiries/CheckImageInquiry", 7843);
		portMapping.put("/services/payments/MassPaymentFileAdd", 7843);
		portMapping.put("/services/payments/MassPaymentInquiry", 7843);
		portMapping.put("/services/payments/PaymentLoanAdd", 7843);
		portMapping.put("/services/transfers/FundsTransferAcctAdd", 7843);
		portMapping.put("/services/Transfers/FundsTransferBankInquiry", 7843);

		portMapping.put("/services/payments/ServiceDebtInquiry", 7843);
		portMapping.put("/services/payments/ServicePaymentAdd", 7843);
		portMapping.put("/services/payments/PaymentCreditCardAdd", 7844);

		try {
			int port = portMapping.get(service_uri);
			lines.add("port=" + port);
		} catch (Exception e) {
			System.err.println(name);
		}

		// String portType_name = wsdl.getRootElement().getChild("portType",
		// wsdlns).getAttributeValue("name");
		// lines.add("portType_name=" + portType_name);

		File properties = new File("C:\\Users\\manji\\Documents\\assist\\multibank\\workspace\\mb.template\\input",
				name + ".properties");
		FileUtils.writeLines(properties, "UTF-8", lines);

		FileUtils.forceMkdir(new File(properties.getParentFile(), name + "/test/" + name));

	}

}
