/*
 * Copyright (c) 2018-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.mobilesync.model

import com.salesforce.androidsdk.util.JSONObjectHelper
import org.json.JSONArray
import org.json.JSONObject

/**
 * Represents the layout of a Salesforce object.
 * @see [https://developer.salesforce.com/docs/atlas.en-us.uiapi.meta/uiapi/ui_api_responses_record_layout.htm](https://developer.salesforce.com/docs/atlas.en-us.uiapi.meta/uiapi/ui_api_responses_record_layout.htm)
 */
data class Layout(
    val id: String,
    val layoutType: String,
    val mode: String,
    val sections: List<LayoutSection>,
    val rawData: JSONObject
) {

    /**
     * Represents a record layout section.
     *
     * @author bhariharan
     * @see [https://developer.salesforce.com/docs/atlas.en-us.uiapi.meta/uiapi/ui_api_responses_record_layout_section.htm.ui_api_responses_record_layout_section](https://developer.salesforce.com/docs/atlas.en-us.uiapi.meta/uiapi/ui_api_responses_record_layout_section.htm.ui_api_responses_record_layout_section)
     */
    data class LayoutSection(
        val isCollapsible: Boolean,
        val columns: Int,
        val heading: String,
        val id: String,
        val layoutRows: List<Row>,
        val rows: Int,
        val usesHeading: Boolean
    ) {
        /**
         * Represents a record layout row.
         * @see [https://developer.salesforce.com/docs/atlas.en-us.uiapi.meta/uiapi/ui_api_responses_record_layout_row.htm.ui_api_responses_record_layout_row](https://developer.salesforce.com/docs/atlas.en-us.uiapi.meta/uiapi/ui_api_responses_record_layout_row.htm.ui_api_responses_record_layout_row)
         */
        data class Row(
            val layoutItems: List<Item>
        ) {
            /**
             * Represents a record layout item.
             * @see [https://developer.salesforce.com/docs/atlas.en-us.uiapi.meta/uiapi/ui_api_responses_record_layout_item.htm.ui_api_responses_record_layout_item](https://developer.salesforce.com/docs/atlas.en-us.uiapi.meta/uiapi/ui_api_responses_record_layout_item.htm.ui_api_responses_record_layout_item)
             */
            data class Item(
                val isEditableForNew: Boolean,
                val isEditableForUpdate: Boolean,
                val label: String,
                val layoutComponents: JSONArray,
                val lookupIdApiName: String,
                val isRequired: Boolean,
                val isSortable: Boolean
            ) {
                companion object {
                    private const val EDITABLE_FOR_NEW = "editableForNew"
                    private const val EDITABLE_FOR_UPDATE = "editableForUpdate"
                    private const val LABEL = "label"
                    private const val LAYOUT_COMPONENTS = "layoutComponents"
                    private const val LOOKUP_ID_API_NAME = "lookupIdApiName"
                    private const val REQUIRED = "required"
                    private const val SORTABLE = "sortable"

                    /**
                     * Creates an instance of this class from its JSON representation.
                     *
                     * @param obj JSON object.
                     * @return Instance of this class.
                     */
                    fun fromJSON(obj: JSONObject): Item {
                        return Item(
                            isEditableForNew = obj.optBoolean(EDITABLE_FOR_NEW),
                            isEditableForUpdate = obj.optBoolean(EDITABLE_FOR_UPDATE),
                            label = obj.optString(LABEL),
                            layoutComponents = obj.optJSONArray(LAYOUT_COMPONENTS) ?: JSONArray(),
                            lookupIdApiName = obj.optString(LOOKUP_ID_API_NAME),
                            isRequired = obj.optBoolean(REQUIRED),
                            isSortable = obj.optBoolean(SORTABLE)
                        )
                    }
                }
            }

            companion object {
                private const val LAYOUT_ITEMS = "layoutItems"

                /**
                 * Creates an instance of this class from its JSON representation.
                 *
                 * @param obj JSON object.
                 * @return Instance of this class.
                 */
                fun fromJSON(obj: JSONObject): Row {
                    return Row(
                        JSONObjectHelper
                            .toList<JSONObject>(obj.optJSONArray(LAYOUT_ITEMS) ?: JSONArray())
                            .map { Item.fromJSON(it) }
                    )
                }
            }
        }

        companion object {
            private const val COLLAPSIBLE = "collapsible"
            private const val COLUMNS = "columns"
            private const val HEADING = "heading"
            private const val LAYOUT_ROWS = "layoutRows"
            private const val ROWS = "rows"
            private const val USE_HEADING = "useHeading"

            /**
             * Creates an instance of this class from its JSON representation.
             *
             * @param `object` JSON object.
             * @return Instance of this class.
             */
            fun fromJSON(obj: JSONObject): LayoutSection {
                return LayoutSection(
                    isCollapsible = obj.optBoolean(COLLAPSIBLE),
                    columns = obj.optInt(COLUMNS),
                    heading = obj.optString(HEADING),
                    id = obj.optString(ID),
                    layoutRows = JSONObjectHelper
                        .toList<JSONObject>(obj.optJSONArray(LAYOUT_ROWS) ?: JSONArray())
                        .map { Row.fromJSON(it) },
                    rows = obj.optInt(ROWS),
                    usesHeading = obj.optBoolean(USE_HEADING)
                )
            }
        }
    }

    companion object {
        private const val ID = "id"
        private const val LAYOUT_TYPE = "layoutType"
        private const val MODE = "mode"
        private const val SECTIONS = "sections"

        /**
         * Creates an instance of this class from its JSON representation.
         *
         * @param `object` JSON object.
         * @return Instance of this class.
         */
        fun fromJSON(obj: JSONObject): Layout {
            return Layout(
                id = obj.optString(ID),
                layoutType = obj.optString(LAYOUT_TYPE),
                mode = obj.optString(MODE),
                sections = JSONObjectHelper
                    .toList<JSONObject>(obj.optJSONArray(SECTIONS) ?: JSONArray())
                    .map { LayoutSection.fromJSON(it) },
                rawData = obj
            )
        }
    }
}