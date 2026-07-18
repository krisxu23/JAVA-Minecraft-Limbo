package ua.nanit.limbo.server.data;

import net.kyori.adventure.text.Component;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;
import ua.nanit.limbo.util.NbtMessageUtil;

import java.lang.reflect.Type;

public class Title {
    private Component title;
    private Component subtitle;
    private int fadeIn;
    private int stay;
    private int fadeOut;

    public Component getTitle() { return title; }
    public void setTitle(Component title) { this.title = title; }
    public Component getSubtitle() { return subtitle; }
    public void setSubtitle(Component subtitle) { this.subtitle = subtitle; }
    public int getFadeIn() { return fadeIn; }
    public void setFadeIn(int fadeIn) { this.fadeIn = fadeIn; }
    public int getStay() { return stay; }
    public void setStay(int stay) { this.stay = stay; }
    public int getFadeOut() { return fadeOut; }
    public void setFadeOut(int fadeOut) { this.fadeOut = fadeOut; }

    public static class Serializer implements TypeSerializer<Title> {
        @Override
        public Title deserialize(Type type, ConfigurationNode node) throws SerializationException {
            Title t = new Title();
            String rawTitle = node.node("title").getString("");
            String rawSubtitle = node.node("subtitle").getString("");
            t.setTitle(NbtMessageUtil.parse(rawTitle));
            t.setSubtitle(NbtMessageUtil.parse(rawSubtitle));
            t.setFadeIn(node.node("fadeIn").getInt(10));
            t.setStay(node.node("stay").getInt(100));
            t.setFadeOut(node.node("fadeOut").getInt(10));
            return t;
        }
        @Override
        public void serialize(Type type, @Nullable Title obj, ConfigurationNode node) throws SerializationException {}
    }
}
