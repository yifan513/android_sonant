package com.shenxunchat.sonant.app;

import android.app.Activity;

import androidx.annotation.NonNull;

public interface IStartupAction {
    void execute(@NonNull Activity activity);
}
