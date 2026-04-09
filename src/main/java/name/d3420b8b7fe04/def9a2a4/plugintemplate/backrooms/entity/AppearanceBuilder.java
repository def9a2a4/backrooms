package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("deprecation")
public class AppearanceBuilder {

    private boolean useArmorStand = false;
    private boolean invisible = true;
    private boolean gravity = false;
    private boolean small = false;
    private String nameTag = null;
    private String playerHeadTexture = null;
    private final List<EquipmentEntry> equipment = new ArrayList<>();

    private Material blockDisplayMaterial = null;
    private String textDisplayContent = null;
    private ItemStack itemDisplayStack = null;

    public AppearanceBuilder armorStand() {
        this.useArmorStand = true;
        return this;
    }

    public AppearanceBuilder invisible(boolean invisible) {
        this.invisible = invisible;
        return this;
    }

    public AppearanceBuilder gravity(boolean gravity) {
        this.gravity = gravity;
        return this;
    }

    public AppearanceBuilder small(boolean small) {
        this.small = small;
        return this;
    }

    public AppearanceBuilder nameTag(String name) {
        this.nameTag = name;
        return this;
    }

    public AppearanceBuilder playerHead(String textureUrl) {
        this.playerHeadTexture = textureUrl;
        return this;
    }

    public AppearanceBuilder equipment(EquipmentSlot slot, ItemStack item) {
        equipment.add(new EquipmentEntry(slot, item));
        return this;
    }

    public AppearanceBuilder blockDisplay(Material material) {
        this.blockDisplayMaterial = material;
        return this;
    }

    public AppearanceBuilder textDisplay(String text) {
        this.textDisplayContent = text;
        return this;
    }

    public AppearanceBuilder itemDisplay(ItemStack item) {
        this.itemDisplayStack = item;
        return this;
    }

    public List<Entity> build(Location location) {
        List<Entity> entities = new ArrayList<>();

        if (useArmorStand) {
            ArmorStand stand = location.getWorld().spawn(location, ArmorStand.class, as -> {
                as.setInvisible(invisible);
                as.setGravity(gravity);
                as.setInvulnerable(true);
                as.setSilent(true);
                as.setSmall(small);
                as.setCanPickupItems(false);

                if (nameTag != null) {
                    as.setCustomName(nameTag);
                    as.setCustomNameVisible(true);
                }

                if (playerHeadTexture != null) {
                    ItemStack head = createPlayerHead(playerHeadTexture);
                    as.getEquipment().setHelmet(head);
                }

                for (EquipmentEntry entry : equipment) {
                    as.getEquipment().setItem(entry.slot(), entry.item());
                }
            });
            entities.add(stand);
        }

        if (blockDisplayMaterial != null) {
            BlockDisplay display = location.getWorld().spawn(location, BlockDisplay.class, bd -> {
                bd.setBlock(blockDisplayMaterial.createBlockData());
            });
            entities.add(display);
        }

        if (textDisplayContent != null) {
            TextDisplay display = location.getWorld().spawn(location, TextDisplay.class, td -> {
                td.setText(textDisplayContent);
            });
            entities.add(display);
        }

        if (itemDisplayStack != null) {
            ItemDisplay display = location.getWorld().spawn(location, ItemDisplay.class, id -> {
                id.setItemStack(itemDisplayStack);
            });
            entities.add(display);
        }

        return entities;
    }

    @SuppressWarnings("deprecation")
    public static ItemStack createPlayerHead(String textureUrl) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            org.bukkit.profile.PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
            org.bukkit.profile.PlayerTextures textures = profile.getTextures();
            try {
                textures.setSkin(URI.create(textureUrl).toURL());
            } catch (MalformedURLException e) {
                // Invalid URL; head will have no custom skin
            }
            meta.setOwnerProfile(profile);
            head.setItemMeta(meta);
        }
        return head;
    }

    private record EquipmentEntry(EquipmentSlot slot, ItemStack item) {}
}
