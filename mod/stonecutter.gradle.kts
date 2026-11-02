plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.3"

stonecutter parameters {
    // Names the game changed, swapped across the whole source tree for older versions.
    replacements {
        // 26.1 renamed the GUI drawing class and its methods. Our drawing variable is always
        // called `graphics`, which keeps these swaps from touching anything else.
        string(current.parsed < "26.1") {
            replace("GuiGraphicsExtractor", "GuiGraphics")
            replace("graphics.text(", "graphics.drawString(")
            replace("graphics.centeredText(", "graphics.drawCenteredString(")
            replace("graphics.outline(", "graphics.renderOutline(")
            replace("graphics.item(", "graphics.renderItem(")
            replace("graphics().text(", "graphics().drawString(")
            replace("com.mojang.renderpearl.api.pipeline", "com.mojang.blaze3d.pipeline")
            // Screens drew through render() before 26.1 split drawing into "extract" steps.
            replace("void extractRenderState(", "void render(")
            replace("void extractBackground(", "void renderBackground(")
            // The screen and the HUD moved under Gui in 26.1.
            replace("gui.setScreen(", "setScreen(")
            replace("gui.screen()", "screen")
            replace("gui.hud.getTabList()", "gui.getTabList()")
            replace("minecraft.gui.hud.isHidden()", "minecraft.options.hideGui")
            replace("net.minecraft.client.multiplayer.chat.GuiMessageTag", "net.minecraft.client.GuiMessageTag")
            // Fabric API's key helper had the older name.
            replace("net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper", "net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper")
            replace("KeyMappingHelper.registerKeyMapping(", "KeyBindingHelper.registerKeyBinding(")
            // Only the in-game tests use these.
            replace("resizeGui()", "resizeDisplay()")
            replace("world.getConnection().waitForChunksRender()", "world.getClientWorld().waitForChunksRender()")
            replace("InputConstants.Type.KEYBOARD", "InputConstants.Type.KEYSYM")
        }
    }
}
