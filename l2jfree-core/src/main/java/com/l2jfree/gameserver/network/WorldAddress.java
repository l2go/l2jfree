/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jfree.gameserver.network;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.tools.network.SubNetHost;

/**
 * Chooses the address a client must use to reach the world. The choice follows the subnet configuration: the
 * first host whose subnets contain the client address wins, and a client that matches no host gets its own
 * address back. The host names are resolved once, when the object is built.
 * <p>
 * The net-config string is a list of entries separated by {@code ;}. An entry is a host, optionally followed by
 * {@code ,} and the subnets in the form {@code network/mask} that use it. A host without subnets matches every
 * client.
 */
public final class WorldAddress
{
	private static final Logger _log = LoggerFactory.getLogger(WorldAddress.class);

	private final List<SubNetHost> _hosts = new ArrayList<SubNetHost>();

	/**
	 * @param netConfig the net-config string, see the class description. A null or blank string has no hosts.
	 */
	public WorldAddress(String netConfig)
	{
		parse(netConfig);
		resolve();
	}

	/**
	 * Builds the net-config string from the three settings of the server configuration. The subnets, when
	 * present, replace both hostnames. Without subnets, the internal host serves the private networks and the
	 * external host serves everybody else.
	 *
	 * @param external the external hostname
	 * @param internal the internal hostname
	 * @param subnetworks the subnet list of {@code subnets.properties}, already joined with {@code ;}
	 * @return the net-config string
	 */
	public static String netConfig(String external, String internal, String subnetworks)
	{
		String config1 = external == null ? "" : external;
		String config2 = internal == null ? "" : internal;

		if (subnetworks != null && subnetworks.length() > 0)
		{
			config1 = subnetworks;
			config2 = "";
		}

		// network configuration string formed on server
		if (config1.contains(";") || config1.contains(","))
			return config1;

		String netConfig = "";
		if (config2.length() > 0) // internal hostname and default internal networks
			netConfig = config2 + "," + "10.0.0.0/8,192.168.0.0/16" + ";";
		if (config1.length() > 0) // external hostname and all available addresses by default
			netConfig += config1 + "," + "0.0.0.0/0" + ";";

		return netConfig;
	}

	/**
	 * @param clientIp the address the client used to reach the login port
	 * @return the address of the first host with a subnet that contains the client, else the client's own address
	 */
	public String addressFor(String clientIp)
	{
		for (SubNetHost host : _hosts)
			if (host.isInSubnet(clientIp))
			{
				String address = host.getIp();
				// a host that could not be resolved cannot be announced: fall back to the client's own address
				return address == null ? clientIp : address;
			}

		return clientIp;
	}

	private void parse(String netConfig)
	{
		if (netConfig == null)
			return;

		StringTokenizer hostNets = new StringTokenizer(netConfig.trim(), ";");

		while (hostNets.hasMoreTokens())
		{
			String hostNet = hostNets.nextToken().trim();
			if (hostNet.isEmpty())
				continue;

			StringTokenizer addresses = new StringTokenizer(hostNet, ",");
			if (!addresses.hasMoreTokens())
				continue;

			SubNetHost host = new SubNetHost(addresses.nextToken().trim());

			if (addresses.hasMoreTokens())
			{
				while (addresses.hasMoreTokens())
				{
					try
					{
						StringTokenizer netmask = new StringTokenizer(addresses.nextToken().trim(), "/");
						String net = netmask.nextToken();
						String mask = netmask.nextToken();

						host.addSubNet(net, mask);
					}
					catch (NoSuchElementException e)
					{
						// a subnet without a mask is ignored
					}
				}
			}
			else
				host.addSubNet("0.0.0.0", "0");

			_hosts.add(host);
		}
	}

	private void resolve()
	{
		for (SubNetHost host : _hosts)
		{
			String name = host.getHostname();
			try
			{
				String address = InetAddress.getByName(name).getHostAddress();
				host.setIp(address);
				_log.info("World address: {}", name.equals(address) ? address : name + " (" + address + ")");
			}
			catch (UnknownHostException e)
			{
				_log.warn("Couldn't resolve hostname \"{}\"", name);
			}
		}
	}
}
