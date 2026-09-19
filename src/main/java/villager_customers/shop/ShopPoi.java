package villager_customers.shop;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.content.logistics.tableCloth.TableClothBlock;
import com.zurrtum.create.content.logistics.tableCloth.TableClothBlockEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The point-of-interest type {@code villager_customers:table_cloth_shop}, registered over every
 * Create Fly table cloth block state so a villager's search can find a shop the same way it finds
 * a job site (`docs/spec/domains/shop.md` `SHOP-REQ-001`; `docs/spec/decisions/DEC-008-poi.md`).
 *
 * <p><b>Registration API, verified by {@code javap} against
 * {@code minecraft-merged-deobf-26.2.jar}:</b> no Fabric API module in 0.160.0+26.2 carries a
 * point-of-interest helper (an exhaustive jar listing across every module under
 * {@code net.fabricmc.fabric-api} found no class with "poi" or "point_of_interest" in its name),
 * and {@code net/minecraft/world/entity/ai/village/poi/point_of_interest_type/} does not exist as
 * a data folder anywhere in the merged jar — {@link PoiType} is a plain code-registered type, not
 * data-driven. Registration is therefore the same raw vanilla pattern
 * {@code net.minecraft.world.entity.ai.village.poi.PoiTypes.bootstrap} uses for job sites:
 * {@link Registry#registerForHolder(Registry, ResourceKey, Object)} against
 * {@link BuiltInRegistries#POINT_OF_INTEREST_TYPE}.
 *
 * <p><b>A second finding this registration alone does not cover:</b> {@code PoiManager}'s two
 * paths that would normally keep a POI in step with the world —
 * {@code ServerLevel.updatePOIOnBlockStateChange} (block place/break) and the chunk-load
 * consistency scan reached from {@code SerializableChunkData} — both call
 * {@code PoiTypes.forState(BlockState)} directly (confirmed by {@code javap -p -c} disassembly of
 * both), which is {@code PoiTypes}' own private, vanilla-only block-state map and cannot see a
 * registry entry a mod adds. So this mod keeps its own POI records in step with the world itself,
 * on the table cloth block entity's own load and unload, exactly mirroring what that vanilla hook
 * would have done for a state it recognised.
 */
public final class ShopPoi {
    /** The point-of-interest type's registry id. */
    public static final Identifier ID = Identifier.fromNamespaceAndPath("villager_customers", "table_cloth_shop");

    /** The point-of-interest type's registry key. */
    public static final ResourceKey<PoiType> KEY = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, ID);

    /**
     * Ticket count and valid range: 1 and 1, the same values every vanilla job-site {@link PoiType}
     * (armorer, farmer, ...) registers with, verified via {@code javap -p -c} disassembly of
     * {@code PoiTypes.bootstrap}.
     */
    private static final int MAX_TICKETS = 1;
    private static final int VALID_RANGE = 1;

    private static Holder<PoiType> holder;

    private ShopPoi() {
    }

    /** Registers the point-of-interest type and the load/unload sync described above. Call once. */
    static void register() {
        holder = Registry.registerForHolder(BuiltInRegistries.POINT_OF_INTEREST_TYPE, KEY, new PoiType(tableClothStates(), MAX_TICKETS, VALID_RANGE));
        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register(ShopPoi::onLoad);
        ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register(ShopPoi::onUnload);
    }

    private static void onLoad(BlockEntity blockEntity, ServerLevel level) {
        if (blockEntity instanceof TableClothBlockEntity) {
            level.getPoiManager().add(blockEntity.getBlockPos(), holder);
        }
    }

    private static void onUnload(BlockEntity blockEntity, ServerLevel level) {
        if (blockEntity instanceof TableClothBlockEntity) {
            level.getPoiManager().remove(blockEntity.getBlockPos());
        }
    }

    /**
     * Every block state of every Create Fly table cloth block (`AllBlocks.TABLE_CLOTH`'s 16
     * colours plus the andesite, brass and copper variants; 19 blocks confirmed via {@code javap}
     * against the Create Fly jar), the same "every possible state, unfiltered" convention
     * {@code PoiTypes.getBlockStates(Block)} uses for job sites.
     */
    private static Set<BlockState> tableClothStates() {
        Set<TableClothBlock> blocks = new LinkedHashSet<>(AllBlocks.TABLE_CLOTH.asList());
        blocks.add(AllBlocks.ANDESITE_TABLE_CLOTH);
        blocks.add(AllBlocks.BRASS_TABLE_CLOTH);
        blocks.add(AllBlocks.COPPER_TABLE_CLOTH);
        return blocks.stream()
            .flatMap(block -> block.getStateDefinition().getPossibleStates().stream())
            .collect(Collectors.toUnmodifiableSet());
    }
}
