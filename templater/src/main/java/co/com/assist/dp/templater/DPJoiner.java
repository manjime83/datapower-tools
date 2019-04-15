package co.com.assist.dp.templater;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

public class DPJoiner implements Runnable {

	private static final SAXBuilder builder = new SAXBuilder();

	private static final XMLOutputter prettyOutputter = new XMLOutputter(
			Format.getPrettyFormat().setOmitDeclaration(true).setIndent("\t"));

	public static void main(String[] args) throws JDOMException, IOException {
		new DPJoiner().run();
	}

	@Override
	public void run() {
		String[] projects = new String[] { "AuditoriaESBV1", "AutenticacionESBV1", "CanalESBV1", "CatalogoCanalESBV1",
				"CatalogoESBV1", "ClienteESBV1", "CuentaAhorrosESBV1", "CuentaCorrienteESBV1", "CuentaESBV1",
				"DispositivoSeguridadESBV1", "LeasingESBV1", "MovimientoESBV1", "PagoESBV1", "PlazoFijoESBV1",
				"PortafolioESBV1", "PrestamoESBV1", "ProductoESBV1", "ProfuturoESBV1", "TarjetaCreditoESBV1",
				"TarjetaPrepagadaESBV1", "TransferenciaESBV1" };

		File output = new File("C:\\Users\\manji\\Documents\\assist\\multibank\\workspace\\mb.entregas\\entrega.20190207.1\\idg");
		FileUtils.deleteQuietly(output);

		Map<String, Element> dpConfigurations = new HashMap<String, Element>();

		for (String project : projects) {
			File input = new File("C:\\Users\\manji\\workspaces\\bgeneral\\bgeneral.template\\output\\" + project);

			File[] domains = input.listFiles((FileFilter) DirectoryFileFilter.DIRECTORY);
			for (File domain : domains) {
				if (!dpConfigurations.containsKey(domain.getName())) {
					Element dpConfiguration = new Element("datapower-configuration");
					dpConfiguration.setAttribute("version", "3");

					Element configuration = new Element("configuration");
					configuration.setAttribute("domain", domain.getName());
					dpConfiguration.addContent(configuration);

					Element files = new Element("files");
					dpConfiguration.addContent(files);

					dpConfigurations.put(domain.getName(), dpConfiguration);
				}

				Element export = null;
				try {
					export = builder.build(new File(domain, "export.xml")).getRootElement();
				} catch (Exception e) {
					e.printStackTrace();
				}
				Element c = export.getChild("configuration");
				if (c != null) {
					dpConfigurations.get(domain.getName()).getChild("configuration").addContent(c.cloneContent());
				}
				Element f = export.getChild("files");
				if (f != null) {
					dpConfigurations.get(domain.getName()).getChild("files").addContent(f.cloneContent());
				}

				try {
					FileUtils.copyDirectoryToDirectory(domain, output);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		Set<Entry<String, Element>> entrySet = dpConfigurations.entrySet();
		for (Entry<String, Element> entry : entrySet) {
			String prettyExport = prettyOutputter.outputString(entry.getValue());
			try {
				FileUtils.writeStringToFile(new File(output, entry.getKey() + "/export.xml"), prettyExport, "UTF-8");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

}
