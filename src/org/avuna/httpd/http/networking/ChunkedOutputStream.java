package org.avuna.httpd.http.networking;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.zip.GZIPOutputStream;
import org.avuna.httpd.AvunaHTTPD;
import org.avuna.httpd.http.Resource;
import org.avuna.httpd.http.ResponseGenerator;
import org.avuna.httpd.http.StatusCode;

public class ChunkedOutputStream extends DataOutputStream {
	private boolean gzip = false, flushed = false;
	private ResponsePacket toSend = null;
	private GZIPOutputStream gzips = null;
	
	public ChunkedOutputStream(OutputStream out, ResponsePacket toSend, boolean gzip) throws IOException {
		super(out);
		this.toSend = toSend;
		this.gzip = gzip;
		if (gzip) {
			gzips = new GZIPOutputStream(gzipb);
		}
	}
	
	private ByteArrayOutputStream cache = new ByteArrayOutputStream(), gzipb = new ByteArrayOutputStream();
	private boolean writing = false;
	
	public void writeHeaders() throws IOException {
		if (!flushed && !writing) {
			StringBuilder ser = new StringBuilder();
			ser.append((toSend.httpVersion + " " + toSend.statusCode + " " + toSend.reasonPhrase + AvunaHTTPD.crlf));
			HashMap<String, ArrayList<String>> hdrs = toSend.headers.getHeaders();
			for (String key : hdrs.keySet()) {
				for (String val : hdrs.get(key)) {
					ser.append((key + ": " + val + AvunaHTTPD.crlf));
				}
			}
			ser.append(AvunaHTTPD.crlf);
			writing = true;
			super.write(ser.toString().getBytes());
			super.flush();
			writing = false;
			flushed = true;
		}
	}
	
	public void write(byte[] b, int off, int len) throws IOException {
		if (writing) {
			super.write(b, off, len);
		}else {
			if (!flushed) {
				writeHeaders();
			}else {
				cache.write(b, off, len);
			}
		}
	}
	
	public void flush() throws IOException {
		byte[] cache = this.cache.toByteArray();
		this.cache.reset();
		if (cache.length == 0) return;
		if (gzip) {
			gzips.write(cache);
			gzips.flush();
			cache = gzipb.toByteArray();
			gzipb.reset();
		}
		ByteBuffer bb = ByteBuffer.allocate(4);
		bb.putInt(0, cache.length);
		byte[] bas = new byte[4];
		bb.position(0);
		bb.get(bas);
		String hex = AvunaHTTPD.fileManager.bytesToHex(bas);
		writing = true;
		boolean httpe = toSend.request.work.httpe;
		if (httpe) {
			ResponsePacket subResponse = new ResponsePacket();
			subResponse.bwt = System.nanoTime();
			ResponseGenerator.generateDefaultResponse(subResponse, StatusCode.PARTIAL_CONTENT);
			subResponse.headers.addHeader("X-Req-ID", toSend.headers.getHeader("X-Req-ID"));
			ByteArrayOutputStream bout = new ByteArrayOutputStream();
			bout.write((hex + AvunaHTTPD.crlf).getBytes());
			bout.write(cache);
			bout.write(AvunaHTTPD.crlf.getBytes());
			subResponse.body = new Resource(bout.toByteArray(), "application/octet-stream");
			subResponse.prewrite();
			subResponse.done = true;
			toSend.request.work.asyncOutQueue.add(subResponse);
		}else {
			super.write((hex + AvunaHTTPD.crlf).getBytes());
			super.write(cache);
			super.write(AvunaHTTPD.crlf.getBytes());
			super.flush();
		}
		writing = false;
	}
	
	public void finish() throws IOException {
		writeHeaders();
		if (cache.size() > 0) {
			flush();
		}
		if (gzip) {
			gzips.flush();
			gzips.close();
			byte[] cache = gzipb.toByteArray();
			gzipb.reset();
			ByteBuffer bb = ByteBuffer.allocate(4);
			bb.putInt(0, cache.length);
			byte[] bas = new byte[4];
			bb.position(0);
			bb.get(bas);
			writing = true;
			super.write((AvunaHTTPD.fileManager.bytesToHex(bas) + AvunaHTTPD.crlf).getBytes());
			super.write(cache);
			super.write((AvunaHTTPD.crlf + "0" + AvunaHTTPD.crlf).getBytes());
			super.flush();
			writing = false;
		}else {
			writing = true;
			super.write(("0" + AvunaHTTPD.crlf).getBytes());
			super.flush();
			writing = false;
		}
	}
	
	public void close() throws IOException {
		finish();
		super.close();
	}
	
}
