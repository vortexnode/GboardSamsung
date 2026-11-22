package dev.jason.gboardpatches.extension.toolbarroundchips;

import android.content.Context;
import android.util.Log;

import java.util.Collections;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

/**
 * Settings screen for adaptive Samsung-style circular icon chips on Gboard's top toolbar.
 */
public final class GboardToolbarRoundChipsSettingsFeature
        implements GboardPatchesSettingsContract.Feature {
    private static final String TAG = "GboardPatches";

    private final String title;
    private final String summary;
    private final String badge;
    private final String sectionTitle;
    private final String enabledTitle;
    private final String enabledSummary;

    public GboardToolbarRoundChipsSettingsFeature(Context context) {
        title = text(context, R.string.gboard_patches_toolbar_round_chips_title);
        summary = text(context, R.string.gboard_patches_toolbar_round_chips_summary);
        badge = text(context, R.string.gboard_patches_header_badge);
        sectionTitle = text(context, R.string.gboard_patches_toolbar_round_chips_section);
        enabledTitle = text(context, R.string.gboard_patches_toolbar_round_chips_enabled_title);
        enabledSummary = text(context, R.string.gboard_patches_toolbar_round_chips_enabled_summary);
    }

    @Override
    public String getEntryTitle() {
        return title;
    }

    @Override
    public String getEntrySummary() {
        return summary;
    }

    @Override
    public boolean isAvailable(Context context) {
        return GboardPatchesFeatureAvailability.hasFeature(
                context, GboardPatchesFeatureAvailability.FEATURE_SAMSUNG_SPACEBAR_GESTURES);
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        try {
            if (host == null || host.getContext() == null) {
                return errorScreen();
            }
            Context context = host.getContext();
            boolean enabled = GboardToolbarRoundChipsSettings.readEnabled(context);
            return buildScreenForState(
                    enabled,
                    value -> GboardToolbarRoundChipsSettings.writeEnabled(context, value));
        } catch (Throwable failure) {
            Log.w(TAG, "Failed to render toolbar round chips settings", failure);
            return errorScreen();
        }
    }

    GboardPatchesSettingsContract.Screen buildScreenForState(
            boolean enabled,
            GboardPatchesSettingsContract.ToggleAction toggleAction) {
        return new GboardPatchesSettingsContract.Screen(
                title,
                badge,
                title,
                summary,
                Collections.emptyList(),
                Collections.singletonList(new GboardPatchesSettingsContract.Section(
                        sectionTitle,
                        Collections.singletonList(
                                new GboardPatchesSettingsContract.ToggleRow(
                                        enabledTitle,
                                        enabledSummary,
                                        true,
                                        enabled,
                                        toggleAction)))));
    }

    private GboardPatchesSettingsContract.Screen errorScreen() {
        return new GboardPatchesSettingsContract.Screen(
                title,
                badge,
                title,
                summary,
                Collections.singletonList(new GboardPatchesSettingsContract.StatusBlock(
                        title,
                        summary,
                        GboardPatchesSettingsContract.StatusTone.WARNING)),
                Collections.emptyList());
    }

    private static String text(Context context, int resourceId) {
        return GboardSettingsText.get(context, resourceId);
    }
}
