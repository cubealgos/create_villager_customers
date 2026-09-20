package villager_customers.mixin;

import com.mojang.datafixers.util.Pair;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.schedule.Activity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.Set;

/**
 * Read-only access to {@code Brain}'s private {@code activityRequirements} and
 * {@code activityMemoriesToEraseWhenStopped} maps (VC-21). {@link VillagerBrainMixin} reads both
 * before re-registering {@code Activity.WORK}, so its own {@code Brain.addActivity} call — which
 * replaces an activity's entire requirement and erase-on-stop sets with a plain {@code Map.put},
 * confirmed by {@code javap -p -c} on {@code Brain.addActivity} — carries forward whatever is
 * already there (vanilla's own {@code JOB_SITE VALUE_PRESENT} condition for {@code WORK}, plus
 * anything a differently-ordered mod already added, `ARCH-FAIL-004`) instead of wiping it.
 *
 * <p>Vanilla registers that condition through {@code Brain}'s own {@code List<ActivityData>}
 * constructor — the path {@code Villager.BRAIN_PROVIDER} actually uses, inside
 * {@code Brain.Provider.makeBrain}, which both {@code Villager.makeBrain} and
 * {@code Villager.refreshBrain} call before {@code registerBrainGoals} ever runs (confirmed the
 * same way; {@code Villager.registerBrainGoals} itself never calls {@code addActivity} at all in
 * this Minecraft version — only schedule setup) — so the map already holds it by the time this
 * mixin's own {@code @Inject at RETURN} reads it.
 */
@Mixin(Brain.class)
public interface BrainActivityStateAccessor {
    @Accessor("activityRequirements")
    Map<Activity, Set<Pair<MemoryModuleType<?>, MemoryStatus>>> villager_customers$activityRequirements();

    @Accessor("activityMemoriesToEraseWhenStopped")
    Map<Activity, Set<MemoryModuleType<?>>> villager_customers$activityMemoriesToEraseWhenStopped();
}
