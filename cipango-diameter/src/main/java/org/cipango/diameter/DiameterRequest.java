// ========================================================================
// Copyright 2008-2009 NEXCOM Systems
// ------------------------------------------------------------------------
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at 
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// ========================================================================

package org.cipango.diameter;

import java.io.IOException;
import java.util.Random;

import org.cipango.diameter.base.Common;

public class DiameterRequest extends DiameterMessage
{
	private static int __hopId;
	private static int __endId;
	
	private static synchronized int nextHopId() { return __hopId++; }
	private static synchronized int nextEndId() { return __endId++; }

	static {
		Random random = new Random();
		 __hopId = Math.abs(random.nextInt());
		 // RFC 3588: Upon reboot implementations MAY set the high order 12 bits to
	     // contain the low order 12 bits of current time, and the low order
	     // 20 bits to a random value
		__endId = (int) ((System.currentTimeMillis() & 0xFFF) << 20) + random.nextInt(0x100000);
	}
	
	public DiameterRequest() {}
	
	public DiameterRequest(Node node, DiameterCommand command, int appId, String sessionId)
	{	
		super(node, appId, command, nextEndId(), nextHopId(), sessionId);
	}
	
	public boolean isRequest()
	{
		return true;
	}
	
	public String getDestinationRealm()
	{
		return get(Common.DESTINATION_REALM);
	}
	
	public String getDestinationHost()
	{
		return get(Common.DESTINATION_HOST);
	}
	
	public DiameterAnswer createAnswer(ResultCode resultCode)
	{
		return new DiameterAnswer(this, resultCode);
	}
	
	public void send() throws IOException
	{
		getNode().send(this);
	}
}