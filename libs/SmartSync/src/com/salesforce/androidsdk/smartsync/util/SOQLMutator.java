/*
 * Copyright (c) 2019-present, salesforce.com, inc.
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * To manipulate a SOQL query given by a String
 *
 * Better than doing regexp maybe it's time to start using a proper parser (e.g. https://github.com/mulesoft/salesforce-soql-parser)
 *
 */
public class SOQLMutator {

    // Clause types of interest
    private static final String SELECT = "select";
    private static final String FROM = "from";
    private static final String WHERE = "where";
    private static final String HAVING = "having";
    private static final String ORDER_BY = "order_by";
    private static final String GROUP_BY = "group_by";
    private static final String LIMIT = "limit";
    private static final String OFFSET = "offset";
    private static final String[] CLAUSE_TYPE_KEYWORDS = new String[] {SELECT, FROM, WHERE, HAVING, GROUP_BY, ORDER_BY, LIMIT, OFFSET};

    private String originalSoql;
    private Map<String, String> clauses = new HashMap<>();
    private Map<String, String> clausesWithoutSubqueries = new HashMap<>();


    /**
     * Initialize this SOQLMutator with the soql query to manipulate
     * @param soql
     */
    public SOQLMutator(String soql) {
        this.originalSoql = soql;
        parseQuery();
    }

    private void parseQuery() {
        // Dealing with two words keywords
        String preparedQuery = this.originalSoql
                .replaceAll("[ ]+[oO][rR][dD][eE][rR][ ]+[bB][yY][ ]+", " order_by ")
                .replaceAll("[ ]+[g][r][o][u][p][ ]+[bB][yY][ ]+", " group_by ");

        StringTokenizer tokenizer = new StringTokenizer(preparedQuery, " ", true);
        int depth = 0;
        String matchingClauseType = null;
        String currentClauseType = null;    // one of the clause types of interest
        while (tokenizer.hasMoreElements()) {
            String token = tokenizer.nextToken();


            if (token.startsWith("(")) {
                depth++;
            }
            // NB: same token could end with ")" .e.g "('abc','def')"
            if (token.endsWith(")")) {
                depth--;
            }

            // Only looking to parse top level query
            else if (depth == 0) {
                for (String clauseType : CLAUSE_TYPE_KEYWORDS) {
                    if (token.toLowerCase(Locale.US).matches(clauseType)) {
                        matchingClauseType = clauseType;
                        break;
                    }
                }
            }

            if (matchingClauseType != null) {
                // We just matched one of the CLAUSE_TYPE_KEYWORDS in the top level query
                currentClauseType = matchingClauseType;
                clauses.put(currentClauseType, "");
                clausesWithoutSubqueries.put(currentClauseType, "");
                matchingClauseType = null;
            } else {
                // We are inside a clause
                if (currentClauseType != null) {
                    clauses.put(currentClauseType, clauses.get(currentClauseType) + token);
                    // We are inside a clause and not in a subquery
                    if (depth == 0) {
                        clausesWithoutSubqueries.put(currentClauseType, clausesWithoutSubqueries.get(currentClauseType) + token);
                    }
                }
            }
        }
    }

    /**
     * Replace fields being selected
     * @param commaSeparatedFields Comma separated fields to use in top level query's select.
     */
    public SOQLMutator replaceSelectFields(String commaSeparatedFields) {
        clauses.put(SELECT, commaSeparatedFields);
        return this;
    }


    /**
     * Add fields to select
     * @param commaSeparatedFields Comma separated fields to add to top level query's select.
     */
    public SOQLMutator addSelectFields(String commaSeparatedFields) {
        clauses.put(SELECT, commaSeparatedFields + "," + trimmedClause(SELECT));
        return this;
    }

    /**
     * Add predicates to where clause
     * @param commaSeparatedPredicates Comma separated predicates to add to top level query's where.
     */
    public SOQLMutator addWherePredicates(String commaSeparatedPredicates) {
        if (clauses.containsKey(WHERE)) {
            clauses.put(WHERE, commaSeparatedPredicates + " and " + trimmedClause(WHERE));
        } else {
            clauses.put(WHERE, commaSeparatedPredicates);
        }
        return this;
    }

    /**
     * Replace order by clause (or add one if none)
     * @param commaSeparatedFields Comma separated fields to add to top level query's select.
     */
    public SOQLMutator replaceOrderBy(String commaSeparatedFields) {
        clauses.put(ORDER_BY, commaSeparatedFields);
        return this;
    }

    /**
     * Check if query is ordering by given fields
     * @param commaSeparatedFields Comma separated fields to look for.
     * @return true if it is the case.
     */
    public boolean isOrderingBy(String commaSeparatedFields) {
        return clauses.containsKey(ORDER_BY) && equalsIgnoringWhiteSpaces(commaSeparatedFields, clauses.get(ORDER_BY));
    }

    /**
     * Check if query has order by clause
     * @return true if it is the case.
     */
    public boolean hasOrderBy() {
        return clauses.containsKey(ORDER_BY);
    }


    /**
     * Check if query is selecting by given field
     * @param field Field to look for.
     * @return true if it is the case.
     */
    public boolean isSelectingField(String field) {
        List<String> selectedFields = Arrays.asList(clausesWithoutSubqueries.get(SELECT).split("[, ]+"));
        return selectedFields.contains(field);
    }

    /**
     * @return a SOQL builder with mutations applied
     */
    public SOQLBuilder asBuilder() {
        return SOQLBuilder.getInstanceWithFields(trimmedClause(SELECT))
                .from(trimmedClause(FROM))
                .where(trimmedClause(WHERE))
                .having(trimmedClause(HAVING))
                .groupBy(trimmedClause(GROUP_BY))
                .orderBy(trimmedClause(ORDER_BY))
                .limit(clauseAsInteger(LIMIT))
                .offset(clauseAsInteger(OFFSET));
    }

    // Helper methods
    private boolean equalsIgnoringWhiteSpaces(String s1, String s2) {
        return removeWhiteSpaces(s1).equals(removeWhiteSpaces(s2));
    }

    private String removeWhiteSpaces(String s) {
        return s.replaceAll("[ ]*",  "");
    }

    private String trimmedClause(String clauseType) {
        return clauses.containsKey(clauseType) ? clauses.get(clauseType).trim() : "";
    }

    private Integer clauseAsInteger(String clauseType) {
        return clauses.containsKey(clauseType) ? new Integer(clauses.get(clauseType).trim()) : null;
    }
}