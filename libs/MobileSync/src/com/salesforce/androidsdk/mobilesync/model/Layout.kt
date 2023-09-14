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
    fun getSections(): List<LayoutSection?>? {
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
        private var layoutRows: MutableList<Row?>? = null

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
        fun getLayoutRows(): List<Row?>? {
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
                    fun fromJSON(`object`: JSONObject?): Item? {
                        var item: Item? = null
                        if (`object` != null) {
                            item = Item()
                            item.isEditableForNew = `object`.optBoolean(EDITABLE_FOR_NEW)
                            item.isEditableForUpdate = `object`.optBoolean(EDITABLE_FOR_UPDATE)
                            item.label = `object`.optString(LABEL)
                            item.layoutComponents = `object`.optJSONArray(LAYOUT_COMPONENTS)
                            item.lookupIdApiName = `object`.optString(LOOKUP_ID_API_NAME)
                            item.isRequired = `object`.optBoolean(REQUIRED)
                            item.isSortable = `object`.optBoolean(SORTABLE)
                        }
                        return item
                    }
                }
            }

            companion object {
                private const val LAYOUT_ITEMS = "layoutItems"

                /**
                 * Creates an instance of this class from its JSON representation.
                 *
                 * @param object JSON object.
                 * @return Instance of this class.
                 */
                fun fromJSON(`object`: JSONObject?): Row? {
                    var row: Row? = null
                    if (`object` != null) {
                        row = Row()
                        val items = `object`.optJSONArray(LAYOUT_ITEMS)
                        if (items != null) {
                            row.layoutItems = ArrayList()
                            for (i in 0 until items.length()) {
                                val item = items.optJSONObject(i)
                                if (item != null) {
                                    row.layoutItems.add(Item.fromJSON(item))
                                }
                            }
                        }
                    }
                    return row
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
             * @param object JSON object.
             * @return Instance of this class.
             */
            fun fromJSON(`object`: JSONObject?): LayoutSection? {
                var section: LayoutSection? = null
                if (`object` != null) {
                    section = LayoutSection()
                    section.isCollapsible = `object`.optBoolean(COLLAPSIBLE)
                    section.columns = `object`.optInt(COLUMNS)
                    section.heading = `object`.optString(HEADING)
                    section.id = `object`.optString(ID)
                    val rows = `object`.optJSONArray(LAYOUT_ROWS)
                    if (rows != null) {
                        section.layoutRows = ArrayList()
                        for (i in 0 until rows.length()) {
                            val row = rows.optJSONObject(i)
                            if (row != null) {
                                section.layoutRows.add(Row.fromJSON(row))
                            }
                        }
                    }
                    section.rows = `object`.optInt(ROWS)
                    section.useHeading = `object`.optBoolean(USE_HEADING)
                }
                return section
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
         * @param object JSON object.
         * @return Instance of this class.
         */
        fun fromJSON(`object`: JSONObject?): Layout? {
            var layout: Layout? = null
            if (`object` != null) {
                layout = Layout()
                layout.rawData = `object`
                layout.id = `object`.optString(ID)
                layout.layoutType = `object`.optString(LAYOUT_TYPE)
                layout.mode = `object`.optString(MODE)
                val sections = `object`.optJSONArray(SECTIONS)
                if (sections != null) {
                    layout.sections = ArrayList()
                    for (i in 0 until sections.length()) {
                        val section = sections.optJSONObject(i)
                        if (section != null) {
                            layout.sections.add(LayoutSection.fromJSON(section))
                        }
                    }
                }
            }
            return layout
        }
    }
}