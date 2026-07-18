package ua.nanit.limbo.protocol.packets.play;

import ua.nanit.limbo.protocol.ByteMessage;
import ua.nanit.limbo.protocol.PacketOut;
import ua.nanit.limbo.protocol.registry.Version;
import ua.nanit.limbo.server.data.Title;

public class PacketTitleLegacy implements PacketOut {
    private Title title;
    private Action action;

    public enum Action { SET_TITLE, SET_SUBTITLE, SET_TIMES_AND_DISPLAY }

    public PacketTitleLegacy(Title title) { this.title = title; }

    public void setAction(Action action) { this.action = action; }

    @Override
    public void encode(ByteMessage msg, Version version) {
        msg.writeVarInt(action.ordinal());
        if (action == Action.SET_TIMES_AND_DISPLAY) {
            msg.writeVarInt(title.getFadeIn());
            msg.writeVarInt(title.getStay());
            msg.writeVarInt(title.getFadeOut());
        } else {
            String text = (action == Action.SET_TITLE)
                ? ua.nanit.limbo.util.NbtMessageUtil.toJson(title.getTitle())
                : ua.nanit.limbo.util.NbtMessageUtil.toJson(title.getSubtitle());
            msg.writeString(text);
        }
    }
}
