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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Helper class to build a SOSL statement.
 *
 * @author bhariharan
 */
public class SOSLBuilder {

    private final HashMap<String, Object> properties;
    private final List<Object> returning;

    /**
     * Returns an instance of this class based on the search term passed in.
     *
     * @param searchTerm Search term.
     * @return Instance of this class.
     */
    public static SOSLBuilder getInstanceWithSearchTerm(String searchTerm) {
        final SOSLBuilder instance = new SOSLBuilder();
        instance.searchTerm(searchTerm);
        instance.limit(0);
        return instance;
    }

    /**
     * Private constructor.
     */
    private SOSLBuilder() {
        properties = new HashMap<String, Object>();
        returning = new ArrayList<Object>();
    }

    /**
     * Adds the 'searchGroup' clause.
     *
     * @param searchGroup Search group clause.
     * @return Instance with 'searchGroup' clause.
     */
    public SOSLBuilder searchGroup(String searchGroup) {
        properties.put("searchGroup", searchGroup);
        return this;
    }

    /**
     * Adds the 'returningSpec' clause.
     *
     * @param returningSpec Returning spec clause.
     * @return Instance with 'returningSpec' clause.
     */
    public SOSLBuilder returning(SOSLReturningBuilder returningSpec) {
        returning.add(returningSpec);
        return this;
    }

    /**
     * Adds the 'divisionFilter' clause.
     *
     * @param divisionFilter Division filter clause.
     * @return Instance with 'divisionFilter' clause.
     */
    public SOSLBuilder divisionFilter(String divisionFilter) {
        properties.put("divisionFilter", divisionFilter);
        return this;
    }

    /**
     * Adds the 'dataCategory' clause.
     *
     * @param dataCategory Data category clause.
     * @return Instance with 'dataCategory' clause.
     */
    public SOSLBuilder dataCategory(String dataCategory) {
        properties.put("dataCategory", dataCategory);
        return this;
    }

    /**
     * Adds the 'limit' clause.
     *
     * @param limit Limit clause.
     * @return Instance with 'limit' clause.
     */
    public SOSLBuilder limit(Integer limit) {
        properties.put("limit", limit);
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
                result = Uri.encode(String.format("%ssearch/?q=%s", path, build()));
            } else {
                result = Uri.encode(String.format("%s/search/?q=%s", path, build()));
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
                result = String.format("%ssearch/?q=%s", path, build());
            } else {
                result = String.format("%s/search/?q=%s", path, build());
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
        final String searchTerm = (String) properties.get("searchTerm");
        if (searchTerm == null || searchTerm.length() == 0) {
            return null;
        }
        query.append(String.format("find {%s}", searchTerm));
        final String searchGroup = (String) properties.get("searchGroup");
        if (searchGroup != null && searchGroup.length() > 0) {
            query.append(" in ");
            query.append(searchGroup);
        }
        if (returning != null && returning.size() > 0) {
            query.append(" returning ");
            query.append(((SOSLReturningBuilder) returning.get(0)).build());
            for (int i = 1; i < returning.size(); i++) {
                query.append(", ");
                query.append(((SOSLReturningBuilder) returning.get(i)).build());
            }
        }
        final String divisionFilter = (String) properties.get("divisionFilter");
        if (divisionFilter != null && divisionFilter.length() > 0) {
            query.append(" with ");
            query.append(divisionFilter);
        }
        final String dataCategory = (String) properties.get("dataCategory");
        if (dataCategory != null && dataCategory.length() > 0) {
            query.append(" with data category ");
            query.append(dataCategory);
        }
        final Integer limit = (Integer) properties.get("limit");
        if (limit != null && limit > 0) {
            query.append(" limit ");
            query.append(String.format("%d", limit));
        }
        return query.toString();
    }

    private SOSLBuilder searchTerm(String searchTerm) {
        String searchValue = (searchTerm == null) ? "" : searchTerm;

        // Escapes special characters from search term.
        if (!searchValue.equals("")) {
            searchValue = searchValue.replace("\\", "\\\\");
            searchValue = searchValue.replace("+", "\\+");
            searchValue = searchValue.replace("^", "\\^");
            searchValue = searchValue.replace("~", "\\~");
            searchValue = searchValue.replace("'", "\\'");
            searchValue = searchValue.replace("-", "\\-");
            searchValue = searchValue.replace("[", "\\[");
            searchValue = searchValue.replace("]", "\\]");
            searchValue = searchValue.replace("{", "\\{");
            searchValue = searchValue.replace("}", "\\}");
            searchValue = searchValue.replace("(", "\\(");
            searchValue = searchValue.replace(")", "\\)");
            searchValue = searchValue.replace("&", "\\&");
            searchValue = searchValue.replace(":", "\\:");
            searchValue = searchValue.replace("!", "\\!");
        }
        properties.put("searchTerm", searchValue);
        return this;
    }
}
