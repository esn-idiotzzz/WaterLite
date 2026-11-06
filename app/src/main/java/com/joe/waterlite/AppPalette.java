package com.joe.waterlite;

import android.content.Context;

/** Shared color roles for custom views and the platform theme. */
public final class AppPalette {
    public final int background, surface, surfaceVariant, text, textSecondary, outline;
    public final int primary, onPrimary, primaryContainer, onPrimaryContainer, error, onError, errorContainer, scrim;

    private AppPalette(Context context) {
        background=context.getColor(R.color.water_background);
        surface=context.getColor(R.color.water_surface);
        surfaceVariant=context.getColor(R.color.water_surface_variant);
        text=context.getColor(R.color.water_text);
        textSecondary=context.getColor(R.color.water_text_secondary);
        outline=context.getColor(R.color.water_outline);
        primary=context.getColor(R.color.water_primary);
        onPrimary=context.getColor(R.color.water_on_primary);
        primaryContainer=context.getColor(R.color.water_primary_container);
        onPrimaryContainer=context.getColor(R.color.water_on_primary_container);
        error=context.getColor(R.color.water_error);
        onError=context.getColor(R.color.water_on_error);
        errorContainer=context.getColor(R.color.water_error_container);
        scrim=context.getColor(R.color.water_scrim);
    }

    public static AppPalette from(Context context) {
        return new AppPalette(context);
    }
}
