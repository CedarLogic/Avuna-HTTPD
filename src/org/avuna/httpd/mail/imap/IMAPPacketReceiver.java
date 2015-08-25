/*	Avuna HTTPD - General Server Applications
    Copyright (C) 2015 Maxwell Bruce

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package org.avuna.httpd.mail.imap;

import java.io.IOException;
import org.avuna.httpd.AvunaHTTPD;
import org.avuna.httpd.util.unio.PacketReceiver;
import org.avuna.httpd.util.unio.UNIOSocket;

public class IMAPPacketReceiver extends PacketReceiver {
	
	private IMAPWork work = null;
	
	protected void setWork(IMAPWork w) {
		this.work = w;
		synchronized (this) {
			this.notify();
		}
	}
	
	private int nb = -1;
	
	@Override
	public void readPacket(UNIOSocket sock, byte[] buf) {
		if (work == null) {
			synchronized (this) {
				try {
					this.wait();
				}catch (InterruptedException e) {}
			}
		}
		try {
			work.flushPacket(buf);
			if (work.nb >= 0) {
				nb = work.nb;
			}
		}catch (IOException e) {
			work.host.logger.logError(e);
		}
	}
	
	@Override
	public int nextDelimType(UNIOSocket sock) {
		return nb < 0 ? 0 : 1;
	}
	
	public byte[] nextDelim(UNIOSocket sock) {
		return "\r\n".getBytes();
	}
	
	public int nextLength(UNIOSocket sock) {
		int n = nb;
		nb = -1;
		return n;
	}
	
	@Override
	public void closed(UNIOSocket sock) {
		if (work != null) try {
			work.close();
		}catch (IOException e) {
			work.host.logger.logError(e);
		}
	}
	
	@Override
	public void fail(Exception e) {
		if (work != null) work.host.logger.logError(e);
		else AvunaHTTPD.logger.logError(e);
	}
	
}
