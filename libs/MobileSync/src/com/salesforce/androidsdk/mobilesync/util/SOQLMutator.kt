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
package com.salesforce.androidsdk.mobilesync.util

/**
 * To manipulate a SOQL query given by a String
 *
 * Better than doing regexp maybe it's time to start using a proper parser (e.g. https://github.com/mulesoft/salesforce-soql-parser)
 *
 */
class SOQLMutator(private val originalSoql: String) {
    private val clauses: MutableMap<String, String> = HashMap()
    private val clausesWithoutSubqueries: MutableMap<String, String> = HashMap()

    init {
        parseQuery()
    }

    private fun parseQuery() {
        var matchingClauseType: String? = null
        var currentClauseType: String? = null // one of the clause types of interest
        val tokenizer = SOQLTokenizer(originalSoql)
        for (token in tokenizer.tokenize()) {
            for (clauseType in CLAUSE_TYPE_KEYWORDS) {
                if (token.lowercase().matches(Regex(clauseType))) {
                    matchingClauseType = clauseType
                    break
                }
            }
            if (matchingClauseType != null) {
                // We just matched one of the CLAUSE_TYPE_KEYWORDS in the top level query
                currentClauseType = matchingClauseType
                clauses[currentClauseType] = ""
                clausesWithoutSubqueries[currentClauseType] = ""
                matchingClauseType = null
            } else {
                // We are inside a clause
                if (currentClauseType != null) {
                    clauses[currentClauseType] = clauses[currentClauseType] + token
                    // We are inside a clause and not in a subquery
                    if (!token.startsWith("(")) {
                        clausesWithoutSubqueries[currentClauseType] =
                            clausesWithoutSubqueries[currentClauseType] + token
                    }
                }
            }
        }
    }

    /**
     * Replace fields being selected
     * @param commaSeparatedFields Comma separated fields to use in top level query's select.
     */
    fun replaceSelectFields(commaSeparatedFields: String): SOQLMutator {
        clauses[SELECT] = commaSeparatedFields
        return this
    }

    /**
     * Add fields to select
     * @param commaSeparatedFields Comma separated fields to add to top level query's select.
     */
    fun addSelectFields(commaSeparatedFields: String): SOQLMutator {
        clauses[SELECT] = "$commaSeparatedFields,${trimmedClause(SELECT)}"
        return this
    }

    /**
     * Add predicates to where clause
     * @param commaSeparatedPredicates Comma separated predicates to add to top level query's where.
     */
    fun addWherePredicates(commaSeparatedPredicates: String): SOQLMutator {
        if (clauses.containsKey(WHERE)) {
            clauses[WHERE] = commaSeparatedPredicates + " and " + trimmedClause(WHERE)
        } else {
            clauses[WHERE] = commaSeparatedPredicates
        }
        return this
    }

    /**
     * Replace order by clause (or add one if none)
     * @param commaSeparatedFields Comma separated fields to add to top level query's select.
     */
    fun replaceOrderBy(commaSeparatedFields: String): SOQLMutator {
        clauses[ORDER_BY] = commaSeparatedFields
        return this
    }

    /**
     * Check if query is ordering by given fields
     * @param commaSeparatedFields Comma separated fields to look for.
     * @return true if it is the case.
     */
    fun isOrderingBy(commaSeparatedFields: String): Boolean {
        return clauses.containsKey(ORDER_BY)
                && equalsIgnoringWhiteSpaces(commaSeparatedFields, clauses[ORDER_BY] ?: "")
    }

    /**
     * Check if query has order by clause
     * @return true if it is the case.
     */
    fun hasOrderBy(): Boolean {
        return clauses.containsKey(ORDER_BY)
    }

    /**
     * Check if query is selecting by given field
     * @param field Field to look for.
     * @return true if it is the case.
     */
    fun isSelectingField(field: String?): Boolean {
        val selectClause = clausesWithoutSubqueries[SELECT] ?: return false
        val selectedFields = selectClause
            .split("[, ]+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        return selectedFields.contains(field)
    }

    /**
     * @return a SOQL builder with mutations applied
     */
    fun asBuilder(): SOQLBuilder {
        return SOQLBuilder.getInstanceWithFields(trimmedClause(SELECT))
            .from(trimmedClause(FROM))
            .where(trimmedClause(WHERE))
            .having(trimmedClause(HAVING))
            .groupBy(trimmedClause(GROUP_BY))
            .orderBy(trimmedClause(ORDER_BY))
            .limit(clauseAsInteger(LIMIT))
            .offset(clauseAsInteger(OFFSET))
    }

    // Helper methods
    private fun equalsIgnoringWhiteSpaces(s1: String, s2: String): Boolean {
        return removeWhiteSpaces(s1) == removeWhiteSpaces(s2)
    }

    private fun removeWhiteSpaces(s: String): String {
        return s.replace("[ ]*".toRegex(), "")
    }

    private fun trimmedClause(clauseType: String): String {
        return clauses[clauseType]?.trim() ?: ""
    }

    private fun clauseAsInteger(clauseType: String): Int? {
        return clauses[clauseType]?.trim()?.toIntOrNull()
    }

    /**
     * Simple SOQL tokenizer
     * Tokens returned are either:
     * - SOQL keyworkds (select, from, where, having, group by, order by, limit, offset)
     * - top level parenthesized expression
     * - top level single quoted expression
     * - strings without white spaces
     * - white spaces
     */
    class SOQLTokenizer(private val soql: String) {
        // Used during tokenization
        private val tokens: MutableList<String> = ArrayList()
        private var inWhiteSpace = false
        private var inQuotes = false
        private var depth = 0
        private var lastCh = 0.toChar()
        private var currentToken = StringBuilder()
        private fun pushToken() {
            tokens.add(currentToken.toString())
            currentToken = StringBuilder()
        }

        private fun beginWhiteSpace() {
            if (depth == 0) {
                pushToken()
            }
            inWhiteSpace = true
            currentToken.append(' ')
        }

        private fun beginWord(ch: Char) {
            if (depth == 0) {
                pushToken()
            }
            inWhiteSpace = false
            currentToken.append(ch)
        }

        private fun beginParenthesized() {
            if (depth == 0) {
                pushToken()
            }
            inWhiteSpace = false
            depth++
            currentToken.append('(')
        }

        private fun endParenthesized() {
            currentToken.append(')')
            depth--
            if (depth == 0) {
                pushToken()
            }
        }

        private fun beginQuoted() {
            if (depth == 0) {
                pushToken()
            }
            inQuotes = true
            inWhiteSpace = false
            currentToken.append('\'')
        }

        private fun endQuoted() {
            currentToken.append('\'')
            if (depth == 0) {
                pushToken()
            }
            inQuotes = false
        }

        // Combining order by, group by into single token
        private fun processTokens(): List<String> {
            val processedTokens: MutableList<String> = ArrayList()
            var i = 0
            while (i < tokens.size) {
                val token = tokens[i]
                if (i + 2 < tokens.size) {
                    val nextToken = tokens[i + 1]
                    val afterNextToken = tokens[i + 2]
                    if (nextToken.trim { it <= ' ' }.isEmpty() && afterNextToken.equals(
                            "by",
                            ignoreCase = true
                        ) && (token.equals("order", ignoreCase = true) || token.equals(
                            "group",
                            ignoreCase = true
                        ))
                    ) {
                        processedTokens.add("$token $afterNextToken")
                        i += 2
                        i++
                        continue
                    }
                }
                processedTokens.add(token)
                i++
            }
            return processedTokens
        }

        fun tokenize(): List<String> {
            val chars = soql.toCharArray()
            for (ch in chars) {
                when (ch) {
                    '\'' -> if (!inQuotes) { // starting '' expression
                        beginQuoted()
                    } else if (lastCh != '\\') { // ending '' expression
                        endQuoted()
                    } else { // within '' expression but escaped
                        currentToken.append(ch)
                    }

                    '(' -> if (!inQuotes) { // starting () expressions
                        beginParenthesized()
                    } else { // within '' expression
                        currentToken.append(ch)
                    }

                    ')' -> if (!inQuotes) { // starting () expressions
                        endParenthesized()
                    } else { // within '' expression
                        currentToken.append(ch)
                    }

                    ' ' -> if (!inWhiteSpace && !inQuotes && depth == 0) { // starting top level white space
                        beginWhiteSpace()
                    } else {
                        currentToken.append(ch)
                    }

                    else -> if (inWhiteSpace) {
                        beginWord(ch)
                    } else {
                        currentToken.append(ch)
                    }
                }
                lastCh = ch
            }
            // Don't forget last token
            if (currentToken.isNotEmpty()) {
                tokens.add(currentToken.toString())
            }

            // Process tokens
            return processTokens()
        }
    }

    companion object {
        // Clause types of interest
        private const val SELECT = "select"
        private const val FROM = "from"
        private const val WHERE = "where"
        private const val HAVING = "having"
        private const val ORDER_BY = "order by"
        private const val GROUP_BY = "group by"
        private const val LIMIT = "limit"
        private const val OFFSET = "offset"
        private val CLAUSE_TYPE_KEYWORDS =
            arrayOf(SELECT, FROM, WHERE, HAVING, GROUP_BY, ORDER_BY, LIMIT, OFFSET)
    }
}