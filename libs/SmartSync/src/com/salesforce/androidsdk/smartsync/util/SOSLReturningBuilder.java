/*
 * Copyright (c) 2014, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.androidsdk.smartsync.util;

import java.util.HashMap;

/**
 * Helper class to build a SOSL returning statement.
 *
 * @author bhariharan
 */
public class SOSLReturningBuilder {

    private final HashMap<String, Object> properties;

    /**
     * Returns an instance of this class based on the object name.
     *
     * @param name Object name.
     * @return Instance of this class.
     */
    public static SOSLReturningBuilder getInstanceWithObjectName(String name) {
        final SOSLReturningBuilder instance = new SOSLReturningBuilder();
        instance.objectName(name);
        instance.limit(0);
        return instance;
    }

    /**
     * Private constructor.
     */
    private SOSLReturningBuilder() {
        properties = new HashMap<String, Object>();
    }

    /**
     * Adds the 'fields' clause.
     *
     * @param fields Fields clause.
     * @return Instance with 'fields' clause.
     */
    public SOSLReturningBuilder fields(String fields) {
        properties.put("fields", fields);
        return this;
    }

    /**
     * Adds the 'where' clause.
     *
     * @param where Where clause.
     * @return Instance with 'where' clause.
     */
    public SOSLReturningBuilder where(String where) {
        properties.put("where", where);
        return this;
    }

    /**
     * Adds the 'orderBy' clause.
     *
     * @param orderBy Order by clause.
     * @return Instance with 'orderBy' clause.
     */
    public SOSLReturningBuilder orderBy(String orderBy) {
        properties.put("orderBy", orderBy);
        return this;
    }

    /**
     * Adds the 'limit' clause.
     *
     * @param limit Limit clause.
     * @return Instance with 'limit' clause.
     */
    public SOSLReturningBuilder limit(Integer limit) {
        properties.put("limit", limit);
        return this;
    }

    /**
     * Adds the 'withNetwork' clause.
     *
     * @param networkId Network ID.
     * @return Instance with 'withNetwork' clause.
     */
    public SOSLReturningBuilder withNetwork(String networkId) {
        properties.put("withNetwork", networkId);
        return this;
    }

    /**
     * Builds the query.
     *
     * @return Query.
     */
    public String build() {
        final StringBuilder query = new StringBuilder();
        final String objectName = (String) properties.get("objectName");
        if (objectName == null || objectName.length() == 0) {
            return null;
        }
        query.append(" ");
        query.append(objectName);
        final String fields = (String) properties.get("fields");
        if (fields != null && fields.length() > 0) {
            query.append(String.format("(%s", fields));
            final String where = (String) properties.get("where");
            if (where != null && where.length() > 0) {
                query.append(" where ");
                query.append(where);
            }
            final String orderBy = (String) properties.get("orderBy");
            if (orderBy != null && orderBy.length() > 0) {
                query.append(" order by ");
                query.append(orderBy);
            }
            final String withNetwork = (String) properties.get("withNetwork");
            if (withNetwork != null && withNetwork.length() > 0) {
                query.append(" with network = ");
                query.append(withNetwork);
            }
            final Integer limit = (Integer) properties.get("limit");
            if (limit != null && limit > 0) {
                query.append(" limit ");
                query.append(String.format("%d", limit));
            }
            query.append(")");
        }
        return query.toString();
    }

    private SOSLReturningBuilder objectName(String name) {
        properties.put("objectName", name);
        return this;
    }
}
