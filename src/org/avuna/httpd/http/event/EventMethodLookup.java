package org.avuna.httpd.http.event;

import org.avuna.httpd.event.Event;
import org.avuna.httpd.http.Method;
import org.avuna.httpd.http.networking.RequestPacket;
import org.avuna.httpd.http.networking.ResponsePacket;

/**
 * You MUST call cancel if you handle the method.
 */
public class EventMethodLookup extends Event {
	
	private final Method method;
	private final RequestPacket request;
	private final ResponsePacket response;
	
	public Method getMethod() {
		return method;
	}
	
	public RequestPacket getRequest() {
		return request;
	}
	
	public ResponsePacket getResponse() {
		return response;
	}
	
	public EventMethodLookup(Method method, RequestPacket request, ResponsePacket response) {
		super(HTTPEventID.METHODLOOKUP);
		this.method = method;
		this.request = request;
		this.response = response;
	}
	
}