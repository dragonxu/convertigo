/*
 * Copyright (c) 2001-2019 Convertigo SA.
 * 
 * This program  is free software; you  can redistribute it and/or
 * Modify  it  under the  terms of the  GNU  Affero General Public
 * License  as published by  the Free Software Foundation;  either
 * version  3  of  the  License,  or  (at your option)  any  later
 * version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY;  without even the implied warranty of
 * MERCHANTABILITY  or  FITNESS  FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program;
 * if not, see <http://www.gnu.org/licenses/>.
 */

package com.twinsoft.convertigo.beans.mobile.components;

import java.util.List;
import com.twinsoft.convertigo.beans.core.Project;
import com.twinsoft.convertigo.engine.Engine;

public class UIDynamicInvoke extends UIDynamicAction {

	private static final long serialVersionUID = 2244390717640903291L;

	private String stack = "";
	
	public UIDynamicInvoke() {
		super();
	}

	public UIDynamicInvoke(String tagName) {
		super(tagName);
	}

	@Override
	public UIDynamicInvoke clone() throws CloneNotSupportedException {
		UIDynamicInvoke cloned = (UIDynamicInvoke) super.clone();
		return cloned;
	}
	
	@Override
	public String getActionName() {
		UIActionStack uias = getTargetStack();
		return uias == null ? "ErrorAction" : uias.getStackName();
	}

	public String getActionStack() {
		return stack;
	}

	public void setActionStack(String stack) {
		this.stack = stack;
	}

	private UIActionStack getTargetStack() {
		try {
			String qname =  getActionStack();
			if (qname != null && qname.indexOf('.') != -1) {
				String p_name = qname.substring(0, qname.indexOf('.'));
				Project project = this.getProject();
				Project p = p_name.equals(project.getName()) ? project: Engine.theApp.databaseObjectsManager.getOriginalProjectByName(p_name);
				if (p != null) {
					for (UIActionStack uias: p.getMobileApplication().getApplicationComponent().getStackComponentList()) {
						if (uias.getQName().equals(qname)) {
							return uias;
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	@Override
	protected Contributor getContributor() {
		// ErrorAction contributor or null
		return getTargetStack() == null ? super.getContributor() : null;
	}
	
	@Override
	protected void addContributors(List<Contributor> contributors) {
		super.addContributors(contributors);
		
		// Now, add target stack contributors
		UIActionStack uias = getTargetStack();
		if (uias != null) {
			uias.addContributors(contributors);
		}
	}

	@Override
	public String toString() {
		String stackName = this.stack.isEmpty() ? "?" : this.stack.substring(this.stack.lastIndexOf('.') + 1);
		return "invoke " + stackName;
	}
	
}
