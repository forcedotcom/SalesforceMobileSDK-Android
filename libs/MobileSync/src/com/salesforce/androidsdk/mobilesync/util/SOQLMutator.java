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
package com.salesforce.androidsdk.mobilesync.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private static final String ORDER_BY = "order by";
    private static final String GROUP_BY = "group by";
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
        String matchingClauseType = null;
        String currentClauseType = null;    // one of the clause types of interest
        SOQLTokenizer tokenizer = new SOQLTokenizer(this.originalSoql);

        for (String token : tokenizer.tokenize()) {
            for (String clauseType : CLAUSE_TYPE_KEYWORDS) {
                if (token.toLowerCase(Locale.US).matches(clauseType)) {
                    matchingClauseType = clauseType;
                    break;
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
                    if (!token.startsWith("(")) {
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

    /**
     * Simple SOQL tokenizer
     * Tokens returned are either:
     *  - SOQL keyworkds (select, from, where, having, group by, order by, limit, offset)
     *  - top level parenthesized expression
     *  - top level single quoted expression
     *  - strings without white spaces
     *  - white spaces
     */
    public static class SOQLTokenizer {

        private String soql;

        public SOQLTokenizer(String soql) {
            this.soql = soql;
        }

        // Used during tokenization
        private List<String> tokens = new ArrayList<>();
        private boolean inWhiteSpace = false;
        private boolean inQuotes = false;
        private int depth = 0;
        private char lastCh = 0;
        private StringBuilder currentToken = new StringBuilder();

        private void pushToken() {
            tokens.add(currentToken.toString());
            currentToken = new StringBuilder();
        }

        private void beginWhiteSpace() {
            if (depth == 0) {
                pushToken();
            }
            inWhiteSpace = true;
            currentToken.append(' ');
        }

        private void beginWord(char ch) {
            if (depth == 0) {
                pushToken();
            }
            inWhiteSpace = false;
            currentToken.append(ch);
        }

        private void beginParenthesized() {
            if (depth == 0) {
                pushToken();
            }
            inWhiteSpace = false;
            depth++;
            currentToken.append('(');
        }

        private void endParenthesized() {
            currentToken.append(')');
            depth--;
            if (depth == 0) {
                pushToken();
            }
        }

        private void beginQuoted() {
            if (depth == 0) {
                pushToken();
            }
            inQuotes = true;
            inWhiteSpace = false;
            currentToken.append('\'');
        }

        private void endQuoted() {
            currentToken.append('\'');
            if (depth == 0) {
                pushToken();
            }
            inQuotes = false;
        }

        // Combining order by, group by into single token
        private List<String> processTokens() {
            List<String> processedTokens = new ArrayList<>();
            for (int i=0; i<tokens.size(); i++) {
                String token = tokens.get(i);
                if (i+2 < tokens.size()) {
                    String nextToken = tokens.get(i+1);
                    String afterNextToken = tokens.get(i+2);
                    if (nextToken.trim().isEmpty() && afterNextToken.equalsIgnoreCase("by") && (token.equalsIgnoreCase("order") || token.equalsIgnoreCase("group"))) {
                        processedTokens.add(token + " " + afterNextToken);
                        i += 2;
                        continue;
                    }
                }
                processedTokens.add(token);
            }

            return processedTokens;
        }

        public List<String> tokenize() {
            char[] chars = this.soql.toCharArray();
            for (char ch : chars) {
                switch (ch) {
                    case '\'':
                        if (!inQuotes) { // starting '' expression
                            beginQuoted();
                        }
                        else if (lastCh != '\\') { // ending '' expression
                            endQuoted();
                        }
                        else { // within '' expression but escaped
                            currentToken.append(ch);
                        }
                        break;

                    case '(':
                        if (!inQuotes) { // starting () expressions
                            beginParenthesized();
                        }
                        else { // within '' expression
                            currentToken.append(ch);
                        }
                        break;

                    case ')':
                        if (!inQuotes) { // starting () expressions
                            endParenthesized();
                        }
                        else { // within '' expression
                            currentToken.append(ch);
                        }
                        break;

                    case ' ':
                        if (!inWhiteSpace && !inQuotes && depth == 0) { // starting top level white space
                            beginWhiteSpace();
                        }
                        else {
                            currentToken.append(ch);
                        }
                        break;

                    default:
                        if (inWhiteSpace) {
                            beginWord(ch);
                        }
                        else {
                            currentToken.append(ch);
                        }
                }
                lastCh = ch;
            }
            // Don't forget last token
            if (currentToken.length() > 0) {
                tokens.add(currentToken.toString());
            }

            // Process tokens
            return processTokens();
        }

    }



}