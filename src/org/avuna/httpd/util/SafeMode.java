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
    along with this program.  If not, see <http://www.gnu.org/licenses/>.*/

package org.avuna.httpd.util;

import java.io.File;
import org.avuna.httpd.AvunaHTTPD;
import org.avuna.httpd.util.unixsocket.CException;

public class SafeMode {
	
	public static class StatResult {
		public int nlink = -1, uid = -1, gid = -1, chmod = -1;
		
		public StatResult(String path) throws CException {
			String s = CLib.stat(path);
			if (s.equals("-1")) {
				throw new CException(CLib.errno(), "stat failed!");
			}
			String[] s2 = s.split("/");
			if (s2.length != 4) {
				throw new CException(-1, "Stat returned bad result!");
			}
			nlink = Integer.parseInt(s2[0]);
			uid = Integer.parseInt(s2[1]);
			gid = Integer.parseInt(s2[2]);
			chmod = Integer.parseInt(s2[3]);
		}
	}
	
	public static boolean canUserRead(int uid, int gid, String f) {
		if (uid <= 0) return true;
		try {
			StatResult stat = new StatResult(f);
			if (stat.uid == uid) {
				if ((stat.chmod & 0400) == 0400) return true;
			}
			if (stat.gid == gid) {
				if ((stat.chmod & 0040) == 0040) return true;
			}
			if ((stat.chmod & 0004) == 0004) return true;
			return false;
		}catch (CException e) {
			Logger.logError(e);
			return false;
		}
	}
	
	public static boolean canUserWrite(int uid, int gid, String f) {
		if (uid <= 0) return true;
		try {
			StatResult stat = new StatResult(f);
			if (stat.uid == uid) {
				if ((stat.chmod & 0200) == 0200) return true;
			}
			if (stat.gid == gid) {
				if ((stat.chmod & 0020) == 0020) return true;
			}
			if ((stat.chmod & 0002) == 0002) return true;
			return false;
		}catch (CException e) {
			Logger.logError(e);
			return false;
		}
	}
	
	public static boolean canUserExecute(int uid, int gid, String f) {
		if (uid <= 0) return true;
		try {
			StatResult stat = new StatResult(f);
			if (stat.uid == uid) {
				if ((stat.chmod & 0100) == 0100) return true;
			}
			if (stat.gid == gid) {
				if ((stat.chmod & 0010) == 0010) return true;
			}
			if ((stat.chmod & 0001) == 0001) return true;
			return false;
		}catch (CException e) {
			Logger.logError(e);
			return false;
		}
	}
	
	public static boolean isHardlink(File f) throws CException {
		if (AvunaHTTPD.windows) return false;
		if (!f.isFile()) return false; // folders CANNOT be hardlinked, but do return the number of subfolders(+1 or 2)
		StatResult sr = new StatResult(f.getAbsolutePath());
		return sr.nlink > 1;
	}
	
	public static boolean isSymlink(File f) throws CException {
		if (AvunaHTTPD.windows) return false;
		byte[] buf = new byte[1024];
		int length = CLib.readlink(f.getAbsolutePath(), buf);
		boolean hl = isHardlink(f);
		return length >= 0 || hl;
	}
	
	public static String readIfSymlink(File f) throws CException {
		if (AvunaHTTPD.windows) return null;
		byte[] buf = new byte[1024];
		int length = CLib.readlink(f.getAbsolutePath(), buf);
		return length >= 0 ? new String(buf, 0, length) : null;
	}
	
	public static boolean setPerms(File root, int uid, int gid, int chmod) { // TODO: block ALL crontab + chroot
		// Logger.log("Setting " + root.getAbsolutePath() + " to " + uid + ":" + gid + " chmod " + chmod);
		// Logger.log(root.getAbsolutePath());
		try {
			if (isSymlink(root)) return false;
		}catch (CException e) {
			return false;
		}
		String ra = root.getAbsolutePath();
		// CLib.umask(0000);
		CLib.chmod(ra, chmod);
		// Logger.log("lchmod returned: " + ch + (ch == -1 ? " error code: " + Native.getLastError() : ""));
		CLib.lchown(ra, uid, gid);
		// CLib.umask(0077);
		return true;
	}
	
	/**
	 * should only be used on an avuna root directory with std perms.
	 * 
	 * @param root
	 * @param uid
	 * @param gid
	 */
	public static void recurPerms(File root, int uid, int gid) {
		recurPerms(root, uid, gid, false);
	}
	
	// should run as root
	private static void recurPerms(File root, int uid, int gid, boolean recursed) {
		try {
			if (isSymlink(root)) {
				return;
			}
		}catch (CException e) {
			return;
		}
		setPerms(root, uid, gid, 0770);
		for (File f : root.listFiles()) {
			if (f.isDirectory()) {
				recurPerms(f, uid, gid, true);
			}else {
				if (uid == 0 && gid == 0) {
					if (f.getName().endsWith(".sh")) {
						setPerms(f, 0, 0, 0770);
					}else {
						setPerms(f, 0, 0, 0660);
					}
				}else {
					String rn = f.getName();
					if (!recursed && (rn.equals("avuna.jar") || rn.equals("main.cfg"))) {
						setPerms(f, 0, gid, 0640);
					}else if (!recursed && (rn.equals("kill.sh") || rn.equals("run.sh") || rn.equals("restart.sh") || rn.equals("cmd.sh"))) {
						setPerms(f, 0, 0, 0770);
					}else {
						setPerms(f, uid, gid, 0660);
					}
				}
			}
		}
	}
}
