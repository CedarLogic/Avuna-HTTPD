package org.avuna.httpd.plugins.base;

/**
 * This class is deprecated, as we have proper CGI functionality now.
 */

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;
import org.avuna.httpd.AvunaHTTPD;
import org.avuna.httpd.http.networking.Packet;
import org.avuna.httpd.http.networking.RequestPacket;
import org.avuna.httpd.http.networking.ResponsePacket;
import org.avuna.httpd.plugins.Patch;
import org.avuna.httpd.plugins.base.fcgi.FCGIConnection;
import org.avuna.httpd.util.Logger;

public class PatchPHP extends Patch {
	private FCGIConnection conn;
	
	public PatchPHP(String name) {
		super(name);
		// try {
		// this.conn = new FCGIConnection("127.0.0.1", 9000);
		// this.conn.start();
		// }catch (IOException e) {
		// Logger.logError(e);
		// this.conn = null;
		// }
	}
	
	@Override
	public boolean shouldProcessPacket(Packet packet) {
		return false;
	}
	
	@Override
	public void processPacket(Packet packet) {
		
	}
	
	@Override
	public boolean shouldProcessResponse(ResponsePacket response, RequestPacket request, byte[] data) {
		return response.headers.hasHeader("Content-Type") && response.headers.getHeader("Content-Type").equals("application/x-php") && response.body != null && data != null;
	}
	
	@Override
	public void processMethod(RequestPacket request, ResponsePacket response) {
		
	}
	
	@Override
	public byte[] processResponse(ResponsePacket response, RequestPacket request, byte[] data) {
		try {
			String get = request.target;
			if (get.contains("#")) {
				get = get.substring(0, get.indexOf("#"));
			}
			String rq = get;
			if (get.contains("?")) {
				rq = get.substring(0, get.indexOf("?"));
				get = get.substring(get.indexOf("?") + 1);
			}else {
				get = "";
			}
			ProcessBuilder pb = new ProcessBuilder((String)pcfg.get("cmd"));
			// FCGISession session = new FCGISession(conn);
			// pb.environment().start();
			pb.environment().put("REQUEST_URI", rq);
			
			rq = AvunaHTTPD.fileManager.correctForIndex(rq, request);
			
			pb.environment().put("CONTENT_LENGTH", request.body.data.length + "");
			pb.environment().put("CONTENT_TYPE", request.body.type);
			pb.environment().put("GATEWAY_INTERFACE", "CGI/1.1");
			// pb.environment().put("PATH_INFO", request.target);
			// pb.environment().put("PATH_TRANSLATED", new File(JavaWebServer.fileManager.getHTDocs(), rq).toString());
			pb.environment().put("QUERY_STRING", get);
			pb.environment().put("REMOTE_ADDR", request.userIP);
			pb.environment().put("REMOTE_HOST", request.userIP);
			pb.environment().put("REMOTE_PORT", request.userPort + "");
			pb.environment().put("REQUEST_METHOD", request.method.name);
			pb.environment().put("REDIRECT_STATUS", response.statusCode + "");
			pb.environment().put("SCRIPT_NAME", rq.substring(rq.lastIndexOf("/")));
			pb.environment().put("SERVER_NAME", request.headers.getHeader("Host"));
			int port = request.host.getHost().getPort();
			pb.environment().put("SERVER_PORT", port + "");
			pb.environment().put("SERVER_PROTOCOL", request.httpVersion);
			pb.environment().put("SERVER_SOFTWARE", "JWS/" + AvunaHTTPD.VERSION);
			pb.environment().put("DOCUMENT_ROOT", request.host.getHTDocs().getAbsolutePath().replace("\\", "/"));
			pb.environment().put("SCRIPT_FILENAME", AvunaHTTPD.fileManager.getAbsolutePath(rq, request).getAbsolutePath().replace("\\", "/"));
			HashMap<String, ArrayList<String>> hdrs = request.headers.getHeaders();
			for (String key : hdrs.keySet()) {
				for (String val : hdrs.get(key)) {
					pb.environment().put("HTTP_" + key.toUpperCase().replace("-", "_"), val); // TODO: will break if multiple same-nameed headers are received
				}
			}
			Process pbr = pb.start();
			// while (!session.isDone()) {
			// try {
			// Thread.sleep(1L);
			// }catch (InterruptedException e) {
			// Logger.logError(e);
			// }
			// }
			OutputStream pbout = pbr.getOutputStream();
			if (request.body != null) {
				pbout.write(request.body.data);
				pbout.flush();
			}
			Scanner s = new Scanner(pbr.getInputStream());
			boolean tt = true;
			ByteArrayOutputStream bout = new ByteArrayOutputStream();
			while (s.hasNextLine()) {
				String line = s.nextLine().trim();
				if (line.length() > 0) {
					if (tt && line.contains(":")) {
						String[] lt = line.split(":");
						String hn = lt[0].trim();
						String hd = lt[1].trim();
						if (hn.equals("Status")) {
							response.statusCode = Integer.parseInt(hd.substring(0, hd.indexOf(" ")));
							response.reasonPhrase = hd.substring(hd.indexOf(" ") + 1);
						}else {
							response.headers.updateHeader(hn, hd);
						}
					}else {
						tt = false;
						bout.write((line + AvunaHTTPD.crlf).getBytes());
						if (line.equals("</html>")) break;
					}
				}else {
					tt = false;
				}
			}
			if (response.headers.getHeader("Content-Type").equals("application/x-php")) {
				response.headers.updateHeader("Content-Type", "text/html");
			}
			s.close();
			return bout.toByteArray();
		}catch (IOException e) {
			Logger.logError(e); // TODO: throws HTMLException?
		}
		return null;// TODO: to prevent PHP leaks
	}
	
	@Override
	public void formatConfig(HashMap<String, Object> json) {
		if (!json.containsKey("cmd")) json.put("cmd", "php-cgi");
	}
	
}