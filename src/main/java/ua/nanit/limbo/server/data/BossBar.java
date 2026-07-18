package ua.nanit.limbo.server.data;

import net.kyori.adventure.text.Component;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;
import ua.nanit.limbo.util.NbtMessageUtil;

import java.lang.reflect.Type;

public class BossBar {
    private Component text;
    private float health;
    private Color color;
    private Division division;

    public Component getText() { return text; }
    public void setText(Component text) { this.text = text; }
    public float getHealth() { return health; }
    public void setHealth(float health) { this.health = health; }
    public Color getColor() { return color; }
    public void setColor(Color color) { this.color = color; }
    public Division getDivision() { return division; }
    public void setDivision(Division division) { this.division = division; }

    public enum Color { PINK, BLUE, RED, GREEN, YELLOW, PURPLE, WHITE }
    public enum Division { SOLID, DASHES_6, DASHES_10, DASHES_12, DASHES_20 }

    public static class Serializer implements TypeSerializer<BossBar> {
        @Override
        public BossBar deserialize(Type type, ConfigurationNode node) throws SerializationException {
            BossBar bar = new BossBar();
            String rawText = node.node("text").getString("");
            bar.setText(NbtMessageUtil.parse(rawText));
            bar.setHealth((float) node.node("health").getDouble(1.0));
            try { bar.setColor(BossBar.Color.valueOf(node.node("color").getString("PINK").toUpperCase())); }
            catch (IllegalArgumentException e) { bar.setColor(BossBar.Color.PINK); }
            try { bar.setDivision(BossBar.Division.valueOf(node.node("division").getString("SOLID").toUpperCase())); }
            catch (IllegalArgumentException e) { bar.setDivision(BossBar.Division.SOLID); }
            return bar;
        }
        @Override
        public void serialize(Type type, @Nullable BossBar obj, ConfigurationNode node) throws SerializationException {}
    }
}
