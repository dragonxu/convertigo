/*
 * Copyright (c) 2001-2011 Convertigo SA.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 *
 * $URL$
 * $Author$
 * $Revision$
 * $Date$
 */

package com.twinsoft.convertigo.beans.core;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

import org.w3c.dom.Element;

import com.twinsoft.convertigo.beans.common.XMLVector;
import com.twinsoft.convertigo.engine.Engine;
import com.twinsoft.convertigo.engine.EngineException;
import com.twinsoft.convertigo.engine.util.ProjectUtils;
import com.twinsoft.convertigo.engine.util.VersionUtils;
import com.twinsoft.convertigo.engine.util.WSDLUtils;
import com.twinsoft.convertigo.engine.util.XSDUtils;
import com.twinsoft.convertigo.engine.util.XSDUtils.XSD;

/**
 * This class manages a Convertigo Project.
 */
public class Project extends DatabaseObject implements ITagsProperty {

	private static final long serialVersionUID = -7523308164370975102L;

	public static final int MAX_OBJECT_NAME_LENGTH = 64;
	
	public static final String WSDL_STYLE_ALL = WSDLUtils.WSDL_STYLE_ALL;
	public static final String WSDL_STYLE_DOC = WSDLUtils.WSDL_STYLE_DOC;
	public static final String WSDL_STYLE_RPC = WSDLUtils.WSDL_STYLE_RPC;
	
    /**
     * Construct a new Project object.
     */
    public Project() {
        super();
        databaseType = "Project";
        vPropertiesForAdmin.add("httpSessionTimeout");
    }

    /**
     * Retrieves the parent Object object.
     */
    @Override
    public DatabaseObject getParent() {
        return null;
    }
    
	/**
	 * Sets the object name.
	 *
	 * @param name the object name.
	 */
    @Override
	public void setName(String name) throws EngineException {
		name = name.trim();
		Project.checkName(name);
		oldName = ((oldName == null) ? name:this.name);
		this.name = name;
	}
    
	public static void checkName(String name) throws EngineException {
		if (name.length() > MAX_OBJECT_NAME_LENGTH) throw new EngineException("The project name is too long (length > " + MAX_OBJECT_NAME_LENGTH + ")!");
		else if (name.indexOf(' ') == 0) throw new EngineException("The project name cannot begin with the space character!");
		else if (name.length() == name.lastIndexOf(' ') + 1) throw new EngineException("The project name cannot end with the space character!");
		else if ((name.indexOf('\\') != -1) || (name.indexOf('/') != -1) || (name.indexOf(':') != -1) || (name.indexOf('*') != -1) ||
		(name.indexOf('?') != -1) || (name.indexOf('"') != -1) || (name.indexOf('<') != -1) || (name.indexOf('>') != -1) || (name.indexOf('|') != -1))
			throw new EngineException("The project name (\"" + name + "\") cannot contain one of the following character(s):\n\\/:*?\"<>|");
	}

    public String getPath() {
        return "/" + getName() + "/_data";
    }

    @Override
    public String getOldPath() {
        return "/" + getOldName() + "/_data";
    }

    @Override
    public String getFileName() {
        return "project.xml";
    }
    
    @Override
    public String getOldFileName() {
        return "project.xml";
    }

    /**
     * The HTTP session timeout.
     */
    private int httpSessionTimeout = 300;
    
    /** Holds value of property browserTypes. */
    private XMLVector<XMLVector<String>> browserDefinitions = new XMLVector<XMLVector<String>>();
    
    /**
     * The WSDL style (Doc/Literal, RPC or Both).
     */
	private String wsdlStyle = WSDL_STYLE_DOC;
	
	/**
	 * WSDL with inline schema or not
	 */
	private boolean schemaInline = true;
	
    public int getHttpSessionTimeout() {
        return httpSessionTimeout;
    }
    
    public void setHttpSessionTimeout(int httpSessionTimeout) {
        this.httpSessionTimeout = httpSessionTimeout;
    }
    
    /** Getter for property browserDefinitions.
     * @return Value of property browserDefinitions.
     */
    public XMLVector<XMLVector<String>> getBrowserDefinitions() {
        return this.browserDefinitions;
    }
    
    /** Setter for property browserDefinitions.
     * @param browserTypes New value of property browserDefinitions.
     */
    public void setBrowserDefinitions(XMLVector<XMLVector<String>> browserDefinitions) {
        this.browserDefinitions = browserDefinitions;
    }
    
    public String getWsdlStyle() {
		return wsdlStyle;
	}

	public void setWsdlStyle(String wsdlStyle) {
		this.wsdlStyle = wsdlStyle;
	}

	public boolean isSchemaInline() {
		return schemaInline;
	}

	public void setSchemaInline(boolean schemaInline) {
		this.schemaInline = schemaInline;
	}

	@Override
    public void add(DatabaseObject databaseObject) throws EngineException {
		if (databaseObject instanceof Connector)
			addConnector((Connector) databaseObject);
		else if (databaseObject instanceof Sequence)
			addSequence((Sequence) databaseObject);
		else if (databaseObject instanceof MobileDevice)
			addMobileDevice((MobileDevice) databaseObject);
        else throw new EngineException("You cannot add to a project a database object of type " + databaseObject.getClass().getName());
    }

    @Override
    public void remove(DatabaseObject databaseObject) throws EngineException {
		if (databaseObject instanceof Connector)
			removeConnector((Connector) databaseObject);
		else if (databaseObject instanceof Sequence)
			removeSequence((Sequence) databaseObject);
		else if (databaseObject instanceof MobileDevice)
			removeMobileDevice((MobileDevice) databaseObject);
		else throw new EngineException("You cannot remove from a project a database object of type " + databaseObject.getClass().getName());
		super.remove(databaseObject);
    }

	/**
	 * The vector of available connectors for this project.
	 */
	transient private List<Connector> vConnectors = new Vector<Connector>();
	
	/**
	 * Adds a connector.
	 */
	protected void addConnector(Connector connector) throws EngineException {
		checkSubLoaded();
		String newDatabaseObjectName = getChildBeanName(vConnectors, connector.getName(), connector.bNew);
		connector.setName(newDatabaseObjectName);
		vConnectors.add(connector);
		if (connector.isDefault) setDefaultConnector(connector);
		super.add(connector);
	}

	public void removeConnector(Connector connector) throws EngineException {
		checkSubLoaded();
		vConnectors.remove(connector);
	}
	
	@Deprecated
	public Vector<Connector> getConnectors() {
		return new Vector<Connector>(getConnectorsList());
	}
	
	public List<Connector> getConnectorsList() {
		checkSubLoaded();
		return sort(vConnectors);
	}

	public Connector getConnectorByName(String connectorName) throws EngineException {
		checkSubLoaded();
		for (Connector connector : vConnectors)
			if (connector.getName().equalsIgnoreCase(connectorName)) return connector;
		throw new EngineException("There is no connector named \"" + connectorName + "\" found into this project.");
	}
    
	/**
	 * The vector of available sequences for this project.
	 */
	transient private List<Sequence> vSequences = new Vector<Sequence>();

	/**
	 * Adds a sequence.
	 */
	protected void addSequence(Sequence sequence) throws EngineException {
		checkSubLoaded();
		String newDatabaseObjectName = getChildBeanName(vSequences, sequence.getName(), sequence.bNew);
		sequence.setName(newDatabaseObjectName);
		vSequences.add(sequence);
		super.add(sequence);
	}

	public void removeSequence(Sequence sequence) throws EngineException {
		checkSubLoaded();
		vSequences.remove(sequence);
	}

	@Deprecated
	public Vector<Sequence> getSequences() {
		return new Vector<Sequence>(getSequencesList());
	}
	
	public List<Sequence> getSequencesList() {
		checkSubLoaded();
		return sort(vSequences);
	}

	public Sequence getSequenceByName(String sequenceName) throws EngineException {
		checkSubLoaded();
		for (Sequence sequence : vSequences)
			if (sequence.getName().equalsIgnoreCase(sequenceName)) return sequence;
		throw new EngineException("There is no sequence named \"" + sequenceName + "\" found into this project.");
	}
	
	/**
	 * The vector of available mobile device for this project.
	 */
	transient private List<MobileDevice> vMobileDevices = new Vector<MobileDevice>();

	/**
	 * Adds a mobile device.
	 */
	protected void addMobileDevice(MobileDevice device) throws EngineException {
		checkSubLoaded();
		String newDatabaseObjectName = getChildBeanName(vMobileDevices, device.getName(), device.bNew);
		device.setName(newDatabaseObjectName);
		vMobileDevices.add(device);
		super.add(device);
	}

	public void removeMobileDevice(MobileDevice device) throws EngineException {
		checkSubLoaded();
		vMobileDevices.remove(device);
	}

	public List<MobileDevice> getMobileDeviceList() {
		checkSubLoaded();
		return sort(vMobileDevices);
	}

	public MobileDevice getMobileDeviceByName(String deviceName) throws EngineException {
		checkSubLoaded();
		for (MobileDevice device : vMobileDevices)
			if (device.getName().equalsIgnoreCase(deviceName)) return device;
		throw new EngineException("There is no mobile device named \"" + deviceName + "\" found into this project.");
	}
	
	/**
	/**
	 * The default connector for this project.
	 */
	transient private Connector defaultConnector = null;
    
	/**
	 * Retrieves the default connector.
	 */
	public Connector getDefaultConnector() throws EngineException {
		if (defaultConnector == null) {
			checkSubLoaded();
			for (Connector connector : vConnectors)
				if (connector.isDefault) {
					defaultConnector = connector;
					break;
				}
		}
        
		// Report from 4.5: fix #401
		if (defaultConnector == null) {
			// Fire exception in Engine mode only!
			if (Engine.isEngineMode()) {
				throw new EngineException("There is no default connector defined for project \"" + getName() + "\".");
			}
			else {
				//In Studio mode we must be able to set a default connector!
			}
		}
		
		return defaultConnector;
	}

	/**
	 * Sets the default connector.
	 */
	public synchronized void setDefaultConnector(Connector connector) throws EngineException {
		if (connector == null)
			throw new IllegalArgumentException("The value of argument 'transaction' is null");
		checkSubLoaded();
		if (vConnectors.contains(connector)) {
			if (defaultConnector == null) getDefaultConnector();
			if (defaultConnector != null) defaultConnector.isDefault = false;
			connector.isDefault = true;
			defaultConnector = connector;
		} else throw new IllegalArgumentException("The value of argument 'connector' is invalid: the connector does not belong to the project");
	}
	
	@Override
	public Object clone() throws CloneNotSupportedException {
		Project clonedObject = (Project) super.clone();
		clonedObject.defaultConnector = null;
		clonedObject.vConnectors = new Vector<Connector>();
		clonedObject.vSequences = new Vector<Sequence>();
		clonedObject.vMobileDevices = new Vector<MobileDevice>();
		clonedObject.xsdDirty = true;
		clonedObject.xsdTypes = null;
		return clonedObject;
	}

	transient private boolean xsdDirty = true;
	transient private String[] xsdTypes = null;
	
	public void setXsdDirty(boolean dirty) {
		xsdDirty = dirty;
	}
	
	/*
	 * Retrieve all types
	 */
	public String[] getXsdTypes() {
		if ((xsdTypes == null) || xsdDirty) {
			try {
				String xsdUri = Engine.PROJECTS_PATH + "/" + name + "/" + name + ".temp.xsd";
				XSD xsd = XSDUtils.getXSD(xsdUri);
				xsdTypes = xsd.getTypes();
				xsdDirty = false;
			} catch (Exception e) {
				Engine.logBeans.warn("Error while trying to get XSD types: "+ e.getMessage());
				xsdTypes = new String[]{"xsd:string"};
			}
		}
		return xsdTypes;
	}
	
	/*
	 * Retrieve types of specified namespace prefix
	 */
	public String[] getXsdTypes(String prefix) {
		List<String> list = Arrays.asList(getXsdTypes());
		ArrayList<String> nsList = new ArrayList<String>();
		for (String s: list) {
			if (s.startsWith(prefix+":"))
				nsList.add(s);
		}
		return nsList.toArray(new String[]{});
	}

	/* (non-Javadoc)
	 * @see com.twinsoft.convertigo.beans.core.ITagsProperty#getTagsForProperty(java.lang.String)
	 */
	public String[] getTagsForProperty(String propertyName) {
		if (propertyName.equals("wsdlStyle")) {
			return new String[]{WSDL_STYLE_ALL, WSDL_STYLE_DOC, WSDL_STYLE_RPC};
		}
		return new String[0];
	}
	
    @Override
	public void configure(Element element) throws Exception {
		super.configure(element);
		
		String version = element.getAttribute("version");
		
		 if (version!= null && VersionUtils.compareMigrationVersion(version, ".m002") < 0) {
	        	Engine.logDatabaseObjectManager.info("Project's file migration to m002 index.html ...");
	        	String projectRoot = Engine.PROJECTS_PATH+'/'+getName();
	        	File indexPage = new File(projectRoot+"/index.html");
	        	if(indexPage.exists()){
	        		Engine.logDatabaseObjectManager.info("index.html found, rename it to index_old.html");
	        		indexPage.renameTo(new File(projectRoot+"/index_old.html"));
	        	}
	        	ProjectUtils.copyIndexFile(getName());
	        	Engine.logDatabaseObjectManager.info("Basic index.html copied");
		 }
    }

	@Override
	public List<DatabaseObject> getAllChildren() {	
		List<DatabaseObject> rep = super.getAllChildren();
		List<Connector> connectors = getConnectorsList();		
		for(Connector conn: connectors){
			rep.add(conn);
		}
		List<Sequence> sequences = getSequencesList();	
		for(Sequence sequence: sequences){
			rep.add(sequence);
		}
		List<MobileDevice> devices = getMobileDeviceList();	
		for(MobileDevice device: devices){
			rep.add(device);
		}		
		return rep;
	}
}
