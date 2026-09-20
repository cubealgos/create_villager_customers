package villager_customers.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import villager_customers.transaction.ModDrivenTrade;

/**
 * Suppresses vanilla's trade-xp orb for a mod-driven unit, while leaving the villager's own
 * levelling untouched (`docs/spec/domains/transaction.md` `TRANSACTION-REQ-010`;
 * `decisions/DEC-007-xp-nuggets.md`; VC-12).
 *
 * <p>{@code javap -p -c} against {@code Villager.rewardTradeXp(MerchantOffer)} in the 26.2 jar
 * (`minecraft-merged-deobf-26.2.jar`) shows the whole method as one straight-line sequence:
 * {@code villagerXp += offer.getXp()}, the {@code shouldIncreaseLevel()} /
 * {@code increaseProfessionLevelOnUpdate} levelling bump, then — only if
 * {@code offer.shouldRewardExp()} — {@code new ExperienceOrb(level(), x, y + 0.5, z, xp)} followed by
 * a single {@code Level.addFreshEntity(Entity)Z} call (its boolean result discarded with
 * {@code pop}). No static {@code ExperienceOrb.award} helper is used on this path — the orb is built
 * with {@code new} and handed straight to {@code addFreshEntity}. Every bit of levelling runs before
 * this call, so redirecting only it leaves the levelling path byte-for-byte untouched.
 *
 * <p>This is the narrowest target the bytecode allows: an {@code @Inject} can run code before or
 * after an instruction but cannot skip a single call by itself, only cancel the whole method (which
 * would skip the levelling too) or need a Mixin Extras {@code @WrapOperation} this project does not
 * depend on. A plain Fabric Mixin {@code @Redirect} on the {@code addFreshEntity} call replaces
 * exactly that one call and nothing else: while {@link ModDrivenTrade#isActive()}, the {@code Entity}
 * (the freshly constructed orb) is simply never added to the level and the redirect returns
 * {@code false} in place of {@code addFreshEntity}'s own result, which the original bytecode discards
 * anyway; otherwise it calls through to the real {@code addFreshEntity} unchanged.
 */
@Mixin(Villager.class)
final class VillagerRewardTradeXpMixin {
    @Redirect(
        method = "rewardTradeXp",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"
        )
    )
    private boolean villager_customers$skipOrbForModDrivenTrade(Level level, Entity orb) {
        if (ModDrivenTrade.isActive()) {
            return false;
        }
        return level.addFreshEntity(orb);
    }
}
