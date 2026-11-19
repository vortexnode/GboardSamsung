package dev.jason.gboardpatches.patches.gboard.features.spacebargesture

import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import dev.jason.gboardpatches.patches.gboard.shared.GboardKeyboardViewHolderTouchFeature
import dev.jason.gboardpatches.patches.gboard.shared.applyKeyboardViewHolderTouchInterceptors
import dev.jason.gboardpatches.patches.gboard.shared.gboardKeyboardViewHolderTouchFeaturePatch

internal val gboardSpacebarGestureBytecodePatch = gboardKeyboardViewHolderTouchFeaturePatch(
    description = "在 KeyboardViewHolder.dispatchTouchEvent 注入 Samsung 空白鍵手勢攔截器。",
    feature = GboardKeyboardViewHolderTouchFeature.SAMSUNG_SPACEBAR_GESTURES,
)

internal fun MutableMethod.applySpacebarGestureTouchInterceptor() {
    applyKeyboardViewHolderTouchInterceptors(
        setOf(GboardKeyboardViewHolderTouchFeature.SAMSUNG_SPACEBAR_GESTURES),
    )
}
