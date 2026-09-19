package villager_customers.customer;

import com.mojang.serialization.Codec;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

import java.util.Optional;

/**
 * The two memory modules a shopping trip lives in, registered and persisted exactly as vanilla's
 * own {@code HOME}/{@code JOB_SITE}/{@code CANT_REACH_WALK_TARGET_SINCE}
 * (`docs/spec/04-architecture.md` `ARCH-DEC-005`; `docs/spec/contracts/data-contract.md`).
 *
 * <p>Both are registered with a codec ({@code GlobalPos.CODEC}, {@code Codec.LONG}) so they persist
 * through ordinary brain memory save/load, the same mechanism vanilla's own memory modules use
 * (`DATA-REQ-001`..`003`); {@link MemoryModuleType}'s own constructor wraps the given value codec
 * into the {@code ExpirableValue} codec it actually stores, confirmed via {@code javap -p -c}.
 */
public final class CustomerMemoryModules {
    /** {@code villager_customers:shopping_trip_target} — the shop being walked to (`DATA-REQ-001`). */
    public static final MemoryModuleType<GlobalPos> SHOPPING_TRIP_TARGET = register("shopping_trip_target", GlobalPos.CODEC);

    /** {@code villager_customers:shopping_cooldown} — the game time of the next allowed roll (`DATA-REQ-002`). */
    public static final MemoryModuleType<Long> SHOPPING_COOLDOWN = register("shopping_cooldown", Codec.LONG);

    private CustomerMemoryModules() {
    }

    private static <T> MemoryModuleType<T> register(String path, Codec<T> codec) {
        return Registry.register(
            BuiltInRegistries.MEMORY_MODULE_TYPE, Identifier.fromNamespaceAndPath("villager_customers", path), new MemoryModuleType<>(Optional.of(codec))
        );
    }

    /**
     * Registers both memory modules. Call once, during mod init — referencing the fields above
     * forces this class's static initializer (and so both {@link Registry#register} calls) to run
     * now rather than whenever something else first touches this class.
     */
    public static void register() {
        if (SHOPPING_TRIP_TARGET == null || SHOPPING_COOLDOWN == null) {
            throw new IllegalStateException("villager_customers memory module registration failed");
        }
    }
}
