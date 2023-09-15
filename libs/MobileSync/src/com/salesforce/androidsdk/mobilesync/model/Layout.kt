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

import org.json.JSONArray
import org.json.JSONObject

/**
 * Represents the layout of a Salesforce object.
 *
 * @author bhariharan
 * @see [https://developer.salesforce.com/docs/atlas.en-us.uiapi.meta/uiapi/ui_api_responses_record_layout.htm](https://developer.salesforce.com/docs/atlas.en-us.uiapi.meta/uiapi/ui_api_responses_record_layout.htm)
 */
class Layout {
    /**
     * Returns the ID of this layout.
     *
     * @return ID of this layout.
     */
    var id: String? = null
        private set

    /**
     * Returns the layout type of this layout.
     *
     * @return Layout type of this layout.
     */
    var layoutType: String? = null
        private set

    /**
     * Returns the mode of this layout.
     *
     * @return Mode of this layout.
     */
    var mode: String? = null
        private set
    private var sections: MutableList<LayoutSection>? = null

    /**
     * Returns the raw data of this layout.
     *
     * @return Raw data of this layout.
     */
    var rawData: JSONObject? = null
        private set

    /**
     * Returns the layout sections of this layout.
     *
     * @return Layout sections of this layout.
     */
    fun getSections(): List<LayoutSection>? {
        return sections
    }

    /**
     * Represents a record layout section.
     *
     * @author bhariharan
     * @see [https://developer.salesforce.com/docs/atlas.en-us.uiapi.meta/uiapi/ui_api_responses_record_layout_section.htm.ui_api_responses_record_layout_section](https://developer.salesforce.com/docs/atlas.en-us.uiapi.meta/uiapi/ui_api_responses_record_layout_section.htm.ui_api_responses_record_layout_section)
     */
    class LayoutSection {
        /**
         * Returns whether this layout section is collapsible or not.
         *
         * @return True - if collapsible, False - otherwise.
         */
        var isCollapsible = false
            private set

        /**
         * Returns the number of columns in this layout section.
         *
         * @return Number of columns in this layout section.
         */
        var columns = 0
            private set

        /**
         * Returns the heading of this layout section.
         *
         * @return Heading of this layout section.
         */
        var heading: String? = null
            private set

        /**
         * Returns the ID of this layout section.
         *
         * @return ID of this layout section.
         */
        var id: String? = null
            private set
        private var layoutRows: MutableList<Row>? = null

        /**
         * Returns the number of rows in this layout section.
         *
         * @return Number of rows in this layout section.
         */
        var rows = 0
            private set
        private var useHeading = false

        /**
         * Returns the rows present in this layout section.
         *
         * @return Rows present in this layout section.
         */
        fun getLayoutRows(): List<Row>? {
            return layoutRows
        }

        /**
         * Returns whether this layout section uses a heading or not.
         *
         * @return True - if it uses a heading, False - otherwise.
         */
        fun usesHeading(): Boolean {
            return useHeading
        }

        /**
         * Represents a record layout row.
         *
         * @author bhariharan
         * @see [https://developer.salesforce.com/docs/atlas.en-us.uiapi.meta/uiapi/ui_api_responses_record_layout_row.htm.ui_api_responses_record_layout_row](https://developer.salesforce.com/docs/atlas.en-us.uiapi.meta/uiapi/ui_api_responses_record_layout_row.htm.ui_api_responses_record_layout_row)
         */
        class Row {
            private var layoutItems: MutableList<Item>? = null

            /**
             * Returns the layout items present in this layout row.
             *
             * @return Layout items present in this layout row.
             */
            fun getLayoutItems(): List<Item>? {
                return layoutItems
            }

            /**
             * Represents a record layout item.
             *
             * @author bhariharan
             * @see [https://developer.salesforce.com/docs/atlas.en-us.uiapi.meta/uiapi/ui_api_responses_record_layout_item.htm.ui_api_responses_record_layout_item](https://developer.salesforce.com/docs/atlas.en-us.uiapi.meta/uiapi/ui_api_responses_record_layout_item.htm.ui_api_responses_record_layout_item)
             */
            class Item {
                /**
                 * Returns whether this item is editable for new.
                 *
                 * @return True - if editable, False - otherwise.
                 */
                var isEditableForNew = false
                    private set

                /**
                 * Returns whether this item is editable for update.
                 *
                 * @return True - if editable, False - otherwise.
                 */
                var isEditableForUpdate = false
                    private set

                /**
                 * Returns the label associated with this row.
                 *
                 * @return Label associated with this row.
                 */
                var label: String? = null
                    private set

                /**
                 * Returns the layout components associated with this row.
                 *
                 * @return Layout components associated with this row.
                 */
                var layoutComponents: JSONArray? = null
                    private set

                /**
                 * Returns the lookup ID API name associated with this row.
                 *
                 * @return Lookup ID API name associated with this row.
                 */
                var lookupIdApiName: String? = null
                    private set

                /**
                 * Returns whether this item is required.
                 *
                 * @return True - if required, False - otherwise.
                 */
                var isRequired = false
                    private set

                /**
                 * Returns whether this item is sortable.
                 *
                 * @return True - if sortable, False - otherwise.
                 */
                var isSortable = false
                    private set

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
                     * @param object JSON object.
                     * @return Instance of this class.
                     */
                    fun fromJSON(obj: JSONObject): Item {
                        return with(Item()) {
                            isEditableForNew = obj.optBoolean(EDITABLE_FOR_NEW)
                            isEditableForUpdate = obj.optBoolean(EDITABLE_FOR_UPDATE)
                            label = obj.optString(LABEL)
                            layoutComponents = obj.optJSONArray(LAYOUT_COMPONENTS)
                            lookupIdApiName = obj.optString(LOOKUP_ID_API_NAME)
                            isRequired = obj.optBoolean(REQUIRED)
                            isSortable = obj.optBoolean(SORTABLE)
                            this
                        }
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
                    return with(Row()) {
                        val jsonItems = obj.optJSONArray(LAYOUT_ITEMS)
                        if (jsonItems != null) {
                            val layoutItems = ArrayList<Item>()
                            for (i in 0 until jsonItems.length()) {
                                layoutItems.add(Item.fromJSON(jsonItems.getJSONObject(i)))
                            }
                            this.layoutItems = layoutItems
                        }
                        this
                    }
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
                return with(LayoutSection()) {
                    isCollapsible = obj.optBoolean(COLLAPSIBLE)
                    columns = obj.optInt(COLUMNS)
                    heading = obj.optString(HEADING)
                    id = obj.optString(ID)
                    val jsonRows = obj.optJSONArray(LAYOUT_ROWS)
                    if (jsonRows != null) {
                        val layoutRows = ArrayList<Row>()
                        for (i in 0 until jsonRows.length()) {
                            layoutRows.add(Row.fromJSON(jsonRows.getJSONObject(i)))
                        }
                        this.layoutRows = layoutRows
                    }
                    rows = obj.optInt(ROWS)
                    useHeading = obj.optBoolean(USE_HEADING)
                    this
                }
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
            return with(Layout()) {
                rawData = obj
                id = obj.optString(ID)
                layoutType = obj.optString(LAYOUT_TYPE)
                mode = obj.optString(MODE)
                val jsonSections = obj.optJSONArray(SECTIONS)
                if (jsonSections != null) {
                    val sections = ArrayList<LayoutSection>()
                    for (i in 0 until jsonSections.length()) {
                        sections.add(LayoutSection.fromJSON(jsonSections.getJSONObject(i)))
                    }
                    this.sections = sections
                }
                this
            }
        }
    }
}