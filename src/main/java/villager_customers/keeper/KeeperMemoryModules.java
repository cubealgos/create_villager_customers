package villager_customers.keeper;

import com.mojang.serialization.Codec;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

import java.util.Optional;

/**
 * The two memory modules a nitwit's keeper seek lives in (`docs/spec/04-architecture.md`
 * `ARCH-DEC-006`; `docs/spec/domains/keeper.md` §5's own Approach), mirroring
 * {@code villager_customers.customer.CustomerMemoryModules}'s trip-target/cooldown shape.
 *
 * <p>{@link #KEEPER_CLAIMED_SEAT} is dual-purpose, exactly as the domain's own state table implies:
 * while walking it names the seat {@code KeeperSeekBehavior} is heading for; once seated, the same
 * value keeps naming "the seat this nitwit is keeper of" so a later tick can tell an ejection or a
 * broken seat apart from "never claimed anything" (`KEEPER-REQ-011`, `012`).
 */
public final class KeeperMemoryModules {
    /** {@code villager_customers:keeper_claimed_seat} — the seat being walked to, or already kept (`KEEPER-REQ-007`..`012`). */
    public static final MemoryModuleType<GlobalPos> KEEPER_CLAIMED_SEAT = register("keeper_claimed_seat", GlobalPos.CODEC);

    /** {@code villager_customers:keeper_seek_cooldown} — the game time of the next allowed seek (`KEEPER-REQ-004`, `KEEPER-DEC-003`). */
    public static final MemoryModuleType<Long> KEEPER_SEEK_COOLDOWN = register("keeper_seek_cooldown", Codec.LONG);

    private KeeperMemoryModules() {
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
        if (KEEPER_CLAIMED_SEAT == null || KEEPER_SEEK_COOLDOWN == null) {
            throw new IllegalStateException("villager_customers keeper memory module registration failed");
        }
    }
}
