package com.shenxunchat.sonant.preference;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.DialogPreference;

import com.shenxunchat.sonant.R;

public class KeySelectDialogPreference extends DialogPreference {
    public KeySelectDialogPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        setDialogLayoutResource(R.layout.dialog_keyselect_preference);
    }
}
