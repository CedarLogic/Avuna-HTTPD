/* Avuna HTTPD - General Server Applications Copyright (C) 2015 Maxwell Bruce This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should have received a copy of the GNU General Public License along with this program. If not, see <http://www.gnu.org/licenses/>. */
package org.avuna.httpd.http.plugins.ssi;

import java.util.ArrayList;
import java.util.HashMap;

public class Page {
	public final ParsedSSIDirective[] directives;
	public final HashMap<String, String> variables = new HashMap<String, String>();
	/** An optionally filled variable for metadata. For example, in HTTP, it is a RequestPacket. */
	public Object data = null;
	public int scope = 0;
	/** If set >= 0, output MUST NOT continue until scope meets this, and then it MUST reset to -1. */
	public int returnScope = -1;
	/** Next output next block return scope set. Purpose is to allow setting the returnScope after the previous block before our call is output. */
	public int nonbrss = -2;
	public final ArrayList<Integer> lifc = new ArrayList<Integer>();
	public final SSIEngine engine;
	
	public Page(SSIEngine engine, ParsedSSIDirective[] directives) {
		this.directives = directives;
		this.engine = engine;
		variables.put("error", "[an error occurred while processing this directive]");
	}
	
	public void postOutputNextBlock() {
		if (nonbrss >= -1) {
			returnScope = nonbrss;
			nonbrss = -2;
		}
	}
	
	/** Tells whether we are inside a returned scope, ie a failed IF statement. This does change state, so multiple calls are BAD. */
	public boolean shouldOutputNextBlock() {
		if (returnScope < 0) return true;
		if (returnScope <= scope) {
			if (nonbrss >= -1) {
				returnScope = nonbrss;
				nonbrss = -2;
			}else {
				returnScope = -1;
			}
			return false;
		}else {
			return true;
		}
	}
	
	protected int scopeDepth() {
		if (returnScope < 0) return 0;
		if (returnScope <= scope) {
			return scope - returnScope;
		}else {
			return 0;
		}
	}
}
