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
package com.salesforce.androidsdk.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

import com.salesforce.androidsdk.R;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.security.PasscodeManager;

@SuppressLint("AppCompatCustomView")
public class PasscodeField extends EditText {

    private static final int MAX_PASSCODE_LENGTH = 8;
    private static final int CIRCLE_DIAMETER = 22;
    private static final int LINE_WIDTH = 2;
    private static final int DEFAULT_PADDING = 20;
    private static final int CIRCLE_SPACING = 16;

    /**
     * {@inheritDoc}
     */
    public PasscodeField(Context context) {
        super(context);
        disableActions();
    }

    /**
     * {@inheritDoc}
     */
    public PasscodeField(Context context, AttributeSet attrs) {
        super(context, attrs);
        disableActions();
    }

    /**
     * {@inheritDoc}
     */
    public PasscodeField(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        disableActions();
    }

    /**
     * This override is necessary to ensure the user is always typing on the end of the passcode string.
     *
     * {@inheritDoc}
     */
    @Override
    public void onSelectionChanged(int start, int end) {
        CharSequence text = getText();
        if (text != null) {
            if (start != text.length() || end != text.length()) {
                setSelection(text.length(), text.length());
                return;
            }
        }

        super.onSelectionChanged(start, end);
    }

    /**
     * Override provided to disable suggestions.
     *
     * @return false
     */
    @Override
    public boolean isSuggestionsEnabled() {
        return false;
    }


    /**
     * Override provided to draw the passcode field UI element instead of the standard EditText
     *
     * @param canvas the provided canvas
     */
    @Override
    @SuppressLint("DrawAllocation") // For Paint
    protected void onDraw(Canvas canvas) {
        PasscodeManager passcodeManager = SalesforceSDKManager.getInstance().getPasscodeManager();
        boolean passcodeLengthKnown = passcodeManager.getPasscodeLengthKnown();
        int passcodeLength = passcodeManager.getPasscodeLength();

        float density = getResources().getDisplayMetrics().density;
        float diameter = CIRCLE_DIAMETER * density;
        float lineWidth = LINE_WIDTH * density;
        float padding = DEFAULT_PADDING * density;
        float spacing = CIRCLE_SPACING * density;

        Paint openCirclePaint = new Paint();
        Paint typedCirclePaint = new Paint();

        int circleColor = getResources().getColor(R.color.sf__primary_color);
        openCirclePaint.setColor(circleColor);
        openCirclePaint.setStyle(Paint.Style.STROKE);
        openCirclePaint.setStrokeWidth(lineWidth);
        typedCirclePaint.setColor(circleColor);

        int circleSpacing = 0;
        int lengthForSpacing = passcodeLengthKnown ? passcodeLength : MAX_PASSCODE_LENGTH;
        float yPosition = getHeight()/2f;
        float startX = (getWidth() - (diameter * lengthForSpacing) - (spacing * (lengthForSpacing - 1)) + (lineWidth * lengthForSpacing * 2)) / 2;

        // If passcode length is unknown (upgrade) don't draw open circles and left align
        if (passcodeLengthKnown) {
            for (int count = 0; count < passcodeLength; count++) {
                canvas.drawCircle(startX + circleSpacing, yPosition, diameter/2, openCirclePaint);
                circleSpacing += diameter + spacing;
            }
        } else {
            startX = (startX > padding) ? padding : startX;
        }

        circleSpacing = 0;
        for (int count = 0; count < getText().length(); count++) {
            canvas.drawCircle(startX + circleSpacing, yPosition, diameter/2, typedCirclePaint);
            circleSpacing += diameter + spacing;
        }
    }

    /**
     * Overrides the Custom Insert Action callbacks to disable all actions, such as select and paste.
     */
    private void disableActions() {
        this.setCustomInsertionActionModeCallback(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {

            }
        });
    }
}
