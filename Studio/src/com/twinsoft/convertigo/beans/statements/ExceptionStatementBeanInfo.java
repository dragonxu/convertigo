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

import java.beans.PropertyDescriptor;

import com.twinsoft.convertigo.beans.core.MySimpleBeanInfo;

public class ExceptionStatementBeanInfo extends MySimpleBeanInfo {
    
	public ExceptionStatementBeanInfo() {
		try {
			beanClass = ExceptionStatement.class;
			additionalBeanClass = com.twinsoft.convertigo.beans.statements.SimpleStatement.class;

			iconNameC16 = "/com/twinsoft/convertigo/beans/statements/images/exception_16x16.gif";
			iconNameC32 = "/com/twinsoft/convertigo/beans/statements/images/exception_32x32.gif";
			
			resourceBundle = java.util.ResourceBundle.getBundle("com/twinsoft/convertigo/beans/statements/res/ExceptionStatement");
			
			displayName = resourceBundle.getString("display_name");
			shortDescription = resourceBundle.getString("short_description");
			
			properties = new PropertyDescriptor[1];

            properties[0] = new PropertyDescriptor("expression", beanClass, "getExpression", "setExpression");
            properties[0].setPropertyEditorClass(getEditorClass("ScriptCellEditor"));
            
		}
		catch(Exception e) {
			com.twinsoft.convertigo.engine.Engine.logBeans.error("Exception with bean info; beanClass=" + beanClass.toString(), e);
		}
	}

}
