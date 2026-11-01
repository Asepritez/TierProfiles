# 04: Surfaces

A surface is somewhere a tier appears: nametag, tab list, chat. This stage connects the core's decisions
to the actual game.

## Mixins

A mod can't edit Minecraft's source, so how do we change what a nametag says? With a **mixin**: a small
class that tells a tool called Mixin to patch a method of the game as it loads. Look at
`EntityRendererMixin`:

```java
@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {
    @Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
    private void tierprofiles$addTag(Entity entity, CallbackInfoReturnable<Component> result) {
```

That reads: "at the moment `EntityRenderer.getNameTag` returns, run my code first, and let me change the
answer." The `tierprofiles$` prefix avoids clashing with other mods.

Mixins are only checked when the game starts, not when you compile. A wrong method name compiles fine
and then crashes. That's why running the real game matters so much in this project.

The choice of hook point is a design decision, not only a technical one. The game calls `getNameTag` only
after deciding a name should show, so hooking there means we *can't* reveal a hidden name, however buggy
our code is.

## Everything goes through Decorator

All three mixins call `Decorator`, the one place tags meet Minecraft text. It's wrapped in a `try`/`catch`
so a bug in our code shows a plain name instead of crashing someone's game mid-fight.

## Icons are text

Nametags, tab rows and chat lines are all text, so how do you put a picture in them? With a **font**. A
Minecraft font can map a character to an image. We use "private use" characters, which Unicode reserves
for exactly this, so `` means "the sword icon" in our font. Drop that character into any text and
the icon appears, on every surface, with no special drawing code.

## Vanilla icons without shipping them

The look should be real Minecraft items, but we may not redistribute Mojang's textures. So nothing is
stored in the mod. Instead `IconPack` is a **resource pack that exists only in memory**.

When the game loads our font, the font asks for an image such as
`tierprofiles:icon/classic/sword.png`. No such file exists. `IconPack` catches the request, goes looking
inside the player's own game, and hands back what it finds. It tries the built-in Programmer Art pack,
then the current game, then our own pixel art. So an icon can't go missing, and items newer than
Programmer Art simply use their current texture.

Potions need one extra step. A potion's coloured liquid is a separate grey image the game tints at
runtime, so `IconPack` paints the two layers together to make one pink healing potion.

## Chat stays honest

Player chat is signed, and Minecraft has a report system that needs the original message. So
`ChatListenerMixin` wraps only the final call that puts text on your screen. The signature check, the
trust check and the report log all still see the untouched message. The tag only exists on your screen.

Tags go at the start of the line, because a server's chat format can be anything and we can't reliably
find the name inside it. Many servers send chat as plain system messages with no sender attached, so
`ChatSender` guesses from the text before the first `:` or `>`. A wrong guess only shows another player's
real tier, so it's harmless.

## Limits

Chat tags only look for a sender near the start of the message, so unusual formats won't get a tag.
