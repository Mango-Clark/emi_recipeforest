package io.github.mango_clark.emirecipeforest.mixin;

import dev.emi.emi.input.EmiBind;
import dev.emi.emi.screen.widget.config.EmiBindWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = EmiBindWidget.class, remap = false)
public interface EmiBindWidgetAccessor {
    @Accessor("bind")
    EmiBind recipeForest$getBind();
}
