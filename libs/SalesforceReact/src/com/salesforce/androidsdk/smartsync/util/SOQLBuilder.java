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

import android.net.Uri;
import android.text.TextUtils;

import java.util.HashMap;
import java.util.List;

/**
 * Helper class to build a SOQL statement.
 *
 * @author bhariharan
 */
public class SOQLBuilder {

    private final HashMap<String, Object> properties;

    /**
     * Returns an instance of this class based on the fields passed as a comma separated string.
     *
     * @param fields Fields.
     * @return Instance of this class.
     */
    public static SOQLBuilder getInstanceWithFields(String fields) {
        final SOQLBuilder instance = new SOQLBuilder();
        instance.fields(fields);
        instance.limit(0);
        instance.offset(0);
        return instance;
    }
    
    /**
     * Returns an instance of this class based on the fields passed as an array of strings.
     *
     * @param fields Fields.
     * @return Instance of this class.
     */
    public static SOQLBuilder getInstanceWithFields(String... fields) {
    	return getInstanceWithFields(TextUtils.join(", ", fields));
    }
    
    /**
     * Returns an instance of this class based on the fields passed as a list of strings.
     *
     * @param fields Fields.
     * @return Instance of this class.
     */
    public static SOQLBuilder getInstanceWithFields(List<String> fields) {
    	return getInstanceWithFields(TextUtils.join(", ", fields));
    }

    
    /**
     * Private constructor.
     */
    private SOQLBuilder() {
        properties = new HashMap<String, Object>();
    }

    /**
     * Adds the 'from' clause.
     *
     * @param from From clause.
     * @return Instance with 'from' clause.
     */
    public SOQLBuilder from(String from) {
        properties.put("from", from);
        return this;
    }

    /**
     * Adds the 'where' clause.
     *
     * @param where Where clause.
     * @return Instance with 'where' clause.
     */
    public SOQLBuilder where(String where) {
        properties.put("where", where);
        return this;
    }

    /**
     * Adds the 'with' clause.
     *
     * @param with With clause.
     * @return Instance with 'with' clause.
     */
    public SOQLBuilder with(String with) {
        properties.put("with", with);
        return this;
    }

    /**
     * Adds the 'groupBy' clause.
     *
     * @param groupBy Group by clause.
     * @return Instance with 'groupBy' clause.
     */
    public SOQLBuilder groupBy(String groupBy) {
        properties.put("groupBy", groupBy);
        return this;
    }

    /**
     * Adds the 'having' clause.
     *
     * @param having Having clause.
     * @return Instance with 'having' clause.
     */
    public SOQLBuilder having(String having) {
        properties.put("having", having);
        return this;
    }

    /**
     * Adds the 'orderBy' clause.
     *
     * @param orderBy Order by clause.
     * @return Instance with 'orderBy' clause.
     */
    public SOQLBuilder orderBy(String orderBy) {
        properties.put("orderBy", orderBy);
        return this;
    }

    /**
     * Adds the 'limit' clause.
     *
     * @param limit Limit clause.
     * @return Instance with 'limit' clause.
     */
    public SOQLBuilder limit(Integer limit) {
        properties.put("limit", limit);
        return this;
    }

    /**
     * Adds the 'offset' clause.
     *
     * @param offset Offset clause.
     * @return Instance with 'offset' clause.
     */
    public SOQLBuilder offset(Integer offset) {
        properties.put("offset", offset);
        return this;
    }

    /**
     * Builds and encodes the query.
     *
     * @return URI encoded query.
     */
    public String buildAndEncode() {
        return Uri.encode(build());
    }

    /**
     * Builds and encodes with path.
     *
     * @param path Path.
     * @return URI encoded query with path.
     */
    public String buildAndEncodeWithPath(String path) {
        String result = null;
        if (path != null) {
            if (path.endsWith("/")) {
                result = Uri.encode(String.format("%squery/?q=%s", path, build()));
            } else {
                result = Uri.encode(String.format("%s/query/?q=%s", path, build()));
            }
        }
        return result;
    }

    /**
     * Builds with path.
     *
     * @param path Path.
     * @return Query with path.
     */
    public String buildWithPath(String path) {
        String result = null;
        if (path != null) {
            if (path.endsWith("/")) {
                result = String.format("%squery/?q=%s", path, build());
            } else {
                result = String.format("%s/query/?q=%s", path, build());
            }
        }
        return result;
    }

    /**
     * Builds the query.
     *
     * @return Query.
     */
    public String build() {
        final StringBuilder query = new StringBuilder();
        final String fieldList = (String) properties.get("fields");
        if (fieldList == null || fieldList.length() == 0) {
            return null;
        }
        query.append("select ");
        query.append(fieldList);
        final String from = (String) properties.get("from");
        if (from == null || from.length() == 0) {
            return null;
        }
        query.append(" from ");
        query.append(from);
        final String where = (String) properties.get("where");
        if (where != null && where.length() > 0) {
            query.append(" where ");
            query.append(where);
        }
        final String groupBy = (String) properties.get("groupBy");
        if (groupBy != null && groupBy.length() > 0) {
            query.append(" group by ");
            query.append(groupBy);
        }
        final String having = (String) properties.get("having");
        if (having != null && having.length() > 0) {
            query.append(" having ");
            query.append(having);
        }
        final String orderBy = (String) properties.get("orderBy");
        if (orderBy != null && orderBy.length() > 0) {
            query.append(" order by ");
            query.append(orderBy);
        }
        final Integer limit = (Integer) properties.get("limit");
        if (limit != null && limit > 0) {
            query.append(" limit ");
            query.append(String.format("%d", limit));
        }
        final Integer offset = (Integer) properties.get("offset");
        if (offset != null && offset > 0) {
            query.append(" offset ");
            query.append(String.format("%d", offset));
        }
        return query.toString();
    }

    private SOQLBuilder fields(String fields) {
        properties.put("fields", fields);
        return this;
    }
}
