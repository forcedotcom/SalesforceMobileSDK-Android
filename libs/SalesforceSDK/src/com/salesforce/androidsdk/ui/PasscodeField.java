package com.salesforce.androidsdk.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;

import com.salesforce.androidsdk.R;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.security.PasscodeManager;
import com.salesforce.androidsdk.ui.PasscodeActivity.PasscodeMode;

import androidx.appcompat.widget.AppCompatEditText;

public class PasscodeField extends AppCompatEditText {
    private static final int MAX_PASSCODE_LENGTH = 8;
    private static final int CIRCLE_DIAMETER = 24;
    private static final int LINE_WIDTH = 2;
    private static final int DEFAULT_PADDING = 20;
    private static final int CIRCLE_SPACING = 20;

    private PasscodeMode currentMode = PasscodeMode.Check;

    public PasscodeField(Context context) {
        super(context);
        disableActions();
    }

    public PasscodeField(Context context, AttributeSet attrs) {
        super(context, attrs);
        disableActions();
    }

    public PasscodeField(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        disableActions();
    }

    /*
     * This is necessary to make sure the user is always typing on the end of the passcode string.
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

    @Override
    public boolean isSuggestionsEnabled() {
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        PasscodeManager passcodeManager = SalesforceSDKManager.getInstance().getPasscodeManager();
        boolean passcodeLengthKnown = passcodeManager.getPasscodeLengthKnown();
        int passcodeLength = passcodeManager.getMinPasscodeLength();

        float density = getResources().getDisplayMetrics().density;

        float diameter = CIRCLE_DIAMETER * density;
        float lineWidth = LINE_WIDTH * density;
        float padding = DEFAULT_PADDING * density;
        float spacing = CIRCLE_SPACING * density;

        Paint openCirclePaint = new Paint();
        Paint typedCirclePaint = new Paint();

        openCirclePaint.setColor(getResources().getColor(R.color.sf__passcode_primary_color));
        openCirclePaint.setStyle(Paint.Style.STROKE);
        openCirclePaint.setStrokeWidth(lineWidth);
        typedCirclePaint.setColor(getResources().getColor(R.color.sf__passcode_primary_color));

        int circleSpacing = 0;
        int lengthForSpacing = passcodeLengthKnown ? passcodeLength : MAX_PASSCODE_LENGTH;
        float yPosition = getHeight()/2;
        float startX = (getWidth() - (diameter * lengthForSpacing) - (padding * (lengthForSpacing - 1)) + (lineWidth * lengthForSpacing * 2)) / 2;

        if (!currentMode.equals(PasscodeMode.Check) || passcodeLengthKnown) {
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

    public void setPasscodeMode(PasscodeMode mode) {
        currentMode = mode;
    }

    private void disableActions() {
        /*
         * TODO: Remove this check once minAPI >= 23.
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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
}
