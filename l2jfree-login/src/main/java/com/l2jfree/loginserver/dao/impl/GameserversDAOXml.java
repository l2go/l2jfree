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
package com.l2jfree.loginserver.dao.impl;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.l2jfree.loginserver.beans.Gameservers;
import com.l2jfree.loginserver.dao.GameserversDAO;

/**
 * DAO object for domain model class Gameservers.
 * Xml implementation.
 * 
 * @see com.l2jfree.loginserver.beans.Gameservers
 */
public class GameserversDAOXml implements GameserversDAO
{
	private static final Log _log = LogFactory.getLog(GameserversDAOXml.class);
	
	private final Map<Integer, Gameservers> serverNames = new TreeMap<Integer, Gameservers>();
	
	/**
	 * Load server name from xml
	 */
	public GameserversDAOXml()
	{
		try
		{
			InputStream in;
			try
			{
				in = new FileInputStream("servername.xml");
			}
			catch (FileNotFoundException e)
			{
				// just for eclipse development, we have to search in dist folder
				in = new FileInputStream("dist/servername.xml");
			}
			
			try (InputStream serverNamesInput = in)
			{
				DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				factory.setNamespaceAware(true);
				factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
				factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
				factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
				factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
				factory.setXIncludeAware(false);
				factory.setExpandEntityReferences(false);
				factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
				factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
				org.w3c.dom.Document document = factory.newDocumentBuilder().parse(serverNamesInput);
				NodeList servers = document.getElementsByTagNameNS("*", "server");
				if (servers.getLength() == 0)
				{
					servers = document.getElementsByTagName("server");
				}
				for (int i = 0; i < servers.getLength(); i++)
				{
					Element server = (Element)servers.item(i);
					String id = server.getAttribute("id");
					String name = server.getAttribute("name");
					if (!id.isEmpty() && !name.isEmpty())
					{
						Gameservers gs = new Gameservers();
						gs.setServerId(Integer.parseInt(id));
						gs.setServerName(name);
						serverNames.put(gs.getServerId(), gs);
					}
				}
			}
			_log.info("Loaded " + serverNames.size() + " server names");
		}
		catch (FileNotFoundException e)
		{
			_log.warn("servername.xml could not be loaded : " + e.getMessage(), e);
		}
		catch (IOException | SAXException | ParserConfigurationException e)
		{
			_log.warn("servername.xml could not be loaded : " + e.getMessage(), e);
		}
		catch (RuntimeException e)
		{
			_log.warn("servername.xml contains invalid server data : " + e.getMessage(), e);
		}
	}
	
	/**
	 * Search by id
	 * @param id
	 * @return
	 */
	@Override
	public Gameservers getGameserverByServerId(int id)
	{
		return serverNames.get(id);
	}
	
	/**
	 * @see com.l2jfree.loginserver.dao.GameserversDAO#createGameserver(Gameservers)
	 */
	@Override
	public int createGameserver(Gameservers obj)
	{
		serverNames.put(obj.getServerId(), obj);
		return obj.getServerId();
	}
	
	/**
	 * @see com.l2jfree.loginserver.dao.GameserversDAO#createOrUpdate(Gameservers)
	 */
	@Override
	public void createOrUpdate(Gameservers obj)
	{
		createGameserver(obj);
		
	}
	
	/**
	 * @see com.l2jfree.loginserver.dao.GameserversDAO#createOrUpdateAll(java.util.Collection)
	 */
	@Override
	public void createOrUpdateAll(Collection<?> entities)
	{
		Iterator<?> it = entities.iterator();
		while (it.hasNext())
		{
			createGameserver((Gameservers)it.next());
		}
	}
	
	/**
	 * @see com.l2jfree.loginserver.dao.GameserversDAO#getAllGameservers()
	 */
	@Override
	public List<Gameservers> getAllGameservers()
	{
		return new ArrayList<Gameservers>(serverNames.values());
	}
	
	/**
	 * @see com.l2jfree.loginserver.dao.GameserversDAO#removeGameservers(Gameservers)
	 */
	@Override
	public void removeGameserver(Gameservers obj)
	{
		serverNames.remove(obj.getServerId());
		
	}
	
	/**
	 * @see com.l2jfree.loginserver.dao.GameserversDAO#removeAccountById(java.io.Serializable)
	 */
	@Override
	public void removeGameserverByServerId(int id)
	{
		serverNames.remove(id);
	}
	
	/**
	 * @see com.l2jfree.loginserver.dao.GameserversDAO#removeAll(java.util.Collection)
	 */
	@Override
	public void removeAll(Collection<?> entities)
	{
		Iterator<?> it = entities.iterator();
		while (it.hasNext())
		{
			removeGameserver((Gameservers)it.next());
		}
	}
	
	/**
	 * @see com.l2jfree.loginserver.dao.GameserversDAO#update(java.lang.Object)
	 */
	@Override
	public void update(Object obj)
	{
		Gameservers gs = (Gameservers)obj;
		removeGameserverByServerId(gs.getServerId());
		createGameserver(gs);
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.loginserver.dao.GameserversDAO#removeAll()
	 */
	@Override
	public void removeAll()
	{
		serverNames.clear();
	}
}
