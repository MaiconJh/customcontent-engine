package com.customcontentengine.adapter.bukkit;

import com.customcontentengine.application.item.ItemService;
import com.customcontentengine.domain.definition.DropTable;
import com.customcontentengine.internalapi.identity.WorldPosition;
import com.customcontentengine.port.DropPort;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

public final class BukkitDropAdapter implements DropPort {
    private final Function<String, Optional<ItemStack>> customItemFactory;
    private final Function<String, Material> materialMatcher;
    private final Function<WorldPosition, World> worldResolver;

    public BukkitDropAdapter(ItemService<ItemStack> itemService) {
        this(
                rawId -> itemService.createCustomItem(rawId).item(),
                BukkitDropAdapter::matchMaterial,
                position -> Bukkit.getWorld(position.worldName()));
    }

    BukkitDropAdapter(
            Function<String, Optional<ItemStack>> customItemFactory,
            Function<String, Material> materialMatcher,
            Function<WorldPosition, World> worldResolver) {
        this.customItemFactory = Objects.requireNonNull(customItemFactory, "customItemFactory");
        this.materialMatcher = Objects.requireNonNull(materialMatcher, "materialMatcher");
        this.worldResolver = Objects.requireNonNull(worldResolver, "worldResolver");
    }

    @Override
    public void drop(WorldPosition position, DropTable drops) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(drops, "drops");
        World world = worldResolver.apply(position);
        if (world == null) {
            throw new IllegalArgumentException("Unknown world: " + position.worldName());
        }

        Location location = new Location(world, position.x() + 0.5, position.y() + 0.5, position.z() + 0.5);
        for (DropTable.Entry entry : drops.entries()) {
            createDrop(entry).ifPresent(item -> world.dropItemNaturally(location, item));
        }
    }

    private Optional<ItemStack> createDrop(DropTable.Entry entry) {
        Optional<ItemStack> customItem = customItemFactory.apply(entry.item());
        if (customItem.isPresent()) {
            ItemStack item = customItem.get();
            item.setAmount(entry.amount());
            return Optional.of(item);
        }

        Material material = materialMatcher.apply(entry.item());
        if (material == null || !material.isItem()) {
            return Optional.empty();
        }
        return Optional.of(new ItemStack(material, entry.amount()));
    }

    private static Material matchMaterial(String rawMaterial) {
        return Material.matchMaterial(rawMaterial.toUpperCase(Locale.ROOT));
    }
}
