// ========================================================================
// Copyright 2010 NEXCOM Systems
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
package org.cipango.snmp;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.cipango.server.Server;
import org.eclipse.jetty.util.component.AggregateLifeCycle;
import org.eclipse.jetty.util.component.Dumpable;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.log.Log;
import org.snmp4j.TransportMapping;
import org.snmp4j.agent.BaseAgent;
import org.snmp4j.agent.CommandProcessor;
import org.snmp4j.agent.DuplicateRegistrationException;
import org.snmp4j.agent.MOGroup;
import org.snmp4j.agent.mo.MOTableRow;
import org.snmp4j.agent.mo.jmx.mibs.JvmManagementMibInst;
import org.snmp4j.agent.mo.snmp.RowStatus;
import org.snmp4j.agent.mo.snmp.SnmpCommunityMIB;
import org.snmp4j.agent.mo.snmp.SnmpNotificationMIB;
import org.snmp4j.agent.mo.snmp.SnmpTargetMIB;
import org.snmp4j.agent.mo.snmp.StorageType;
import org.snmp4j.agent.mo.snmp.VacmMIB;
import org.snmp4j.agent.security.MutableVACM;
import org.snmp4j.mp.MPv3;
import org.snmp4j.mp.MessageProcessingModel;
import org.snmp4j.security.AuthSHA;
import org.snmp4j.security.PrivDES;
import org.snmp4j.security.SecurityLevel;
import org.snmp4j.security.SecurityModel;
import org.snmp4j.security.USM;
import org.snmp4j.security.UsmUser;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.TcpAddress;
import org.snmp4j.smi.TransportIpAddress;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.Variable;
import org.snmp4j.transport.DefaultTcpTransportMapping;
import org.snmp4j.transport.DefaultUdpTransportMapping;

public class SnmpAgent extends BaseAgent implements LifeCycle, Dumpable
{

	public static final OID NEXCOM_ENTREPRISE_OID = new OID("1.3.6.1.4.26588");

	static
	{
		//LogFactory.setLogFactory(new Log4jLogFactory());
	}

	private SnmpAddress[] _trapReceivers;
	private SnmpAddress[] _connectors;
		
	private List<MOGroup> _mibs = new ArrayList<MOGroup>();
	private Server _server;


	public SnmpAgent()
			throws IOException
	{
		super(new File("snmpAgentBC.cfg"),
	        null,
	        new CommandProcessor(new OctetString(MPv3.createLocalEngineID())));
		setSysDescr(new OctetString("Cipango-" + Server.getSipVersion()));
		setSysOID(NEXCOM_ENTREPRISE_OID);
		agent = new CommandProcessor(new OctetString(MPv3.createLocalEngineID()));
	}
	
	public void start() throws IOException
	{
		init();
		finishInit();
		run();
	}
	
	protected void addCommunities(SnmpCommunityMIB communityMIB)
	{
		Variable[] com2sec = new Variable[]
		{ new OctetString("public"), // community name
				new OctetString("public"), // security name
				getAgent().getContextEngineID(), // local engine ID
				new OctetString(), // default context name
				new OctetString(), // transport tag
				new Integer32(StorageType.nonVolatile), // storage type
				new Integer32(RowStatus.active) // row status
		};
		MOTableRow row = communityMIB.getSnmpCommunityEntry().createRow(
				new OctetString("public2public").toSubIndex(true), com2sec);
		communityMIB.getSnmpCommunityEntry().addRow(row);
	}

	protected void addNotificationTargets(SnmpTargetMIB targetMIB,
			SnmpNotificationMIB notificationMIB)
	{
		targetMIB.addDefaultTDomains();

		notificationMIB.addNotifyEntry(new OctetString("default"),
				new OctetString("notify"),
				SnmpNotificationMIB.SnmpNotifyTypeEnum.trap,
				StorageType.permanent);

		for (int i = 0; i < _trapReceivers.length; i++)
		{
			addTrapHost(_trapReceivers[i]);
		}
		
		targetMIB.addTargetParams(new OctetString("v2c"),
                MessageProcessingModel.MPv2c,
                SecurityModel.SECURITY_MODEL_SNMPv2c,
                new OctetString("public"),
                SecurityLevel.NOAUTH_NOPRIV,
                StorageType.permanent);
	}
	
	private void addTrapHost(SnmpAddress address)
	{
		try
		{
			TransportIpAddress transportIpAddress;
			if (address.getPort() <= 0)
				address.setPort(162);
			if (address.isUdp())
				transportIpAddress = new UdpAddress(address.getInetAddress(), address.getPort());
			else
				transportIpAddress = new TcpAddress(address.getInetAddress(), address.getPort());
	
			snmpTargetMIB.addTargetAddress(new OctetString("notification" + address.getHost()),
					address.getTransportDomain(), 
					new OctetString(transportIpAddress.getValue()),
					200, 1, 
					new OctetString("notify"),
					new OctetString("v2c"),
					StorageType.permanent);

			Log.info("Add SNMP trap receiver: " + address);
		}
		catch (Exception e) 
		{
			Log.warn("Failed to add SNMP trap receiver: " + address, e);
		}
	}

	protected void addUsmUser(USM usm)
	{
		 UsmUser user = new UsmUser(new OctetString("SHADES"),
                 AuthSHA.ID,
                 new OctetString("SHADESAuthPassword"),
                 PrivDES.ID,
                 new OctetString("SHADESPrivPassword"));
		usm.addUser(user.getSecurityName(), usm.getLocalEngineID(), user);
		user = new UsmUser(new OctetString("TEST"),
		                 AuthSHA.ID,
		                 new OctetString("maplesyrup"),
		                 PrivDES.ID,
		                 new OctetString("maplesyrup"));
		usm.addUser(user.getSecurityName(), usm.getLocalEngineID(), user);
		user = new UsmUser(new OctetString("SHA"),
		                 AuthSHA.ID,
		                 new OctetString("SHAAuthPassword"),
		                 null,
		                 null);
		usm.addUser(user.getSecurityName(), usm.getLocalEngineID(), user);
	}

	protected void addViews(VacmMIB vacm)
	{
		   vacm.addGroup(SecurityModel.SECURITY_MODEL_SNMPv1,
	                  new OctetString("public"),
	                  new OctetString("v1v2group"),
	                  StorageType.nonVolatile);
	    vacm.addGroup(SecurityModel.SECURITY_MODEL_SNMPv2c,
	                  new OctetString("public"),
	                  new OctetString("v1v2group"),
	                  StorageType.nonVolatile);
	    vacm.addGroup(SecurityModel.SECURITY_MODEL_USM,
	                  new OctetString("SHADES"),
	                  new OctetString("v3group"),
	                  StorageType.nonVolatile);
	    vacm.addGroup(SecurityModel.SECURITY_MODEL_USM,
	                  new OctetString("TEST"),
	                  new OctetString("v3test"),
	                  StorageType.nonVolatile);
	    vacm.addGroup(SecurityModel.SECURITY_MODEL_USM,
	                  new OctetString("SHA"),
	                  new OctetString("v3restricted"),
	                  StorageType.nonVolatile);

	    vacm.addAccess(new OctetString("v1v2group"), new OctetString(),
	                   SecurityModel.SECURITY_MODEL_ANY,
	                   SecurityLevel.NOAUTH_NOPRIV,
	                   MutableVACM.VACM_MATCH_EXACT,
	                   new OctetString("fullReadView"),
	                   new OctetString("fullWriteView"),
	                   new OctetString("fullNotifyView"),
	                   StorageType.nonVolatile);
	    vacm.addAccess(new OctetString("v3group"), new OctetString(),
	                   SecurityModel.SECURITY_MODEL_USM,
	                   SecurityLevel.AUTH_PRIV,
	                   MutableVACM.VACM_MATCH_EXACT,
	                   new OctetString("fullReadView"),
	                   new OctetString("fullWriteView"),
	                   new OctetString("fullNotifyView"),
	                   StorageType.nonVolatile);
	    vacm.addAccess(new OctetString("v3restricted"), new OctetString(),
	                   SecurityModel.SECURITY_MODEL_USM,
	                   SecurityLevel.AUTH_NOPRIV,
	                   MutableVACM.VACM_MATCH_EXACT,
	                   new OctetString("restrictedReadView"),
	                   new OctetString("restrictedWriteView"),
	                   new OctetString("restrictedNotifyView"),
	                   StorageType.nonVolatile);
	    vacm.addAccess(new OctetString("v3test"), new OctetString(),
	                   SecurityModel.SECURITY_MODEL_USM,
	                   SecurityLevel.AUTH_PRIV,
	                   MutableVACM.VACM_MATCH_EXACT,
	                   new OctetString("testReadView"),
	                   new OctetString("testWriteView"),
	                   new OctetString("testNotifyView"),
	                   StorageType.nonVolatile);

	    vacm.addViewTreeFamily(new OctetString("fullReadView"), new OID("1.3"),
	                           new OctetString(), VacmMIB.vacmViewIncluded,
	                           StorageType.nonVolatile);
	    vacm.addViewTreeFamily(new OctetString("fullWriteView"), new OID("1.3"),
	                           new OctetString(), VacmMIB.vacmViewIncluded,
	                           StorageType.nonVolatile);
	    vacm.addViewTreeFamily(new OctetString("fullNotifyView"), new OID("1.3"),
	                           new OctetString(), VacmMIB.vacmViewIncluded,
	                           StorageType.nonVolatile);
	}

	public void setTransportMapping(TransportMapping[] mappings)
	{
		transportMappings = mappings;
	}

	@Override
	protected void registerManagedObjects()
	{
	}

	@Override
	protected void unregisterManagedObjects()
	{
	}	
		
	public SnmpAddress[] getTrapReceivers()
	{
		return _trapReceivers;
	}

	public void setTrapReceivers(SnmpAddress[] trapReceivers)
	{
		SnmpAddress[] oldReceivers = _trapReceivers;
		_trapReceivers = trapReceivers;
		
		if (_server != null)
			_server.getContainer().update(this, oldReceivers, trapReceivers, "trap");
		
		if (isStarted())
		{      
	        if (oldReceivers!=null)
	        {
	            for (int i=oldReceivers.length;i-->0;)
	            {
	                if (oldReceivers[i]!=null)
	                {
	                	String key = "notification" + oldReceivers[i].getHost();
	                    snmpTargetMIB.removeTargetAddress(new OctetString(key));
	                }
	            }
	        }
	        
	        if (_trapReceivers!=null)
	        {
	            for (int i=0;i<_trapReceivers.length;i++)
	               addTrapHost(_trapReceivers[i]);
	        }
		}
	}
	
	public void addMib(MOGroup mib)
	{
		if (!_mibs.contains(mib))
			_mibs.add(mib);
	}
		
	@Override
	protected void registerSnmpMIBs()
	{
		super.registerSnmpMIBs();
		try
		{
			_mibs.add(new CipangoMib());
			_mibs.add(new JvmManagementMibInst(notificationOriginator));

			for (MOGroup mib : _mibs)
			{
				mib.registerMOs(server, null);
				if (mib instanceof Mib)
					((Mib) mib).setSnmpAgent(this);
			}

		}
		catch (DuplicateRegistrationException ex)
		{
			Log.warn("Unable to register MIBs", ex);
		}
	}

	@Override
	protected void unregisterSnmpMIBs()
	{
		super.unregisterSnmpMIBs();
		for (MOGroup moGroup : _mibs)
			moGroup.unregisterMOs(server, null);
	}
	
	

	public void addLifeCycleListener(Listener arg0)
	{
	}

	public boolean isFailed()
	{
		return false;
	}

	public boolean isRunning()
	{
		return getAgentState() == STATE_INIT_STARTED 
			||  getAgentState() == STATE_INIT_FINISHED
			||  getAgentState() == STATE_RUNNING;
	}

	public boolean isStarted()
	{
		return  getAgentState() == STATE_RUNNING;
	}

	public boolean isStarting()
	{
		return getAgentState() == STATE_INIT_STARTED;
	}

	public boolean isStopped()
	{
		return getAgentState() == STATE_STOPPED; 
	}

	public boolean isStopping()
	{
		return false; 
	}

	public void removeLifeCycleListener(Listener arg0)
	{
	}
	
	public SnmpAddress[] getConnectors()
	{
		return _connectors;
	}
	
	public void setConnectors(SnmpAddress[] connectors)
	{
		if (isStarted())
			throw new IllegalStateException("already started");
		if (_server != null)
			_server.getContainer().update(this, _connectors, connectors, "connectors");

		_connectors = connectors;
	}
	
	@Override
	protected void initTransportMappings() throws IOException {
		if (_connectors == null)
		{
			SnmpAddress[] connectors = new SnmpAddress[1];
			connectors[0] = new SnmpAddress();
			connectors[0].setPort(161);
			connectors[0].setHost("0.0.0.0");
			setConnectors(_connectors);
		}

	    transportMappings = new TransportMapping[_connectors.length];
	    for (int i = 0; i < _connectors.length; i++)
	    {
	    	if (_connectors[i].getPort() <= 0)
	    		_connectors[i].setPort(161);
	    	if (_connectors[i].isUdp())
	    	{
	    		UdpAddress address = new UdpAddress(_connectors[i].getInetAddress(), _connectors[i].getPort());
	    		transportMappings[i] = new DefaultUdpTransportMapping(address);
	    	}
	    	else
	    	{
	    		TcpAddress address = new TcpAddress(_connectors[i].getInetAddress(), _connectors[i].getPort());
	    		transportMappings[i] = new DefaultTcpTransportMapping(address);
	    	}
	    }
	  }

	public void setServer(Server server)
	{
		_server = server;
	}

	public String dump()
	{
		return AggregateLifeCycle.dump(this);
	}

	public void dump(Appendable out, String indent) throws IOException
	{
		out.append(String.valueOf(this)).append("\n");
		AggregateLifeCycle.dump(out,indent,Arrays.asList(_connectors), Arrays.asList(_trapReceivers));
	}
}
