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

package com.twinsoft.convertigo.beans.statements;

import java.util.List;

import com.twinsoft.convertigo.beans.core.IScreenClassContainer;
import com.twinsoft.convertigo.beans.core.ScreenClass;
import com.twinsoft.convertigo.engine.util.StringUtils;

public class ScHandlerStatement extends HandlerStatement{

	private static final long serialVersionUID = -6843768711473408997L;

    public static final String EVENT_ENTRY_HANDLER = "Entry";
	public static final String EVENT_EXIT_HANDLER = "Exit";
	
	public static final String RETURN_REDETECT = "redetect";
	public static final String RETURN_CONTINUE = "continue";
    public static final String RETURN_SKIP = "skip";
    public static final String RETURN_ACCUMULATE = "accumulate";
	
    public static final String CHOOSE_SCREENCLASS_NAME = "[Please choose a screen class]";
    
	private String normalizedScreenClassName = "";
	
	public ScHandlerStatement(String handlerType) {
		this(handlerType,CHOOSE_SCREENCLASS_NAME);
	}
	
	public ScHandlerStatement(String handlerType, String normalizedScreenClassName) {
		super(handlerType,"");
		this.normalizedScreenClassName = (normalizedScreenClassName.equals(CHOOSE_SCREENCLASS_NAME) ? "":normalizedScreenClassName);
		if (handlerType.equals(EVENT_ENTRY_HANDLER)) {
			this.handlerResult = RETURN_REDETECT;
			this.name = "on" + normalizedScreenClassName + "Entry";
		}
		else {
			this.handlerResult = RETURN_ACCUMULATE;
			this.name = "on" + normalizedScreenClassName + "Exit";
		}
	}
	
	/**
	 * @return Returns the normalizedScreenClassName.
	 */
	public String getNormalizedScreenClassName() {
		return normalizedScreenClassName;
	}

	/**
	 * @param normalizedScreenClassName The normalizedScreenClassName to set.
	 */
	public void setNormalizedScreenClassName(String normalizedScreenClassName) {
		this.normalizedScreenClassName = normalizedScreenClassName;
	}

	public String[] getTypeStrings() {
		return new String[] { EVENT_ENTRY_HANDLER, EVENT_EXIT_HANDLER };
	}
	
	@Override
	public String[] getResultStrings() {
		if (handlerType.equals(EVENT_ENTRY_HANDLER))
			return new String[] { RETURN_CONTINUE, RETURN_REDETECT, RETURN_SKIP };
		else
			return new String[] { RETURN_ACCUMULATE };
	}
	
	@Override
	public String[] getTagsForProperty(String propertyName) {
		if(propertyName.equals("normalizedScreenClassName")){
			IScreenClassContainer<?> connector = (IScreenClassContainer<?>) getParent().getParent();
	    	List<? extends ScreenClass> v = connector.getAllScreenClasses();
			String[] sNames = new String[v.size()];
			for (int i = 0 ; i < v.size() ; i++) {
				String normalizedScreenClassName = StringUtils.normalize(v.get(i).getName());
				sNames[i] = normalizedScreenClassName;
			}
			return sNames;
		}
		return super.getTagsForProperty(propertyName);
	}
}
