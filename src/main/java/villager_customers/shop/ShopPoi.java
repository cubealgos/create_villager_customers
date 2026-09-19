package villager_customers.shop;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.content.logistics.tableCloth.TableClothBlock;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PoiHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The point-of-interest type {@code villager_customers:table_cloth_shop}, registered over every
 * Create Fly table cloth block state so a villager's search can find a shop the same way it finds
 * a job site (`docs/spec/domains/shop.md` `SHOP-REQ-001`; `docs/spec/decisions/DEC-008-poi.md`).
 *
 * <p><b>Registration API, verified by {@code javap} against the Fabric API 0.160.0+26.2 module
 * jars:</b> {@code fabric-object-builder-api-v1} (24.1.1) ships
 * {@link PoiHelper#register(Identifier, int, int, Iterable)}. Disassembling it shows it forwards
 * to vanilla's own (access-widened) {@code PoiTypes.register(Registry, ResourceKey, Set, int,
 * int)} — the exact private method {@code PoiTypes.bootstrap} itself uses for job sites, which
 * also runs {@code PoiTypes}' own {@code registerBlockStates}, populating its private
 * {@code TYPE_BY_STATE} map. Because registration goes through that same vanilla method, vanilla's
 * own discovery paths — {@code ServerLevel.updatePOIOnBlockStateChange} (block place/break) and
 * the chunk-load consistency scan — recognise a table cloth exactly the way they recognise a bed
 * or a lectern, through {@code PoiTypes.forState}, with no sync of this mod's own needed. (An
 * earlier version of this class registered straight into
 * {@code BuiltInRegistries.POINT_OF_INTEREST_TYPE} and synced presence itself on the table cloth
 * block entity's load/unload, since that bypassed {@code TYPE_BY_STATE} entirely; that path was
 * fragile — the chunk-load consistency scan clears any record for a state {@code forState} does
 * not know, so the record only survived because the block-entity load event happened to fire
 * after that scan — and is no longer needed now that {@link PoiHelper} is used.)
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

    private ShopPoi() {
    }

    /** Registers the point-of-interest type. Call once. */
    static void register() {
        PoiHelper.register(ID, MAX_TICKETS, VALID_RANGE, tableClothStates());
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
