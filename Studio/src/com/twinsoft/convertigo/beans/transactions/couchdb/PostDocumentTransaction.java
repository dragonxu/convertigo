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
 * $URL: $
 * $Author: $
 * $Revision: $
 * $Date: $
 */
package com.twinsoft.convertigo.beans.transactions.couchdb;

import java.util.List;

import javax.xml.namespace.QName;

import org.codehaus.jettison.json.JSONObject;

import com.twinsoft.convertigo.beans.variables.RequestableVariable;
import com.twinsoft.convertigo.engine.enums.CouchPostDocumentPolicy;

public class PostDocumentTransaction extends AbstractDocumentTransaction {

	private static final long serialVersionUID = -7606732916561433014L;
	
	private CouchPostDocumentPolicy policy = CouchPostDocumentPolicy.none;
	
	private String q_batch = "";
	
	public PostDocumentTransaction() {
		super();
	}

	@Override
	public PostDocumentTransaction clone() throws CloneNotSupportedException {
		PostDocumentTransaction clonedObject =  (PostDocumentTransaction) super.clone();
		return clonedObject;
	}
	
	@Override
	public List<CouchDbParameter> getDeclaredParameters() {
		return getDeclaredParameters(var_database, var_id, var_data);
	}
		
	@Override
	protected Object invoke() throws Exception {
		JSONObject jsonDocument = new JSONObject();
		
		// add document members from variables
		for (RequestableVariable variable : getVariablesList()) {
			String variableName = variable.getName();

			if (!variableName.equals(var_database.variableName())) {
				Object jsonElement = toJson(getParameterValue(variableName));
				addJson(jsonDocument, variableName, jsonElement);
			}
		}
		
		return getCouchClient().postDocument(getTargetDatabase(), jsonDocument, policy);
	}
	
	@Override
	public QName getComplexTypeAffectation() {
		return new QName(COUCHDB_XSD_NAMESPACE, "postDocumentType");
	}
	
	public CouchPostDocumentPolicy getPolicy() {
		return policy;
	}

	public void setPolicy(CouchPostDocumentPolicy policy) {
		this.policy = policy;
	}

	public String getQ_batch() {
		return q_batch;
	}

	public void setQ_batch(String q_batch) {
		this.q_batch = q_batch;
	}
}