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
		String[] projects = new String[] { "camaracomerciows.ConsultaCamaraComercioService",
				"DecisorWS.ConsultasDecisorService", "DecisorWS.MotorService", "dhws.DHService", "dhws.DHService2",
				"dhws.DHService_v1-2", "dhws.DHService_v1-3", "dhws3.DH2ClientesService",
				"dhws3.DH2PJClientesService_v1-16", "dhws3.DH2PJClientesService_v1-17",
				"dhws3.DH2PNClientesService_v1-4", "dhws3.DH2PNClientesService_v1-5", "dhws3.DH2Service",
				"HCPL_WS.HcplWS", "HCPL_WS.HcplWSClientes", "idws.ServicioIdentificacion",
				"idws2.ServicioIdentificacion", "ijws.ServicioIJWS", "localizacion.ServicioLocalizacion",
				"localizacion2.ServicioLocalizacion2", "SuperSociedadesWS.SuperSociedadesService",
				"ValidacionWS.ServicioValidacion" };

		File output = new File("C:\\Users\\manji\\workspaces\\experian\\DataPower\\src\\002.servicios");
		FileUtils.deleteQuietly(output);

		Map<String, Element> dpConfigurations = new HashMap<String, Element>();

		for (String project : projects) {
			File input = new File(
					"C:\\Users\\manji\\git\\datapower-tools\\templater\\assets\\output\\" + project + "\\src");

			File[] modules = input.listFiles((FileFilter) DirectoryFileFilter.DIRECTORY);
			for (File module : modules) {
				File[] domains = module.listFiles((FileFilter) DirectoryFileFilter.DIRECTORY);
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
					FileUtils.writeStringToFile(new File(output, entry.getKey() + "/export.xml"), prettyExport,
							"UTF-8");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

}
