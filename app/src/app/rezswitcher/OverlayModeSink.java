package app.rezswitcher;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import app.rezswitcher.core.AfrController;

/** Requests a display mode by keeping an invisible 1x1 overlay window on top with preferredDisplayModeId set. */
public final class OverlayModeSink implements AfrController.ModeSink {
    private final Context context;
    private final WindowManager windowManager;
    private final Handler main = new Handler(Looper.getMainLooper());
    private View view;

    public OverlayModeSink(Context context) {
        this.context = context;
        this.windowManager = context.getSystemService(WindowManager.class);
    }

    @Override
    public void requestMode(int modeId) {
        main.post(() -> {
            WindowManager.LayoutParams params = layoutParams(modeId);
            if (view == null) {
                view = new View(context);
                windowManager.addView(view, params);
            } else {
                windowManager.updateViewLayout(view, params);
            }
        });
    }

    @Override
    public void clearMode() {
        main.post(() -> {
            if (view != null) {
                windowManager.removeView(view);
                view = null;
            }
        });
    }

    private static WindowManager.LayoutParams layoutParams(int modeId) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                1, 1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.preferredDisplayModeId = modeId;
        return params;
    }
}
