package co.com.assist.dp.tester;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StrSubstitutor {

	private static final Pattern p = Pattern.compile("\\$\\{(.+)\\}");

	private Map<String, String> map;

	public StrSubstitutor(Map<String, String> map) {
		this.map = map;
	}

	public String replace(String str) {
		Matcher m = p.matcher(str);
		StringBuffer sb = new StringBuffer();
		while (m.find()) {
			String var = m.group(1);
			String replacement = map.get(var);
			m.appendReplacement(sb, replacement);
		}
		m.appendTail(sb);
		return sb.toString();
	}

}