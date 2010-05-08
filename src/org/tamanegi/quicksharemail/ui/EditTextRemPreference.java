package org.tamanegi.quicksharemail.ui;

import android.content.Context;
import android.preference.EditTextPreference;
import android.util.AttributeSet;

public class EditTextRemPreference extends EditTextPreference
{

    public EditTextRemPreference(Context context)
    {
        super(context);
    }

    public EditTextRemPreference(Context context, AttributeSet attrs)
    {
        super(context, attrs);
    }

    public EditTextRemPreference(Context context, AttributeSet attrs,
            int defStyle)
    {
        super(context, attrs, defStyle);
    }

    public void performClick()
    {
        onClick();
    }

}
