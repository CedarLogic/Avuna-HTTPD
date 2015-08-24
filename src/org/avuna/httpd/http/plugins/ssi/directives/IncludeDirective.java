package org.avuna.httpd.http.plugins.ssi.directives;

import org.avuna.httpd.hosts.VHost;
import org.avuna.httpd.http.Method;
import org.avuna.httpd.http.networking.RequestPacket;
import org.avuna.httpd.http.networking.ResponsePacket;
import org.avuna.httpd.http.plugins.ssi.Page;
import org.avuna.httpd.http.plugins.ssi.ParsedSSIDirective;
import org.avuna.httpd.http.plugins.ssi.PluginSSI;
import org.avuna.httpd.http.plugins.ssi.SSIDirective;

public class IncludeDirective extends SSIDirective {
	
	public IncludeDirective(PluginSSI ssi) {
		super(ssi);
	}
	
	private static String processHREF(VHost vhost, String parent, String href) {
		String h = href.trim();
		if (h.startsWith("http://") || h.startsWith("https://") || h.startsWith("//")) {
			return null; // cant SSI remote
		}
		String[] hs = h.split("/");
		String[] ps = parent.substring(0, parent.lastIndexOf("/")).split("/");
		int pt = 0;
		for (int i = 0; i < hs.length; i++) {
			if (hs[i].length() == 0) continue;
			if (hs[i].equals("..")) {
				pt++;
			}else {
				break;
			}
		}
		if (pt > ps.length) {
			vhost.logger.log("[WARNING] Attempt to escape htdocs from SSI: " + parent + " child: " + href);
			return null;
		}
		String[] f = new String[ps.length - pt + hs.length - pt];
		System.arraycopy(ps, 0, f, 0, ps.length - pt);
		System.arraycopy(hs, pt, f, ps.length - pt, hs.length - pt);
		h = "";
		for (String s : f) {
			if (s.length() == 0 || s.equals(".")) continue;
			h += "/" + s;
		}
		if (!h.startsWith("/")) h = "/" + h;
		String th = h;
		if (th.contains("#")) th = th.substring(0, th.indexOf("#"));
		if (th.contains("?")) th = th.substring(0, th.indexOf("?"));
		return h;
	}
	
	@Override
	public String call(Page page, ParsedSSIDirective dir) {
		if (dir.args.length != 1) return null;
		if (page.data == null || !(page.data instanceof RequestPacket)) return null;
		RequestPacket request = (RequestPacket) page.data;
		String f = dir.args[0];
		if (f.startsWith("file=")) {
			f = processHREF(request.host, request.target, f.substring(5));
		}else if (f.startsWith("virtual=")) {
			f = f.substring(8);
			if (!f.startsWith("/")) f = "/" + f;
		}
		RequestPacket subreq = request.clone();
		subreq.parent = request;
		subreq.target = f;
		subreq.method = Method.GET;
		subreq.body.data = null;
		subreq.headers.removeHeaders("If-None-Matches"); // just in case of collision + why bother ETag?
		subreq.headers.removeHeaders("Accept-Encoding"); // gzip = problem
		ResponsePacket subresp = request.host.getHost().processSubRequests(subreq)[0];
		if (subresp != null && subresp.body != null && subresp.body.data != null) {
			return new String(subresp.body.data);
		}
		return null;
	}
	
	@Override
	public String getDirective() {
		return "include";
	}
	
}
