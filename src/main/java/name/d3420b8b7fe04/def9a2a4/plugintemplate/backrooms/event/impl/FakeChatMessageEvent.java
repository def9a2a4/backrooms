package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event.AbstractTimedEvent;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class FakeChatMessageEvent extends AbstractTimedEvent {

    private List<String> messages = new ArrayList<>();

    public FakeChatMessageEvent() {
        this.chance = 0.1;
        this.checkIntervalTicks = 60 * 20;
    }

    @Override
    public String getId() {
        return "fake_chat_message";
    }

    @Override
    public void loadConfig(ConfigurationSection config) {
        super.loadConfig(config);
        messages = new ArrayList<>();
        if (config != null && config.contains("messages")) {
            messages.addAll(config.getStringList("messages"));
        }
        if (messages.isEmpty()) {
            messages.add("<Steve> hello?");
            messages.add("<Steve> is anyone there");
            messages.add("Steve left the game");
        }
    }

    @Override
    public void trigger(Player player, BackroomsPlayerState state) {
        String msg = messages.get(ThreadLocalRandom.current().nextInt(messages.size()));
        // Replace {player} placeholder with actual player name
        msg = msg.replace("{player}", player.getName());
        player.sendMessage(msg);
    }
}
