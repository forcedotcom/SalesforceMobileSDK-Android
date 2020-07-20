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
package com.salesforce.androidsdk.mobilesync.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the layout of a Salesforce object.
 *
 * @author bhariharan
 * @see <a href="https://developer.salesforce.com/docs/atlas.en-us.uiapi.meta/uiapi/ui_api_responses_record_layout.htm">https://developer.salesforce.com/docs/atlas.en-us.uiapi.meta/uiapi/ui_api_responses_record_layout.htm</a>
 */
public class Layout {

    private static final String ID = "id";
    private static final String LAYOUT_TYPE = "layoutType";
    private static final String MODE = "mode";
    private static final String SECTIONS = "sections";

    private String id;
    private String layoutType;
    private String mode;
    private List<LayoutSection> sections;
    private JSONObject rawData;

    /**
     * Creates an instance of this class from its JSON representation.
     *
     * @param object JSON object.
     * @return Instance of this class.
     */
    public static Layout fromJSON(JSONObject object) {
        Layout layout = null;
        if (object != null) {
            layout = new Layout();
            layout.rawData = object;
            layout.id = object.optString(ID);
            layout.layoutType = object.optString(LAYOUT_TYPE);
            layout.mode = object.optString(MODE);
            final JSONArray sections = object.optJSONArray(SECTIONS);
            if (sections != null) {
                layout.sections = new ArrayList<>();
                for (int i = 0; i < sections.length(); i++) {
                    final JSONObject section = sections.optJSONObject(i);
                    if (section != null) {
                        layout.sections.add(LayoutSection.fromJSON(section));
                    }
                }
            }
        }
        return layout;
    }

    /**
     * Returns the ID of this layout.
     *
     * @return ID of this layout.
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the layout type of this layout.
     *
     * @return Layout type of this layout.
     */
    public String getLayoutType() {
        return layoutType;
    }

    /**
     * Returns the mode of this layout.
     *
     * @return Mode of this layout.
     */
    public String getMode() {
        return mode;
    }

    /**
     * Returns the layout sections of this layout.
     *
     * @return Layout sections of this layout.
     */
    public List<LayoutSection> getSections() {
        return sections;
    }

    /**
     * Returns the raw data of this layout.
     *
     * @return Raw data of this layout.
     */
    public JSONObject getRawData() {
        return rawData;
    }

    /**
     * Represents a record layout section.
     *
     * @author bhariharan
     * @see <a href="https://developer.salesforce.com/docs/atlas.en-us.uiapi.meta/uiapi/ui_api_responses_record_layout_section.htm#ui_api_responses_record_layout_section">https://developer.salesforce.com/docs/atlas.en-us.uiapi.meta/uiapi/ui_api_responses_record_layout_section.htm#ui_api_responses_record_layout_section</a>
     */
    public static class LayoutSection {

        private static final String COLLAPSIBLE = "collapsible";
        private static final String COLUMNS = "columns";
        private static final String HEADING = "heading";
        private static final String LAYOUT_ROWS = "layoutRows";
        private static final String ROWS = "rows";
        private static final String USE_HEADING = "useHeading";

        private boolean collapsible;
        private int columns;
        private String heading;
        private String id;
        private List<Row> layoutRows;
        private int rows;
        private boolean useHeading;

        /**
         * Creates an instance of this class from its JSON representation.
         *
         * @param object JSON object.
         * @return Instance of this class.
         */
        public static LayoutSection fromJSON(JSONObject object) {
            LayoutSection section = null;
            if (object != null) {
                section = new LayoutSection();
                section.collapsible = object.optBoolean(COLLAPSIBLE);
                section.columns = object.optInt(COLUMNS);
                section.heading = object.optString(HEADING);
                section.id = object.optString(ID);
                final JSONArray rows = object.optJSONArray(LAYOUT_ROWS);
                if (rows != null) {
                    section.layoutRows = new ArrayList<>();
                    for (int i = 0; i< rows.length(); i++) {
                        final JSONObject row = rows.optJSONObject(i);
                        if (row != null) {
                            section.layoutRows.add(Row.fromJSON(row));
                        }
                    }
                }
                section.rows = object.optInt(ROWS);
                section.useHeading = object.optBoolean(USE_HEADING);
            }
            return section;
        }

        /**
         * Returns whether this layout section is collapsible or not.
         *
         * @return True - if collapsible, False - otherwise.
         */
        public boolean isCollapsible() {
            return collapsible;
        }

        /**
         * Returns the number of columns in this layout section.
         *
         * @return Number of columns in this layout section.
         */
        public int getColumns() {
            return columns;
        }

        /**
         * Returns the heading of this layout section.
         *
         * @return Heading of this layout section.
         */
        public String getHeading() {
            return heading;
        }

        /**
         * Returns the ID of this layout section.
         *
         * @return ID of this layout section.
         */
        public String getId() {
            return id;
        }

        /**
         * Returns the rows present in this layout section.
         *
         * @return Rows present in this layout section.
         */
        public List<Row> getLayoutRows() {
            return layoutRows;
        }

        /**
         * Returns the number of rows in this layout section.
         *
         * @return Number of rows in this layout section.
         */
        public int getRows() {
            return rows;
        }

        /**
         * Returns whether this layout section uses a heading or not.
         *
         * @return True - if it uses a heading, False - otherwise.
         */
        public boolean usesHeading() {
            return useHeading;
        }

        /**
         * Represents a record layout row.
         *
         * @author bhariharan
         * @see <a href="https://developer.salesforce.com/docs/atlas.en-us.uiapi.meta/uiapi/ui_api_responses_record_layout_row.htm#ui_api_responses_record_layout_row">https://developer.salesforce.com/docs/atlas.en-us.uiapi.meta/uiapi/ui_api_responses_record_layout_row.htm#ui_api_responses_record_layout_row</a>
         */
        public static class Row {

            private static final String LAYOUT_ITEMS = "layoutItems";

            private List<Item> layoutItems;

            /**
             * Creates an instance of this class from its JSON representation.
             *
             * @param object JSON object.
             * @return Instance of this class.
             */
            public static Row fromJSON(JSONObject object) {
                Row row = null;
                if (object != null) {
                    row = new Row();
                    final JSONArray items = object.optJSONArray(LAYOUT_ITEMS);
                    if (items != null) {
                        row.layoutItems = new ArrayList<>();
                        for (int i = 0; i< items.length(); i++) {
                            final JSONObject item = items.optJSONObject(i);
                            if (item != null) {
                                row.layoutItems.add(Item.fromJSON(item));
                            }
                        }
                    }
                }
                return row;
            }

            /**
             * Returns the layout items present in this layout row.
             *
             * @return Layout items present in this layout row.
             */
            public List<Item> getLayoutItems() {
                return layoutItems;
            }

            /**
             * Represents a record layout item.
             *
             * @author bhariharan
             * @see <a href="https://developer.salesforce.com/docs/atlas.en-us.uiapi.meta/uiapi/ui_api_responses_record_layout_item.htm#ui_api_responses_record_layout_item">https://developer.salesforce.com/docs/atlas.en-us.uiapi.meta/uiapi/ui_api_responses_record_layout_item.htm#ui_api_responses_record_layout_item</a>
             */
            public static class Item {

                private static final String EDITABLE_FOR_NEW = "editableForNew";
                private static final String EDITABLE_FOR_UPDATE = "editableForUpdate";
                private static final String LABEL = "label";
                private static final String LAYOUT_COMPONENTS = "layoutComponents";
                private static final String LOOKUP_ID_API_NAME = "lookupIdApiName";
                private static final String REQUIRED = "required";
                private static final String SORTABLE = "sortable";

                private boolean editableForNew;
                private boolean editableForUpdate;
                private String label;
                private JSONArray layoutComponents;
                private String lookupIdApiName;
                private boolean required;
                private boolean sortable;

                /**
                 * Creates an instance of this class from its JSON representation.
                 *
                 * @param object JSON object.
                 * @return Instance of this class.
                 */
                public static Item fromJSON(JSONObject object) {
                    Item item = null;
                    if (object != null) {
                        item = new Item();
                        item.editableForNew = object.optBoolean(EDITABLE_FOR_NEW);
                        item.editableForUpdate = object.optBoolean(EDITABLE_FOR_UPDATE);
                        item.label = object.optString(LABEL);
                        item.layoutComponents = object.optJSONArray(LAYOUT_COMPONENTS);
                        item.lookupIdApiName = object.optString(LOOKUP_ID_API_NAME);
                        item.required = object.optBoolean(REQUIRED);
                        item.sortable = object.optBoolean(SORTABLE);
                    }
                    return item;
                }

                /**
                 * Returns whether this item is editable for new.
                 *
                 * @return True - if editable, False - otherwise.
                 */
                public boolean isEditableForNew() {
                    return editableForNew;
                }

                /**
                 * Returns whether this item is editable for update.
                 *
                 * @return True - if editable, False - otherwise.
                 */
                public boolean isEditableForUpdate() {
                    return editableForUpdate;
                }

                /**
                 * Returns the label associated with this row.
                 *
                 * @return Label associated with this row.
                 */
                public String getLabel() {
                    return label;
                }

                /**
                 * Returns the layout components associated with this row.
                 *
                 * @return Layout components associated with this row.
                 */
                public JSONArray getLayoutComponents() {
                    return layoutComponents;
                }

                /**
                 * Returns the lookup ID API name associated with this row.
                 *
                 * @return Lookup ID API name associated with this row.
                 */
                public String getLookupIdApiName() {
                    return lookupIdApiName;
                }

                /**
                 * Returns whether this item is required.
                 *
                 * @return True - if required, False - otherwise.
                 */
                public boolean isRequired() {
                    return required;
                }

                /**
                 * Returns whether this item is sortable.
                 *
                 * @return True - if sortable, False - otherwise.
                 */
                public boolean isSortable() {
                    return sortable;
                }
            }
        }
    }
}
