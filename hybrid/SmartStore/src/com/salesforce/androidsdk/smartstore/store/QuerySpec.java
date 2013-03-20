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
package com.salesforce.androidsdk.smartstore.store;

import com.salesforce.androidsdk.smartstore.store.SmartStore.SmartStoreException;

/**
 * Simple class to represent a query spec
 */
public class QuerySpec {
	// Constants
	private static final String SELECT = "SELECT  ";
	private static final String FROM = "FROM ";
	private static final String WHERE = "WHERE ";
	private static final String ORDER_BY = "ORDER BY ";
	
	// Key members
	public final QueryType queryType;
    public final int pageSize;
    public final String smartSql;

    // Exact/Range/Like
	public final String soupName;
    public final String path;
    public final Order order;

    // Exact
    public final String matchKey;
    // Range
    public final String beginKey;
    public final String endKey;
    // Like
    public final String likeKey;

    // Private constructor for soup query spec
    private QuerySpec(String soupName, String path, QueryType queryType, String matchKey, String beginKey, String endKey, String likeKey, Order order, int pageSize) {
    	this.soupName = soupName;
        this.path = path;
        this.queryType = queryType;
        this.matchKey = matchKey;
        this.beginKey = beginKey;
        this.endKey = endKey;
        this.likeKey = likeKey;
        this.order = order;
        this.pageSize = pageSize;
        this.smartSql = computeSmartSql();
    }

    // Private constructor for smart query spec
    private QuerySpec(String smartSql, int pageSize) {
    	this.smartSql = smartSql;
    	this.pageSize = pageSize;
        this.queryType = QueryType.smart;
    	
    	// Not applicable
        this.soupName = null;
        this.path = null;
        this.matchKey = null;
        this.beginKey = null;
        this.endKey = null;
        this.likeKey = null;
        this.order = null;    	
    }

    /**
     * Return q auery spec for an all query
     * @param soupName
     * @param path
     * @param order
     * @param pageSize
     * @return
     */
    public static QuerySpec buildAllQuerySpec(String soupName, String path, Order order, int pageSize) {
    	return buildRangeQuerySpec(soupName, path, null, null, order, pageSize);
    }
    
    /**
     * Return a query spec for an exact match query
     * @param soupName
     * @param path
     * @param exactMatchKey
     * @param pageSize
     * @return
     */
    public static QuerySpec buildExactQuerySpec(String soupName, String path, String exactMatchKey, int pageSize) {
        return new QuerySpec(soupName, path, QueryType.exact, exactMatchKey, null, null, null, Order.ascending /* meaningless - all rows will have the same value in the indexed column*/, pageSize);
    }

    /**
     * Return a query spec for a range query
     * @param soupName
     * @param path
     * @param beginKey
     * @param endKey
     * @param order
     * @param pageSize
     * @return
     */
    public static QuerySpec buildRangeQuerySpec(String soupName, String path, String beginKey, String endKey, Order order, int pageSize) {
        return new QuerySpec(soupName, path, QueryType.range, null, beginKey, endKey, null, order, pageSize);
    }

    /**
     * Return a query spec for a like query
     * @param soupName
     * @param path
     * @param matchKey
     * @param order
     * @param pageSize
     * @return
     */
    public static QuerySpec buildLikeQuerySpec(String soupName, String path, String likeKey, Order order, int pageSize) {
        return new QuerySpec(soupName, path, QueryType.like, null, null, null, likeKey, order, pageSize);
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
     * @return smartSql for exact/like/range queries
     */
    private String computeSmartSql() {
    	return computeSelectClause() + computeFromClause() + computeWhereClause() + computeOrderClause();
    }

    /**
     * @return select clause for exact/like/range queries
     */
    private String computeSelectClause() {
    	return SELECT  + computeFieldReference(SmartSqlHelper.SOUP) + " ";
    }

    /**
     * @return from clause for exact/like/range queries
     */
    private String computeFromClause() {
    	return FROM  + computeSoupReference() + " ";
    }
    
    /**
     * @return where clause for exact/like/range queries
     */
    private String computeWhereClause() {
    	if (path == null) return "";
    	
    	String field = computeFieldReference(path);
    	String pred = "";
        switch(queryType) {
        case exact: pred =  field + " = ? "; break;
        case like: pred = field + " LIKE ? "; break;
        case range:
            if (beginKey == null && endKey == null) { break; }
            if (endKey == null) { pred = field + " >= ? "; break; }
            if (beginKey == null) { pred = field + " <= ? "; break; }
            else { pred = field + " >= ?  AND " + field + " <= ? "; break; }
        default:
            throw new SmartStoreException("Fell through switch: " + queryType);
        }
        return (pred.equals("") ? "" : WHERE + pred);
    }

    /**
     * @return order clause for exact/like/range queries
     */
    private String computeOrderClause() {
    	if (path == null) return "";

    	return ORDER_BY + computeFieldReference(path) + " " + order.sql + " ";
    }
    
	/**
	 * @return soup reference for smart sql query
	 */
	private String computeSoupReference() {
		return "{" + soupName + "}";
	}

    
    /**
     * @param field
	 * @return field reference for smart sql query
	 */
	private String computeFieldReference(String field) {
		return "{" + soupName + ":" + field + "}";
	}

    /**
     * @return args going with the sql predicate returned by getKeyPredicate
     */
    public String[] getArgs() {
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
        case smart:
        	return null;
        default:
            throw new SmartStoreException("Fell through switch: " + queryType);
        }
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
     * Simple class to represent query order
     */
    public enum Order {
        ascending("ASC"), descending("DESC");

        public final String sql;

        Order(String sqlOrder) {
            this.sql = sqlOrder;
        }
    }

}