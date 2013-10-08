package com.android.systemui.statusbar.toggles;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;

import com.android.systemui.R;

public class RebootToggle extends BaseToggle {

    @Override
    public void init(Context c, int style) {
        super.init(c, style);
        setIcon(R.drawable.ic_qs_reboot);
        setLabel(R.string.quick_settings_reboot);
    }

    @Override
    public void onClick(View v) {
        Intent intent=new Intent(Intent.ACTION_POWERMENU_REBOOT);
        mContext.sendBroadcast(intent);

        collapseStatusBar();
        dismissKeyguard();
    }

}
