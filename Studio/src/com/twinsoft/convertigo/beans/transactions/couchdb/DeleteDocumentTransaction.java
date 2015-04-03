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

public class DeleteDocumentTransaction extends AbstractDocumentTransaction {

	private static final long serialVersionUID = 6392840891762384633L;
	
	private String q_rev = "";
	private String q_batch = "";
	
	public DeleteDocumentTransaction() {
		super();
	}

	@Override
	public DeleteDocumentTransaction clone() throws CloneNotSupportedException {
		DeleteDocumentTransaction clonedObject =  (DeleteDocumentTransaction) super.clone();
		return clonedObject;
	}
	
	@Override
	public List<CouchDbParameter> getDeclaredParameters() {
		return getDeclaredParameters(var_database, var_docid, var_docrev);
	}

	@Override
	protected Object invoke() throws Exception {
		String docId = getParameterStringValue(var_docid);
		String docRev = getParameterStringValue(var_docrev);
		
		return getCouchClient().deleteDocument(getTargetDatabase(), docId, docRev);
	}

	@Override
	public QName getComplexTypeAffectation() {
		return new QName(COUCHDB_XSD_NAMESPACE, "deleteDocumentType");
	}
	
	public String getQ_rev() {
		return q_rev;
	}

	public void setQ_rev(String q_rev) {
		this.q_rev = q_rev;
	}

	public String getQ_batch() {
		return q_batch;
	}

	public void setQ_batch(String q_batch) {
		this.q_batch = q_batch;
	}
}