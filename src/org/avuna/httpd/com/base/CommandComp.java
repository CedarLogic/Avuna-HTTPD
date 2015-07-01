/* Avuna HTTPD - General Server Applications Copyright (C) 2015 Maxwell Bruce This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should have received a copy of the GNU General Public License along with this program. If not, see <http://www.gnu.org/licenses/>. */

package org.avuna.httpd.com.base;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.avuna.httpd.AvunaHTTPD;
import org.avuna.httpd.com.Command;
import org.avuna.httpd.com.CommandContext;
import org.avuna.httpd.hosts.Host;
import org.avuna.httpd.hosts.HostHTTP;
import org.avuna.httpd.hosts.VHost;
import org.avuna.httpd.http.plugins.javaloader.PluginJavaLoader;
import org.avuna.httpd.util.Logger;

public class CommandComp extends Command {
	
	private static void recurDelete(File f) throws IOException {
		for (File sf : f.listFiles()) {
			if (sf.isFile()) {
				sf.delete();
			}else {
				recurDelete(sf);
			}
		}
		f.delete();
	}
	
	@Override
	public int processCommand(String[] args, CommandContext context) throws Exception {
		boolean all = args.length < 1;
		Host ghost = (Host) AvunaHTTPD.hosts.get(context.getSelectedHost());
		if (ghost == null) {
			context.println("Invalid Selected Host! (select)");
			return 2;
		}
		if (!(ghost instanceof HostHTTP)) {
			context.println("Not a http host! (select)");
			return 4;
		}
		HostHTTP phost = (HostHTTP) ghost;
		VHost host = phost.getVHostByName(context.getSelectedVHost());
		if (host == null) {
			context.println("Invalid Selected VHost! (select)");
			return 3;
		}
		String sep = AvunaHTTPD.windows ? ";" : ":";
		String us = null;
		try {
			String fp = AvunaHTTPD.class.getProtectionDomain().getCodeSource().getLocation().getPath();
			us = fp;
		}catch (Exception e) {
			Logger.logError(e);
			context.println("Critical error <Couldn't find us! Are we bound in memory?>!");
			return -2;
		}
		if (us.equals("./")) {
			us = new File("avuna.jar").getAbsolutePath();
		}
		String cp = new File(us).getAbsolutePath() + sep + host.getHTDocs().toString() + sep + host.getHTSrc().toString() + sep + PluginJavaLoader.lib.toString() + sep;
		for (File f : PluginJavaLoader.lib.listFiles()) {
			if (!f.isDirectory() && f.getName().endsWith(".jar")) {
				cp += f.toString() + sep;
			}
		}
		cp = cp.substring(0, cp.length() - 1);
		ArrayList<String> cfs = new ArrayList<String>();
		cfs.add(AvunaHTTPD.mainConfig.getNode("javac").getValue());
		cfs.add("-cp");
		cfs.add(cp);
		cfs.add("-d");
		cfs.add(host.getHTDocs().toString());
		ArrayList<File> tc = new ArrayList<File>();
		File htsrc = host.getHTSrc();
		htsrc.mkdirs();
		File tmp = new File(htsrc, "tmp");
		if (tmp.exists()) {
			if (tmp.isFile()) tmp.delete();
			else recurDelete(tmp);
		}
		tmp.mkdirs();
		if (all) {
			recurForComp(tc, host.getHTSrc());
		}else {
			tc.add(new File(host.getHTSrc(), args[0]));
		}
		for (File f : tc) {
			String tfs = f.getAbsolutePath().substring(htsrc.getAbsolutePath().length());
			String fabs = tfs;
			if (fabs.endsWith(".xjsp")) {
				fabs = fabs.substring(0, fabs.length() - 4) + "java";
			}
			File tf = new File(tmp, fabs);
			tf.getParentFile().mkdirs();
			tf.createNewFile();
			FileInputStream fin = new FileInputStream(f);
			ByteArrayOutputStream fbout = new ByteArrayOutputStream();
			byte[] buf = new byte[1024];
			int i = 0;
			do {
				i = fin.read(buf);
				if (i > 0) fbout.write(buf, 0, i);
			}while (i > 0);
			fin.close();
			String java = fbout.toString();
			String pkg = tfs;
			if (pkg.startsWith("/")) pkg = pkg.substring(1);
			int ci = pkg.lastIndexOf("/");
			String cn = pkg.substring(ci + 1, pkg.lastIndexOf("."));
			pkg = ci == -1 ? "" : pkg.substring(0, ci);
			pkg = pkg.replace('/', '.');
			if (f.getName().endsWith(".xjsp")) java = processXJSP(pkg, cn, java);
			java = processJava(java);
			FileOutputStream fout = new FileOutputStream(tf);
			fout.write(java.getBytes());
			fout.flush();
			fout.close();
			cfs.add(tf.getAbsolutePath());
		}
		ProcessBuilder pb = new ProcessBuilder(cfs.toArray(new String[] {}));
		pb.directory(AvunaHTTPD.fileManager.getMainDir());
		pb.redirectErrorStream(true);
		Process proc = pb.start();
		Scanner s = new Scanner(proc.getInputStream());
		while (s.hasNextLine()) {
			context.println("javac: " + s.nextLine());
		}
		s.close();
		context.println("Compile completed.");
		return 0;
	}
	
	private static final Pattern itag = Pattern.compile("(?s)<import>(.*?)<\\/import>", Pattern.CASE_INSENSITIVE);
	private static final Pattern jtag = Pattern.compile("(?s)<java>(.*?)<\\/java>", Pattern.CASE_INSENSITIVE);
	private static final Pattern mtag = Pattern.compile("(?s)<extern>(.*?)<\\/extern>", Pattern.CASE_INSENSITIVE);
	
	private static String processXJSP(String pkg, String className, String ojava) {
		String java = ojava;
		StringBuilder result = new StringBuilder();
		if (pkg.length() > 0) {
			result.append("package ").append(pkg).append(";").append(AvunaHTTPD.crlf).append(AvunaHTTPD.crlf);
		}
		result.append("import org.avuna.httpd.http.networking.RequestPacket;").append(AvunaHTTPD.crlf);
		result.append("import org.avuna.httpd.http.networking.ResponsePacket;").append(AvunaHTTPD.crlf);
		result.append("import org.avuna.httpd.http.plugins.javaloader.HTMLBuilder;").append(AvunaHTTPD.crlf);
		result.append("import org.avuna.httpd.http.plugins.javaloader.JavaLoaderPrint;").append(AvunaHTTPD.crlf);
		ArrayList<int[]> skips = new ArrayList<int[]>();
		Matcher im = itag.matcher(java);
		while (im.find()) {
			skips.add(new int[] { im.start(), im.end() });
			result.append("import ").append(im.group(1).trim()).append(";").append(AvunaHTTPD.crlf);
		}
		int offset = 0;
		for (int[] is : skips) {
			java = java.substring(0, is[0] - offset) + java.substring(is[1] - offset, java.length()).trim();
			offset += is[1] - is[0] + 1;
		}
		skips.clear();
		offset = 0;
		result.append(AvunaHTTPD.crlf);
		result.append("public class " + className + " extends JavaLoaderPrint {").append(AvunaHTTPD.crlf);
		Matcher mm = mtag.matcher(java);
		while (mm.find()) {
			skips.add(new int[] { mm.start(), mm.end() });
			result.append(AvunaHTTPD.crlf);
			result.append(mm.group(1));
		}
		for (int[] is : skips) {
			java = java.substring(0, is[0] - offset) + java.substring(is[1] - offset, java.length());
			offset += is[1] - is[0] + 1;
		}
		skips.clear();
		offset = 0;
		result.append(AvunaHTTPD.crlf);
		result.append("    public boolean generate(HTMLBuilder out, ResponsePacket response, RequestPacket request) {").append(AvunaHTTPD.crlf);
		int oend = 0;
		Matcher jm = jtag.matcher(java);
		while (jm.find()) {
			String html = java.substring(oend, jm.start());
			html = html.replace("\\", "\\\\");
			html = html.replace("\"", "\\\"");
			html = html.replace("\r\n", "\\r\\n");
			html = html.replace("\n", "\\r\\n");
			result.append("        out.print(\"" + html.trim() + "\");").append(AvunaHTTPD.crlf);
			result.append(jm.group(1)).append(AvunaHTTPD.crlf);
			oend = jm.end();
		}
		if (oend < java.length() - 1) {
			String html = java.substring(oend, java.length());
			html = html.replace("\\", "\\\\");
			html = html.replace("\"", "\\\"");
			html = html.replace("\r\n", "\\r\\n");
			html = html.replace("\n", "\\r\\n");
			result.append("        out.print(\"" + html.trim() + "\");").append(AvunaHTTPD.crlf);
		}
		result.append("        return true;").append(AvunaHTTPD.crlf);
		result.append("    }").append(AvunaHTTPD.crlf);
		result.append(AvunaHTTPD.crlf);
		result.append("}").append(AvunaHTTPD.crlf);// end class
		return result.toString();
	}
	
	private static final Pattern generate = Pattern.compile("(?s)(\\s*)public.*?boolean generate\\([a-zA-Z.]*?HTMLBuilder ([a-zA-Z0-9_]+?),\\s?[a-zA-Z.]*?ResponsePacket ([a-zA-Z0-9_]+?),\\s?[a-zA-Z.]*?RequestPacket ([a-zA-Z0-9_]+?)\\)\\s*\\{");
	private static final Pattern fcomment = Pattern.compile("(?s)\\/\\*%.*\\*\\/");
	private static final HashMap<String, Pattern> hbns = new HashMap<String, Pattern>();
	
	private static String processJava(String java) {
		StringBuilder result = new StringBuilder();
		Matcher gm = generate.matcher(java);
		int ss = 1024;
		int begin = 0;
		String hbn = "";
		while (gm.find()) {
			if (gm.group(1).length() < ss) {
				ss = gm.group(1).length();
				begin = gm.end();
				hbn = gm.group(2);
			}
		}
		if (hbn.length() > 0) {
			int end = java.length();
			int cbc = 0;
			for (int i = begin; i < java.length(); i++) {// TODO" {} in ""
				char c = java.charAt(i);
				if (c == '{') {
					cbc++;
				}else if (c == '}') {
					if (cbc == 0) {
						end = i;
						break;
					}
					cbc--;
				}
			}
			result.append(java.substring(0, begin));
			String gen = java.substring(begin, end);
			if (gen.contains("out.containsKey(\"inst\")")) {
				System.out.print("");
			}
			int lm = 0;
			boolean inq = false;
			boolean block = false;
			boolean hblock = false;
			int sbi = -1;
			for (int i = 0; i < gen.length(); i++) {
				char c = gen.charAt(i);
				if (!inq && i > 0) {
					char pc = gen.charAt(i - 1);
					if (!block && pc == '/') {
						if (c == '/') {
							int eol = gen.indexOf('\n', i);
							String ngen = gen.substring(0, i - 1);
							ngen += gen.substring(eol + 1);
							gen = ngen;
						}else if (c == '*') {
							sbi = i - 1;
							block = true;
							if (i < gen.length()) {
								char nc = gen.charAt(i + 1);
								if (nc == '%') {
									hblock = true;
								}
							}
						}
					}else if (pc == '*') {
						if (c == '/' && block) {
							String ngen = gen.substring(0, sbi);
							if (hblock) {
								String html = gen.substring(sbi + 3, i - 1);
								html = html.trim();
								html = html.replace("\\", "\\\\");
								html = html.replace("\"", "\\\"");
								html = html.replace("\r", "");
								String[] hl = html.split("\n");
								html = "";
								for (String hls : hl) {
									hls = hls.trim();
									if (hls.startsWith("*")) hls = hls.substring(1);
									hls = hls.trim();
									html += hls + "\\r\\n";
								}
								ngen += hbn + ".print(\"" + html + "\");";
								hblock = false;
							}
							ngen += gen.substring(i + 1);
							gen = ngen;
							i = sbi;
							block = false;
						}
					}
				}
				if (!block && c == '\"') {
					if (i > 0 && gen.charAt(i - 1) != '\\') {
						inq = !inq;
					}
				}
			}
			// gen = gen.replaceAll("(?s)\\s*\\/\\*[^%].*\\*\\/", "");
			Pattern lp = null;
			if (hbns.containsKey(hbn)) {
				lp = hbns.get(hbn);
			}else {
				lp = Pattern.compile("(?s)((?:\\s*" + hbn + "\\.print(?:ln)?\\(\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\"\\);)++)");
				hbns.put(hbn, lp);
			}
			Matcher gme = lp.matcher(gen);
			int oend = 0;
			while (gme.find()) {
				int mstart = gme.start();
				int mend = gme.end();
				result.append(gen.substring(oend, mstart)).append(AvunaHTTPD.crlf);
				String match = gme.group();
				int olen = match.length();
				match = match.replace("\r", "");
				match = match.replace("\n", "");
				olen = olen - match.length();
				ArrayList<String> msl = new ArrayList<String>();
				lm = 0;
				inq = false;
				for (int i = 0; i < match.length(); i++) {
					char c = match.charAt(i);
					if (c == ';' || c == '{' || c == '}') {
						if (!inq) {
							msl.add(match.substring(lm, i));
							lm = i + 1;
						}
					}else if (c == '\"') {
						if (i > 0 && match.charAt(i - 1) != '\\') {
							inq = !inq;
						}
					}
				}
				String[] ms = msl.toArray(new String[0]);
				result.append(" ").append(hbn).append(".print(\"");
				for (String s : ms) {
					String sss = s.trim();
					if (sss.startsWith(hbn + ".print")) {
						sss = sss.substring(hbn.length() + 6);
						boolean ln = false;
						if (sss.startsWith("ln")) {
							ln = true;
							sss = sss.substring(2);
						}
						sss = sss.substring(2, sss.length() - 2);
						result.append(sss);
						if (ln) {
							result.append("\\r\\n");
						}
					}
				}
				result.append("\");" + AvunaHTTPD.crlf);
				oend = mend;
			}
			result.append(gen.substring(oend, gen.length()));
			result.append(java.substring(end, java.length()));
		}
		return result.toString();
	}
	
	private static void recurForComp(ArrayList<File> cfs, File base) throws Exception {
		for (File f : base.listFiles()) {
			if (f.isDirectory()) {
				recurForComp(cfs, f);
			}else {
				String n = f.getName();
				if (n.endsWith(".java") || n.endsWith(".xjsp")) cfs.add(f);
			}
		}
	}
	
	@Override
	public String getHelp() {
		return "Compiles all/a specific file from htsrc to htdocs.";
	}
}
