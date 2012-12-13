/*
 * Copyright (c) 2012, salesforce.com, inc.
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
package com.salesforce.androidsdk.store;

import com.salesforce.androidsdk.store.SmartStore.SmartStoreException;

/**
 * Simple class to represent a query spec (soup query spec or smart query spec)
 */
public class QuerySpec {
    public final String path;
    public final QueryType queryType;

    // Exact
    public final String matchKey;
    // Range
    public final String beginKey;
    public final String endKey;
    // Like
    public final String likeKey;
    // Smart
    public final String smartSql;
    
    // Order
    public final Order order;

    // Page size
    public final int pageSize;

    // Private constructor for soup query spec
    private QuerySpec(String path, QueryType queryType, String matchKey, String beginKey, String endKey, String likeKey, Order order, int pageSize) {
        this.path = path;
        this.queryType = queryType;
        this.matchKey = matchKey;
        this.beginKey = beginKey;
        this.endKey = endKey;
        this.likeKey = likeKey;
        this.order = order;
        this.pageSize = pageSize;
        
        // Not applicable
        this.smartSql = null;
    }
    
    // Private constructor for smart query spec
    private QuerySpec(String smartSql, int pageSize) {
    	this.smartSql = smartSql;
    	this.pageSize = pageSize;
        this.queryType = QueryType.smart;
    	
    	// Not applicable
        this.path = null;
        this.matchKey = null;
        this.beginKey = null;
        this.endKey = null;
        this.likeKey = null;
        this.order = null;    	
    }

    /**
     * Return a query spec for returning all entries
     * @param order
     * @param pageSize
     * @return
     */
    public static QuerySpec buildAllQuerySpec(Order order, int pageSize) {
        return new QuerySpec(null, QueryType.range, null, null, null, null, order, pageSize);
    }


    /**
     * Return a query spec for an exact match query
     * @param path
     * @param exactMatchKey
     * @param pageSize
     * @return
     */
    public static QuerySpec buildExactQuerySpec(String path, String exactMatchKey, int pageSize) {
        return new QuerySpec(path, QueryType.exact, exactMatchKey, null, null, null, Order.ascending /* meaningless - all rows will have the same value in the indexed column*/, pageSize);
    }

    /**
     * Return a query spec for a range query
     * @param path
     * @param beginKey
     * @param endKey
     * @param order
     * @param pageSize
     * @return
     */
    public static QuerySpec buildRangeQuerySpec(String path, String beginKey, String endKey, Order order, int pageSize) {
        return new QuerySpec(path, QueryType.range, null, beginKey, endKey, null, order, pageSize);
    }

    /**
     * Return a query spec for a like query
     * @param path
     * @param matchKey
     * @param order
     * @param pageSize
     * @return
     */
    public static QuerySpec buildLikeQuerySpec(String path, String likeKey, Order order, int pageSize) {
        return new QuerySpec(path, QueryType.like, null, null, null, likeKey, order, pageSize);
    }

    /**
     * Return a query spec for a smart query
     * @param smartSql
     * @param pageSize
     * @return
     */
    public static QuerySpec buildSmartQuerySpec(String smartSql, int pageSize) {
    	return new QuerySpec(smartSql, pageSize);
    }
    
    /**
     * @param columnName
     * @return string representing sql predicate
     */
    public String getKeyPredicate(String columnName) {
        switch(queryType) {
        case exact:
            return columnName + " = ?";
        case like:
            return columnName + " LIKE ?";
        case range:
            if (beginKey == null && endKey == null)
                return null;
            else if (endKey == null)
                return columnName + " >= ? ";
            else if (beginKey == null)
                return columnName + " <= ? ";
            else
                return columnName + " >= ? AND " + columnName + " <= ?";
        default:
            throw new SmartStoreException("Fell through switch: " + queryType);
        }
    }

    /**
     * @return args going with the sql predicate returned by getKeyPredicate
     */
    public String[] getKeyPredicateArgs() {
        switch(queryType) {
        case exact:
            return new String[] {matchKey};
        case like:
            return new String[] {likeKey};
        case range:
            if (beginKey == null && endKey == null)
                return null;
            else if (endKey == null)
                return new String[] {beginKey};
            else if (beginKey == null)
                return new String[] {endKey};
            else
                return new String[] {beginKey, endKey};
        default:
            throw new SmartStoreException("Fell through switch: " + queryType);
        }
    }

    /**
     * @param columnName
     * @return sql for order by
     */
    public String getOrderBy(String columnName) {
        return columnName + " " + order.sql;
    }


    /**
     * Query type enum
     */
    public enum QueryType {
        exact,
        range,
        like,
        smart;
    }


    /**
     * Simple class to represent query order (used by QuerySpec)
     */
    public enum Order {
        ascending("ASC"), descending("DESC");

        public final String sql;

        Order(String sqlOrder) {
            this.sql = sqlOrder;
        }
    }

}